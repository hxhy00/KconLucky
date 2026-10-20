package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.service.IKconAttendeeService;
import com.ruoyi.kcon.service.IKconCheckinService;
import com.ruoyi.system.service.ISysDictTypeService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
 * 导入容错设计：
 *   - 按表头名匹配列，顺序任意
 *   - 必填 3 列：姓名 / 手机号 / 渠道来源
 *   - 选填 5 列：权益类型 / 参与抽奖 / 公司 / 职位 / 邮箱
 *   - 权益类型与参与抽奖留空时，按渠道来源查字典 kcon_channel_mapping 自动带出；
 *     命中映射时映射优先，未命中时回退到行内值
 *   - 未识别的列直接忽略（例如票务总表带来的 购票时间/金额/发票需求）
 *   - 兼容旧表头：渠道标识、渠道、票种、票务类型、是否参与抽奖、职务/年级、职务、年级
 *
 * @author kcon
 */
@Service
public class KconAttendeeServiceImpl implements IKconAttendeeService
{
    private static final Logger log = LoggerFactory.getLogger(KconAttendeeServiceImpl.class);

    /** 表头别名 → 标准字段名 */
    private static final Map<String, String> HEADER_ALIAS = new HashMap<>();

    static
    {
        HEADER_ALIAS.put("姓名", "name");
        HEADER_ALIAS.put("手机号", "phone");
        HEADER_ALIAS.put("手机", "phone");
        HEADER_ALIAS.put("电话", "phone");
        HEADER_ALIAS.put("渠道标识", "channel");
        HEADER_ALIAS.put("渠道", "channel");
        HEADER_ALIAS.put("渠道来源", "channel");
        HEADER_ALIAS.put("票种", "ticketType");
        HEADER_ALIAS.put("票务类型", "ticketType");
        HEADER_ALIAS.put("权益类型", "ticketType");
        HEADER_ALIAS.put("公司", "company");
        HEADER_ALIAS.put("邮箱", "email");
        HEADER_ALIAS.put("第几次参加kcon", "attendCount");
        HEADER_ALIAS.put("第几次参加", "attendCount");
        HEADER_ALIAS.put("参加次数", "attendCount");
        HEADER_ALIAS.put("职务/年级", "position");
        HEADER_ALIAS.put("职务", "position");
        HEADER_ALIAS.put("年级", "position");
        HEADER_ALIAS.put("职位", "position");
        HEADER_ALIAS.put("是否参与抽奖", "drawEligible");
        HEADER_ALIAS.put("参与抽奖", "drawEligible");
    }

    /** 必填列（标准字段名）：只保留身份与来源，权益/资格由映射带出 */
    private static final List<String> REQUIRED = List.of("name", "phone", "channel");

    /** 必填列的中文名（报错用） */
    private static final Map<String, String> FIELD_CN = Map.of(
            "name", "姓名", "phone", "手机号", "channel", "渠道来源"
    );

    /** 渠道来源映射字典类型 */
    private static final String DICT_CHANNEL_MAPPING = "kcon_channel_mapping";

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Override
    public List<KconAttendee> selectAttendeeList(KconAttendee query)
    {
        List<KconAttendee> list = attendeeMapper.selectAttendeeList(query);
        for (KconAttendee a : list)
        {
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
        attendee.setPhoneTail4(tail4(attendee.getPhone()));
        attendee.setPhone(attendee.getPhone().trim());
        // draw_eligible 列 NOT NULL：直连 API 新增时可能不带该字段，缺省按「否」
        // （与导入口径一致：只有显式「是」才进池），避免插 null 触发 SQL 错误
        if (attendee.getDrawEligible() == null)
        {
            attendee.setDrawEligible(0);
        }
        return attendeeMapper.insertAttendee(attendee);
    }

    @Override
    public int updateAttendee(KconAttendee attendee)
    {
        if (attendee.getPhone() != null && !attendee.getPhone().trim().isEmpty())
        {
            attendee.setPhone(attendee.getPhone().trim());
            attendee.setPhoneTail4(tail4(attendee.getPhone()));
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
            throw new ServiceException("缺少必填列：" + String.join("、", missing)
                    + "。正确表头应包含：姓名、手机号、渠道来源（权益类型/参与抽奖/公司/职位/邮箱为选填）");
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

        String batch = "B" + System.currentTimeMillis();
        int inserted = 0, updated = 0, skipped = 0;
        // 未命中映射且未填「参与抽奖」的行数：该行照常入库（按「否」），单独计数提示
        int drawFlagMissing = 0;
        int mappingMissing = 0;
        int phoneIdx = colIndex.get("phone");
        Integer ticketIdx = colIndex.get("ticketType");
        Integer drawIdx = colIndex.get("drawEligible");
        Map<String, ChannelMapping> channelMappings = loadChannelMappings();
        // 记录被跳过行的行号与原因：只返回一个「跳过 N 条」，现场拿着 Excel 不知道要改哪几行
        List<Map<String, Object>> skipDetails = new ArrayList<>();
        // 记录漏填「参与抽奖」的行号：导入成功但这些人按「否」处理，会失去抽奖资格
        List<Map<String, Object>> flagMissingDetails = new ArrayList<>();
        // 记录未配置映射的渠道来源：提示到字典管理补充，而不是让整批人静默失去资格
        List<Map<String, Object>> mappingMissingDetails = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++)
        {
            String[] row = rows.get(r);
            int lineNo = r + 1; // 第 1 行是表头，数据从第 2 行起，与 Excel 行号一致

            String phone = cell(row, phoneIdx).trim();
            if (phone.isEmpty())
            {
                skipped++;
                addSkipDetail(skipDetails, lineNo, "手机号为空");
                continue;
            }
            if (!phone.matches("1[3-9]\\d{9}"))
            {
                skipped++;
                addSkipDetail(skipDetails, lineNo, "手机号格式不正确：" + phone);
                continue;
            }

            // 「必填列存在」不等于「值必填」：列缺失已在上面统一拦截，
            // 这里必须逐行校验取值，否则空姓名/空渠道照样入库，
            // 之后按渠道统计时就会出现无标签的脏数据
            String name = cell(row, colIndex.get("name")).trim();
            if (name.isEmpty())
            {
                skipped++;
                addSkipDetail(skipDetails, lineNo, "姓名为空");
                continue;
            }
            String channel = cell(row, colIndex.get("channel")).trim();
            if (channel.isEmpty())
            {
                skipped++;
                addSkipDetail(skipDetails, lineNo, "渠道来源为空");
                continue;
            }

            // 权益类型 / 参与抽奖为选填：先取行内值，再按渠道来源查映射。
            // 映射优先（2026-10-19 用户确认）：命中映射时忽略行内填写。
            String explicitTicket = ticketIdx == null ? "" : cell(row, ticketIdx).trim();
            String explicitDraw = drawIdx == null ? "" : cell(row, drawIdx).trim();

            ChannelMapping mapping = channelMappings.get(channel);
            String ticketType;
            int drawEligible;
            if (mapping != null)
            {
                ticketType = mapping.benefitType;
                drawEligible = mapping.drawEligible;
            }
            else
            {
                // 未配置映射：回退到行内值；都为空时按「否」入库并提示补映射
                ticketType = explicitTicket;
                drawEligible = "是".equals(explicitDraw) ? 1 : 0;
                mappingMissing++;
                addMappingMissingDetail(mappingMissingDetails, lineNo, channel);
                if (explicitDraw.isEmpty())
                {
                    // 该行照常入库（按「否」），只做单独计数提示：漏填行名单缺失会
                    // 让持有人失去抽奖资格，现场需要知道具体行号去补。不并入 skipped——
                    // skipped 表示「没导入」，这行导入了，混在一起统计会对不上。
                    drawFlagMissing++;
                    addFlagMissingDetail(flagMissingDetails, lineNo);
                }
            }

            KconAttendee a = new KconAttendee();
            a.setName(name);
            a.setPhone(phone);
            a.setChannel(channel);
            a.setTicketType(emptyToNull(ticketType));
            a.setDrawEligible(drawEligible);
            a.setPhoneTail4(tail4(phone));
            a.setImportBatch(batch);
            a.setCreateBy(operator);

            if (colIndex.containsKey("company"))
            {
                a.setCompany(emptyToNull(cell(row, colIndex.get("company"))));
            }
            if (colIndex.containsKey("email"))
            {
                a.setEmail(emptyToNull(cell(row, colIndex.get("email"))));
            }
            if (colIndex.containsKey("position"))
            {
                a.setPosition(emptyToNull(cell(row, colIndex.get("position"))));
            }
            if (colIndex.containsKey("attendCount"))
            {
                a.setAttendCount(parseInt(cell(row, colIndex.get("attendCount"))));
            }

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
            else
            {
                skipped++;
            }
        }

        log.info("名单导入完成 mode={} 新增={} 更新={} 跳过={} 未配置映射={} 漏填参与标记={} 明细条数={}",
                mode, inserted, updated, skipped, mappingMissing, drawFlagMissing, skipDetails.size());

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("total", attendeeMapper.countAll());
        stat.put("inserted", inserted);
        stat.put("updated", updated);
        stat.put("skipped", skipped);
        stat.put("skipDetails", skipDetails);
        stat.put("mappingMissing", mappingMissing);
        stat.put("mappingMissingDetails", mappingMissingDetails);
        stat.put("drawFlagMissing", drawFlagMissing);
        stat.put("flagMissingDetails", flagMissingDetails);
        stat.put("batch", batch);
        return stat;
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
     * 读取字典 kcon_channel_mapping。
     *
     * 字典口径：dict_label=渠道来源，dict_value=权益类型，remark=是/否。
     * 读取失败或字典未配置时返回空映射，导入回退到行内值。
     */
    private Map<String, ChannelMapping> loadChannelMappings()
    {
        Map<String, ChannelMapping> map = new HashMap<>();
        try
        {
            List<SysDictData> list = dictTypeService.selectDictDataByType(DICT_CHANNEL_MAPPING);
            if (list == null)
            {
                return map;
            }
            for (SysDictData d : list)
            {
                // 只使用正常状态的字典项
                if (!"0".equals(d.getStatus()))
                {
                    continue;
                }
                String label = d.getDictLabel() == null ? "" : d.getDictLabel().trim();
                if (label.isEmpty())
                {
                    continue;
                }
                String benefit = d.getDictValue() == null ? "" : d.getDictValue().trim();
                String flag = d.getRemark() == null ? "" : d.getRemark().trim();
                map.put(label, new ChannelMapping(benefit, "是".equals(flag) ? 1 : 0));
            }
        }
        catch (Exception e)
        {
            log.warn("读取渠道来源映射失败，导入将回退到行内值", e);
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
        d.put("reason", "渠道来源未配置映射，按不参与处理");
        details.add(d);
    }

    /** 漏填「参与抽奖」明细上限：与失败行明细一致，够现场定位即可 */
    private static final int FLAG_MISSING_DETAIL_LIMIT = 20;

    private void addFlagMissingDetail(List<Map<String, Object>> details, int lineNo)
    {
        if (details.size() >= FLAG_MISSING_DETAIL_LIMIT)
        {
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("row", lineNo);
        d.put("reason", "未配置映射且参与抽奖为空（按「否」处理）");
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
        Map<String, Object> dist = attendeeMapper.statAttendCount();
        data.put("attendCountDist", dist != null ? dist : new LinkedHashMap<>());
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

    private Integer parseInt(String s)
    {
        if (s == null || s.trim().isEmpty())
        {
            return null;
        }
        try
        {
            double d = Double.parseDouble(s.trim());
            int v = (int) d;
            return v >= 1 ? v : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
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
