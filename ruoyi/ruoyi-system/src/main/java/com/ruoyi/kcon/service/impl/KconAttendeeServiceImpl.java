package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.service.IKconAttendeeService;
import com.ruoyi.kcon.service.IKconChannelMappingService;
import com.ruoyi.kcon.service.IKconCheckinService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参会人名单管理实现
 *
 * 导入规则（2026-10-21 起：只认 6 列标准模板，不再兼容旧模板）：
 *   - 表头必须是：姓名 / 手机号 / 渠道来源 / 公司 / 职位 / 邮箱，顺序任意、列可增删
 *   - 必填 3 列：姓名 / 手机号 / 渠道来源
 *   - 选填 3 列：公司 / 职位 / 邮箱
 *   - 权益类型与参与抽奖不读文件，一律由渠道来源查映射表 kcon_channel_mapping 带出；
 *     渠道未配置映射时该行计阻塞问题，正式导入整批拒绝
 *   - 未识别的列直接忽略（例如票务总表带来的 购票时间/金额/发票需求）
 *   - 渠道来源用下拉选择（前端），避免手输错字
 *
 * 为什么不再兼容旧表头：旧模板的「票种」「是否参与抽奖」语义已作废，
 * 继续接受只会让人以为填了有用，实际被映射静默覆盖。宁可导入时直接报「表头不认识」，
 * 也不要让操作员拿着一份填满了却全部无效的表格反复试。
 *
 * @author kcon
 */
@Service
public class KconAttendeeServiceImpl implements IKconAttendeeService
{
    private static final Logger log = LoggerFactory.getLogger(KconAttendeeServiceImpl.class);

    /** 标准表头 → 标准字段名（只认 6 列模板，不做旧表头别名兼容） */
    private static final Map<String, String> HEADER_ALIAS = new HashMap<>();

    static
    {
        HEADER_ALIAS.put("姓名", "name");
        HEADER_ALIAS.put("手机号", "phone");
        HEADER_ALIAS.put("渠道来源", "channel");
        HEADER_ALIAS.put("公司", "company");
        HEADER_ALIAS.put("职位", "position");
        HEADER_ALIAS.put("邮箱", "email");
    }

    /** 必填列（标准字段名）：只保留身份与来源，权益/资格由映射带出 */
    private static final List<String> REQUIRED = List.of("name", "phone", "channel");

    /** 必填列的中文名（报错用） */
    private static final Map<String, String> FIELD_CN = Map.of(
            "name", "姓名", "phone", "手机号", "channel", "渠道来源"
    );

    /** 单文件最多导入行数（不含表头）：超过直接阻塞，避免超大文件拖垮现场机器 */
    private static final int MAX_IMPORT_ROWS = 20000;

    /** 字段长度上限，与 {@link KconAttendee} 的 @Size 约束保持一致 */
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_COMPANY_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_POSITION_LENGTH = 64;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private IKconChannelMappingService channelMappingService;

    @Override
    public List<KconAttendee> selectAttendeeList(KconAttendee query)
    {
        List<KconAttendee> list = attendeeMapper.selectAttendeeList(query);
        for (KconAttendee a : list)
        {
            a.setPhoneMask(IKconCheckinService.maskPhone(a.getPhone()));
            // 列表只回掩码：完整手机号仅保留在单条详情（编辑表单回填用）
            a.setPhone(null);
        }
        return list;
    }

    @Override
    public List<KconAttendee> selectAttendeeExportList(KconAttendee query)
    {
        List<KconAttendee> list = attendeeMapper.selectAttendeeList(query);
        for (KconAttendee a : list)
        {
            // 导出需要完整手机号（打印/核对名单），仅补掩码字段，不清空明文
            a.setPhoneMask(IKconCheckinService.maskPhone(a.getPhone()));
        }
        return list;
    }

    @Override
    public KconAttendee selectAttendeeById(Long id)
    {
        KconAttendee a = attendeeMapper.selectAttendeeById(id);
        if (a != null)
        {
            a.setPhoneMask(IKconCheckinService.maskPhone(a.getPhone()));
        }
        return a;
    }

    @Override
    public KconAttendee selectByPhone(String phone)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            return null;
        }
        return attendeeMapper.selectAttendeeByPhone(phone.trim());
    }

    @Override
    public int insertAttendee(KconAttendee attendee)
    {
        // 渠道白名单：与导入同一口径。若手工新增能绕过去，映射表就不是唯一事实源了，
        // 现场仍会冒出没配映射的渠道人员，事后再靠人肉比对排查。
        // 先 trim 再校验与落库：校验用 trim 后的值，落库也必须用同一个值，
        // 否则「 嘉宾 」能通过白名单，却以带空格的形式存进库，后续按渠道重算会漏行。
        String channel = attendee.getChannel() == null ? null : attendee.getChannel().trim();
        attendee.setChannel(channel);
        checkChannelAllowed(channel);
        // draw_eligible 一律按映射重算，不接受客户端值：
        // 映射是「参与抽奖」的唯一事实源，直连 API 显式传 1 可给非购票渠道开资格
        Map<String, ChannelMapping> mappings = loadChannelMappings();
        ChannelMapping mapping = mappings.get(channel);
        attendee.setDrawEligible(mapping != null ? mapping.drawEligible : 0);
        // 尾号必须基于 trim 后的手机号计算：带尾部空格时先取后四位会得到 "001 " 这类脏值
        if (attendee.getPhone() != null)
        {
            attendee.setPhone(attendee.getPhone().trim());
        }
        attendee.setPhoneTail4(tail4(attendee.getPhone()));
        return attendeeMapper.insertAttendee(attendee);
    }

    @Override
    public int updateAttendee(KconAttendee attendee)
    {
        // 全程只查一次旧记录：渠道校验与「人工权益」判定共用，避免同一事务里重复打库
        KconAttendee old = attendeeMapper.selectAttendeeById(attendee.getId());
        // 只在渠道真被改过时才校验：存量里可能还有未配置映射的渠道（如历史遗留的「媒体」），
        // 若每次编辑都校验，这些人连补个邮箱都改不了，只能靠改库。
        // 校验与落库统一使用 trim 后的值，避免「白名单通过、库里带空格」。
        boolean channelChanged = false;
        if (attendee.getChannel() != null && !attendee.getChannel().trim().isEmpty())
        {
            String channel = attendee.getChannel().trim();
            String oldChannel = old == null ? null : old.getChannel();
            channelChanged = !channel.equals(oldChannel);
            if (channelChanged)
            {
                checkChannelAllowed(channel);
            }
            attendee.setChannel(channel);
            // draw_eligible 一律按映射重算，不接受客户端值：
            // 映射是「参与抽奖」的唯一事实源，直连 API 显式传 1 可给非购票渠道开资格；
            // 而 resyncByChannel 又会把未锁定行按映射改回，形成「改了又被静默覆盖」。
            // 渠道未变但映射缺失（历史遗留渠道）时不动该字段，避免破坏存量编辑
            Map<String, ChannelMapping> mappings = loadChannelMappings();
            ChannelMapping mapping = mappings.get(channel);
            if (mapping != null)
            {
                attendee.setDrawEligible(mapping.drawEligible);
                if (channelChanged)
                {
                    // 渠道变更时权益类型跟随新映射：旧的人工标记属于旧渠道，
                    // 一并复位为「跟随映射」
                    attendee.setTicketType(mapping.benefitType.isEmpty() ? null : mapping.benefitType);
                    attendee.setTicketTypeManual(0);
                }
            }
        }
        if (attendee.getPhone() != null && !attendee.getPhone().trim().isEmpty())
        {
            String phone = attendee.getPhone().trim();
            String oldPhone = old == null ? null : old.getPhone();
            // 签到后改手机号会破坏兑奖核验：kcon_checkin 里存的仍是旧号，
            // 而名单改成新号后，本人按新号核验永远对不上、按旧号又查不到名单。
            // 改号必须由管理员先处理签到记录，不能让一次普通编辑把两者的关联改断。
            if (oldPhone != null && !phone.equals(oldPhone)
                    && checkinMapper.selectByAttendeeId(attendee.getId()) != null)
            {
                throw new ServiceException("签到后不允许修改手机号，如需修改请联系管理员处理签到记录");
            }
            attendee.setPhone(phone);
            attendee.setPhoneTail4(tail4(phone));
        }
        // 渠道变更时 ticketType 已按新映射回填并复位标记，不是人工修改，
        // 不再走「值变了就置人工标记」的判定（否则渠道一改就被误标成人工改过）
        if (!channelChanged)
        {
            markManualTicketTypeIfChanged(attendee, old);
        }
        int rows = attendeeMapper.updateAttendee(attendee);
        // 乐观锁：带了版本号却更新 0 行，说明这条名单在编辑期间被别人改过（或已被删除）。
        // 与其静默覆盖对方的修改，不如明确拒绝让人重新打开——现场两人同时补录名单时，
        // 静默覆盖很难当场察觉，等发现时已分不清哪个版本是对的。
        if (rows == 0 && attendee.getVersion() != null && !attendee.getVersion().isEmpty())
        {
            if (attendeeMapper.selectAttendeeById(attendee.getId()) == null)
            {
                throw new ServiceException("该名单已被删除，请刷新列表");
            }
            throw new ServiceException("该名单已被他人修改，请关闭后重新打开再改");
        }
        return rows;
    }

    /**
     * 编辑时若权益类型与库中现值不同，自动置「人工修改」标记。
     *
     * 为什么自动判断而不是让前端传标记：前端少传一次就丢一次保护，
     * 而这里用「值真的变了」作为判据，无论谁调用都成立。
     * 值没变则不动标记——否则每次点开保存都会把「跟随映射」误判成「人工改过」，
     * 之后改映射这批人就再也同步不到了。
     */
    private void markManualTicketTypeIfChanged(KconAttendee attendee, KconAttendee old)
    {
        String incoming = attendee.getTicketType();
        if (incoming == null)
        {
            return;
        }
        if (old == null)
        {
            return;
        }
        String current = old.getTicketType();
        boolean changed = current == null ? !incoming.trim().isEmpty()
                : !current.trim().equals(incoming.trim());
        if (changed)
        {
            attendee.setTicketTypeManual(1);
        }
    }

    @Override
    public int clearManualTicketType(Long id)
    {
        // 恢复为「跟随渠道来源映射」：清标记 + 按映射回填权益。
        // 只影响权益类型，不动参与抽奖（该字段本来就以映射为准）。
        if (id == null)
        {
            throw new ServiceException("缺少名单 ID");
        }
        if (attendeeMapper.selectAttendeeById(id) == null)
        {
            throw new ServiceException("该名单不存在，可能已被删除");
        }
        return attendeeMapper.clearManualTicketType(id);
    }

    @Override
    public int deleteAttendeeByIds(Long[] ids)
    {
        // 需求 FR-13.3：默认禁止删除已签到人员。
        // 删掉之后该人的签到记录、端口号、中奖记录都会变成孤儿——
        // 名单页再也查不到这个人，但观众手里有端口号能报出来，兑奖时无法处理。
        if (ids != null && ids.length > 0)
        {
            int signed = checkinMapper.countSignedByAttendeeIds(ids);
            if (signed > 0)
            {
                List<String> names = checkinMapper.selectSignedNamesByAttendeeIds(ids);
                String who = names.isEmpty() ? "" : "（" + String.join("、", names) + " 等）";
                throw new ServiceException("选中的记录中有 " + signed + " 人已签到" + who
                        + "，不能删除。已签到人员若需移除，请先在「数据清场」清空数据。");
            }
        }
        return attendeeMapper.deleteAttendeeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importAttendees(InputStream is, String fileName, String mode, String operator)
    {
        List<String[]> rows = readRows(is, fileName);

        // replace 模式的清空必须放在解析之后：解析会抛异常（缺列/文件为空/未配映射），
        // 若先清空再解析，异常回滚虽然有效，但白跑一次全表 DELETE。
        ParsedImport parsed = parseAttendees(rows, mode, operator);

        // 存在阻塞问题就整批拒绝，不落库，也没有「强制导入」的绕行口子。
        // 「未配置映射」以前只是警告，漏配的渠道也能进名单、还能被抽中，现场抽完才发现。
        // 既然映射表被定为允许导入的渠道白名单，就必须整批拦截：只跳过那几行而放行其余行，
        // 现场拿到的是一份「看起来成功」的残缺名单，比直接失败更难发现。
        // 注意这段只能放在 importAttendees，不能放进 parseAttendees——
        // 否则预检也会抛异常，前端连「哪几行有问题」都看不到。
        if (!parsed.blockers.isEmpty())
        {
            String first = String.valueOf(parsed.blockers.get(0).get("reason"));
            // 只有「未配置映射」才提示去补渠道；字段超长/行数超限等直接说修正后重试
            String hint = parsed.mappingMissing > 0
                    ? "请先在「设置 → 渠道来源」补全该渠道后重新导入。"
                    : "请修正后重新导入。";
            throw new ServiceException("导入已中止：" + first
                    + "（共 " + parsed.blockers.size() + " 处）。" + hint);
        }

        // 3. replace 模式先清空
        if ("replace".equalsIgnoreCase(mode))
        {
            // 替换只清名单表，若此时已有签到记录，会产生「幽灵中奖者」：
            // 抽奖池直接从 kcon_checkin 取号（不 join 名单表），孤儿端口号照样进池，
            // 抽中后回查参会人返回 null，大屏定格页姓名/公司空白、兑奖也查不到持有人。
            // 所以只要当天已有签到，就拒绝替换，让用户改用追加模式或先做数据清场。
            int signed = checkinMapper.countAll();
            if (signed > 0)
            {
                throw new ServiceException(
                        "当前已有 " + signed + " 条签到记录，不能使用「替换」模式。"
                        + "替换会清空名单但保留签到记录，导致已签到人员变成无法兑奖的孤儿记录。"
                        + "请改用「追加」模式，或先在「数据清场」里清空数据再重新导入。");
            }
            // 用 DELETE 而非 TRUNCATE：TRUNCATE 属 DDL，会隐式提交，
            // 本方法上的 @Transactional 会因此失效——一旦后续解析/插入中途失败，
            // 就会留下「旧名单已清空、新名单未导入」的空档且无法回滚。
            // 名单规模有限（千级），DELETE 的开销可接受。
            attendeeMapper.clearAllAttendees();
        }

        int inserted = 0, updated = 0;
        for (KconAttendee a : parsed.rows)
        {
            int rows1 = attendeeMapper.upsertAttendee(a);
            // MySQL: 1=insert, 2=update（ON DUPLICATE KEY UPDATE）
            if (rows1 == 1)
            {
                inserted++;
            }
            else if (rows1 >= 2)
            {
                updated++;
            }
        }
        // upsert 返回 0 行的（理论上不会发生）计入跳过，保证三项之和与总行数一致
        int skipped = parsed.skipped + (parsed.rows.size() - inserted - updated);

        log.info("名单导入完成 mode={} 新增={} 更新={} 跳过={} 未配置映射={} 明细条数={}",
                mode, inserted, updated, skipped, parsed.mappingMissing, parsed.skipDetails.size());

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("total", attendeeMapper.countAll());
        stat.put("inserted", inserted);
        stat.put("updated", updated);
        stat.put("skipped", skipped);
        stat.put("skipDetails", parsed.skipDetails);
        stat.put("mappingMissing", parsed.mappingMissing);
        stat.put("mappingMissingDetails", parsed.mappingMissingDetails);
        stat.put("batch", parsed.batch);
        return stat;
    }

    @Override
    public Map<String, Object> previewImport(InputStream is, String fileName, String mode)
    {
        List<String[]> rows = readRows(is, fileName);
        // parseAttendees 内部不再抛阻塞异常（拦截上移到 importAttendees），
        // 预检可以直接复用，得到 blockers 让前端展示「哪些行不会入库」。
        ParsedImport parsed = parseAttendees(rows, mode, operator());

        // 预检不能落库，但 replace 模式至少要知道会不会因为已有签到被拒
        int signedTotal = checkinMapper.countAll();
        if ("replace".equalsIgnoreCase(mode) && signedTotal > 0)
        {
            throw new ServiceException("当前已有 " + signedTotal + " 条签到记录，不能使用「替换」模式。"
                    + "请改用「追加」模式，或先在「数据清场」里清空数据。");
        }

        // 新增 / 更新计数：按库中已存在的手机号区分
        List<String> phones = new ArrayList<>(parsed.rows.size());
        for (KconAttendee a : parsed.rows)
        {
            phones.add(a.getPhone());
        }
        List<String> existing = phones.isEmpty() ? new ArrayList<>()
                : attendeeMapper.selectExistingPhones(phones);
        int willUpdate = existing.size();
        int willInsert = parsed.rows.size() - willUpdate;

        // 渠道 → 权益/资格 的命中分布：现场最容易在这一栏看出映射漏配
        Map<String, Object> channelMap = new LinkedHashMap<>();
        for (KconAttendee a : parsed.rows)
        {
            String ch = a.getChannel() == null ? "" : a.getChannel();
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) channelMap.get(ch);
            if (item == null)
            {
                item = new LinkedHashMap<>();
                item.put("channel", ch);
                item.put("count", 0);
                item.put("mapped", parsed.mappedChannels.contains(ch));
                item.put("benefitType", a.getTicketType());
                item.put("drawEligible", a.getDrawEligible());
                channelMap.put(ch, item);
            }
            item.put("count", (Integer) item.get("count") + 1);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", parsed.blockers.isEmpty() ? (parsed.mappingMissing > 0 ? "WARN" : "OK") : "BLOCK");
        data.put("totalRows", rows.size() - 1);
        data.put("willInsert", willInsert);
        data.put("willUpdate", willUpdate);
        data.put("willSkip", parsed.skipped);
        data.put("blockers", parsed.blockers);
        data.put("warnings", parsed.warnings);
        data.put("channelStats", new ArrayList<>(channelMap.values()));
        data.put("unmappedChannels", parsed.mappingMissingDetails);
        data.put("skipDetails", parsed.skipDetails);
        return data;
    }

    /**
     * 读取文件为二维字符串数组（首行为表头）。
     */
    private List<String[]> readRows(InputStream is, String fileName)
    {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        List<String[]> rows;
        if (lower.endsWith(".csv"))
        {
            rows = readCsv(is);
        }
        else if (lower.endsWith(".xls") || lower.endsWith(".xlsx"))
        {
            rows = readExcel(is);
        }
        else
        {
            throw new ServiceException("仅支持 .xlsx / .xls / .csv 文件");
        }

        if (rows.isEmpty())
        {
            throw new ServiceException("文件内容为空");
        }
        return rows;
    }

    /**
     * 解析 + 逐行校验，产出待入库记录。
     * 预检与正式导入共用本方法，保证「预检说没问题」和「实际导进去」完全一致。
     */
    private ParsedImport parseAttendees(List<String[]> rows, String mode, String operator)
    {
        ParsedImport parsed = new ParsedImport();
        parsed.batch = "B" + System.currentTimeMillis();

        // 1. 解析表头 → 列索引映射
        String[] header = rows.get(0);
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < header.length; i++)
        {
            String h = normalize(header[i]);
            if (h.isEmpty())
            {
                continue;
            }
            String field = HEADER_ALIAS.get(h);
            if (field != null && !colIndex.containsKey(field))
            {
                colIndex.put(field, i);
            }
        }

        // 2. 必填列校验
        List<String> missing = new ArrayList<>();
        for (String req : REQUIRED)
        {
            if (!colIndex.containsKey(req))
            {
                missing.add(FIELD_CN.get(req));
            }
        }
        if (!missing.isEmpty())
        {
            // 一个字段都没认出来 = 大概率是旧模板或别的表，直接说清楚，别让操作员猜
            if (colIndex.isEmpty())
            {
                throw new ServiceException("无法识别表头，请使用系统提供的导入模板"
                        + "（6 列：姓名、手机号、渠道来源、公司、职位、邮箱）。"
                        + "旧版模板（含「渠道标识」「票种」「是否参与抽奖」等表头）已不再支持。");
            }
            throw new ServiceException("缺少必填列：" + String.join("、", missing)
                    + "。请使用系统提供的导入模板（6 列：姓名、手机号、渠道来源、公司、职位、邮箱），"
                    + "旧版模板已不再支持。");
        }

        int phoneIdx = colIndex.get("phone");
        // 行数上限：先挡住超大文件，避免逐行解析把内存和响应体撑爆
        int dataRows = rows.size() - 1;
        if (dataRows > MAX_IMPORT_ROWS)
        {
            addBlocker(parsed.blockers, 0,
                    "行数超过上限（最多 " + MAX_IMPORT_ROWS + " 行，当前 " + dataRows + " 行）");
            return parsed;
        }
        Map<String, ChannelMapping> channelMappings = loadChannelMappings();
        // 文件内手机号去重：重复行按后者覆盖前者，但要在预检里提示
        Map<String, Integer> seenPhones = new HashMap<>();

        for (int r = 1; r < rows.size(); r++)
        {
            String[] row = rows.get(r);
            int lineNo = r + 1; // 第 1 行是表头，数据从第 2 行起，与 Excel 行号一致

            String phone = cell(row, phoneIdx).trim();
            if (phone.isEmpty())
            {
                parsed.skipped++;
                addSkipDetail(parsed.skipDetails, lineNo, "手机号为空");
                continue;
            }
            if (!phone.matches("1[3-9]\\d{9}"))
            {
                parsed.skipped++;
                addSkipDetail(parsed.skipDetails, lineNo, "手机号格式不正确：" + phone);
                continue;
            }
            Integer firstLine = seenPhones.put(phone, lineNo);
            if (firstLine != null)
            {
                addWarning(parsed.warnings, lineNo,
                        "手机号 " + mask(phone) + " 与第 " + firstLine + " 行重复，将以本行为准");
            }

            // 「必填列存在」不等于「值必填」：列缺失已在上面统一拦截，
            // 这里必须逐行校验取值，否则空姓名/空渠道照样入库，
            // 之后按渠道统计时就会出现无标签的脏数据
            String name = cell(row, colIndex.get("name")).trim();
            if (name.isEmpty())
            {
                parsed.skipped++;
                addSkipDetail(parsed.skipDetails, lineNo, "姓名为空");
                continue;
            }
            String channel = cell(row, colIndex.get("channel")).trim();
            if (channel.isEmpty())
            {
                parsed.skipped++;
                addSkipDetail(parsed.skipDetails, lineNo, "渠道来源为空");
                continue;
            }

            // 选填列先取值再统一做长度校验：超长字段若直接入库会被 MySQL 截断或报错，
            // 这里按实体 @Size 的同一口径记 blocker，预检阶段就能看见具体是哪一行。
            String company = colIndex.containsKey("company")
                    ? emptyToNull(cell(row, colIndex.get("company"))) : null;
            String email = colIndex.containsKey("email")
                    ? emptyToNull(cell(row, colIndex.get("email"))) : null;
            String position = colIndex.containsKey("position")
                    ? emptyToNull(cell(row, colIndex.get("position"))) : null;
            String tooLong = null;
            if (name.length() > MAX_NAME_LENGTH)
            {
                tooLong = "姓名长度超过 " + MAX_NAME_LENGTH + " 个字符";
            }
            else if (company != null && company.length() > MAX_COMPANY_LENGTH)
            {
                tooLong = "公司名称长度超过 " + MAX_COMPANY_LENGTH + " 个字符";
            }
            else if (email != null && email.length() > MAX_EMAIL_LENGTH)
            {
                tooLong = "邮箱长度超过 " + MAX_EMAIL_LENGTH + " 个字符";
            }
            else if (position != null && position.length() > MAX_POSITION_LENGTH)
            {
                tooLong = "职位长度超过 " + MAX_POSITION_LENGTH + " 个字符";
            }
            if (tooLong != null)
            {
                parsed.skipped++;
                addBlocker(parsed.blockers, lineNo, tooLong);
                continue;
            }

            // 权益类型 / 参与抽奖完全由映射决定：
            // 6 列模板不含这两列，票务表里即使有也不再读取。
            ChannelMapping mapping = channelMappings.get(channel);
            String ticketType;
            int drawEligible;
            if (mapping != null)
            {
                ticketType = mapping.benefitType;
                drawEligible = mapping.drawEligible;
                parsed.mappedChannels.add(channel);
            }
            else
            {
                // 未配置映射 = 硬性白名单不通过（2026-10-21 用户确认）：
                // 映射表是「允许导入的渠道」唯一事实源，未配置的渠道一律不入库。
                // 以前是回退行内值，结果没配映射的渠道也能进名单，再被抽中——
                // 只能靠事后人肉比对才发现，所以这里直接跳过并计入 blocker。
                parsed.skipped++;
                parsed.mappingMissing++;
                addMappingMissingDetail(parsed.mappingMissingDetails, lineNo, channel);
                addBlocker(parsed.blockers, lineNo,
                        "渠道来源「" + channel + "」未配置映射，该渠道不允许导入（可在「渠道来源映射」新增后重试）");
                continue;
            }

            KconAttendee a = new KconAttendee();
            a.setName(name);
            a.setPhone(phone);
            a.setChannel(channel);
            a.setTicketType(emptyToNull(ticketType));
            a.setDrawEligible(drawEligible);
            a.setPhoneTail4(tail4(phone));
            a.setImportBatch(parsed.batch);
            a.setCreateBy(operator);
            a.setCompany(company);
            a.setEmail(email);
            a.setPosition(position);

            parsed.rows.add(a);
        }

        // 一行有效数据都没有（表头以下为空、或全部行因手机号/姓名/渠道为空被跳过）：
        // 以前 blockers 为空会放行，replace 模式先清空名单再循环 0 次，返回「新增 0」成功——
        // 现场传一份空模板就把名单静默清空。这里补一条 blocker，预检显示 BLOCK、
        // 正式导入抛异常中止，两条路径共用同一判断
        if (parsed.rows.isEmpty() && parsed.blockers.isEmpty())
        {
            addBlocker(parsed.blockers, 0,
                    "没有可导入的有效数据行（表头以下为空，或所有行都因手机号/姓名/渠道来源为空被跳过）");
        }

        return parsed;
    }

    /** 解析结果：一次解析，两种用途（预检展示 / 正式导入落库） */
    private static final class ParsedImport
    {
        final List<KconAttendee> rows = new ArrayList<>();
        final List<Map<String, Object>> skipDetails = new ArrayList<>();
        final List<Map<String, Object>> mappingMissingDetails = new ArrayList<>();
        final List<Map<String, Object>> blockers = new ArrayList<>();
        final List<Map<String, Object>> warnings = new ArrayList<>();
        /** 命中了映射的渠道集合（预检判断某渠道是否已配置用） */
        final java.util.Set<String> mappedChannels = new java.util.HashSet<>();
        int skipped;
        int mappingMissing;
        String batch;
    }

    /** 预检明细上限：超过后只计数不列明细，避免响应体过大 */
    private static final int PREVIEW_DETAIL_LIMIT = 50;

    private void addBlocker(List<Map<String, Object>> details, int lineNo, String reason)
    {
        if (details.size() >= PREVIEW_DETAIL_LIMIT)
        {
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("row", lineNo);
        d.put("reason", reason);
        details.add(d);
    }

    private void addWarning(List<Map<String, Object>> details, int lineNo, String reason)
    {
        addBlocker(details, lineNo, reason);
    }

    /** 预检不落库，操作人仅用于构造记录，取当前登录用户 */
    private String operator()
    {
        try
        {
            return com.ruoyi.common.utils.SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            // 无登录上下文（如内部调用）时退回占位，不影响解析
            return "";
        }
    }

    /** 手机号脱敏（预检回显用，避免把完整号码带到前端） */
    private String mask(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 渠道来源映射条目 */
    private static final class ChannelMapping
    {
        final String benefitType;
        final int drawEligible;

        ChannelMapping(String benefitType, int drawEligible)
        {
            this.benefitType = benefitType;
            this.drawEligible = drawEligible;
        }
    }

    /**
     * 校验渠道来源是否已配置映射（映射表即允许入库的渠道白名单）。
     *
     * 映射表整表为空时同样拒绝：与导入口径一致（空表 = 不放行任何渠道），
     * 否则「手工能加、导入不能导」会让空表状态下的白名单形同虚设。
     * 手工新增的入口本身就在「渠道来源映射」菜单旁，配置路径很短，不会把人锁死。
     */
    private void checkChannelAllowed(String channel)
    {
        if (channel == null || channel.trim().isEmpty())
        {
            throw new ServiceException("渠道来源不能为空，请从已配置的渠道中选择");
        }
        Map<String, ChannelMapping> mappings = loadChannelMappings();
        if (mappings.isEmpty())
        {
            // 与导入同一口径：映射表是「允许使用的渠道」唯一事实源，空表 = 一个渠道都不放行。
            // 以前这里告警放行、导入却整批拒绝，口径不一致会让人以为「手工能加就说明渠道合法」。
            throw new ServiceException("渠道来源映射表为空，请先在「渠道来源」中配置后再添加名单");
        }
        if (!mappings.containsKey(channel.trim()))
        {
            throw new ServiceException("渠道来源「" + channel.trim()
                    + "」未配置映射，不允许使用。请先在「设置 → 渠道来源」新增该渠道。");
        }
    }

    /**
     * 读取渠道来源映射表 kcon_channel_mapping。
     *
     * 读取失败或表为空时返回空映射。
     */
    private Map<String, ChannelMapping> loadChannelMappings()
    {
        Map<String, ChannelMapping> map = new HashMap<>();
        try
        {
            List<KconChannelMapping> list = channelMappingService.selectAll();
            if (list == null)
            {
                return map;
            }
            for (KconChannelMapping m : list)
            {
                String channel = m.getChannelSource() == null ? "" : m.getChannelSource().trim();
                if (channel.isEmpty())
                {
                    continue;
                }
                String benefit = m.getBenefitType() == null ? "" : m.getBenefitType().trim();
                int eligible = Integer.valueOf(1).equals(m.getDrawEligible()) ? 1 : 0;
                map.put(channel, new ChannelMapping(benefit, eligible));
            }
        }
        catch (Exception e)
        {
            // 读取失败按空表处理：白名单口径下空表 = 一个渠道都不放行（手工与导入一致），
            // 宁可现场先配置好映射，也不能在映射读不到时悄悄放行未配置的渠道
            log.warn("读取渠道来源映射失败，白名单校验按空表处理（所有渠道将被拒绝）", e);
        }
        return map;
    }

    /** 未配置映射明细上限 */
    private static final int MAPPING_MISSING_DETAIL_LIMIT = 20;

    private void addMappingMissingDetail(List<Map<String, Object>> details, int lineNo, String channel)
    {
        if (details.size() >= MAPPING_MISSING_DETAIL_LIMIT)
        {
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("row", lineNo);
        d.put("channel", channel);
        d.put("reason", "渠道来源未配置映射，该渠道不允许导入");
        details.add(d);
    }

    /** 失败行明细上限：够现场定位问题即可，避免响应体过大 */
    private static final int SKIP_DETAIL_LIMIT = 20;

    private void addSkipDetail(List<Map<String, Object>> details, int lineNo, String reason)
    {
        if (details.size() >= SKIP_DETAIL_LIMIT)
        {
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("row", lineNo);
        d.put("reason", reason);
        details.add(d);
    }

    @Override
    public Map<String, Object> dashboardStat()
    {
        int total = attendeeMapper.countAll();
        int signed = attendeeMapper.countSigned();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("signed", signed);
        data.put("unsigned", Math.max(total - signed, 0));
        data.put("signRate", total == 0 ? 0.0
                : Math.round(signed * 1000.0 / total) / 10.0);
        data.put("topCompanies", attendeeMapper.statTopCompanies(10));
        return data;
    }

    @Override
    public List<KconAttendee> selectSignSheet()
    {
        List<KconAttendee> list = attendeeMapper.selectSignSheet();
        for (KconAttendee a : list)
        {
            a.setPhoneMask(IKconCheckinService.maskPhone(a.getPhone()));
        }
        return list;
    }

    // ---------------- 内部工具 ----------------

    private List<String[]> readExcel(InputStream is)
    {
        List<String[]> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(is))
        {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet)
            {
                if (row == null)
                {
                    continue;
                }
                int last = Math.max(row.getLastCellNum(), 0);
                String[] arr = new String[last];
                for (int c = 0; c < last; c++)
                {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    arr[c] = cell == null ? "" : fmt.formatCellValue(cell).trim();
                }
                rows.add(arr);
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("Excel 解析失败：" + e.getMessage());
        }
        return rows;
    }

    private List<String[]> readCsv(InputStream is)
    {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null)
            {
                if (first)
                {
                    // 去掉 BOM
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF')
                    {
                        line = line.substring(1);
                    }
                    first = false;
                }
                if (line.trim().isEmpty())
                {
                    continue;
                }
                rows.add(splitCsv(line));
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("CSV 解析失败：" + e.getMessage());
        }
        return rows;
    }

    /** 简易 CSV 拆分（支持双引号包裹） */
    private String[] splitCsv(String line)
    {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++)
        {
            char ch = line.charAt(i);
            if (ch == '"')
            {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"')
                {
                    sb.append('"');
                    i++;
                }
                else
                {
                    inQuote = !inQuote;
                }
            }
            else if (ch == ',' && !inQuote)
            {
                out.add(sb.toString());
                sb.setLength(0);
            }
            else
            {
                sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    /** 表头归一化：去空格、转小写、全角括号转半角 */
    private String normalize(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.replace("（", "(").replace("）", ")")
                .replace(" ", "").replace("\u3000", "")
                .toLowerCase().trim();
    }

    private String cell(String[] row, Integer idx)
    {
        if (idx == null || idx >= row.length || row[idx] == null)
        {
            return "";
        }
        return row[idx];
    }

    private String emptyToNull(String s)
    {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private String tail4(String phone)
    {
        if (phone == null || phone.length() < 4)
        {
            return null;
        }
        return phone.substring(phone.length() - 4);
    }
}
