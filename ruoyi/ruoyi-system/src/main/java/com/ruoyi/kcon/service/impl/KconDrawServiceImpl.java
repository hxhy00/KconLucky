package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.domain.KconSession;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPrizeMapper;
import com.ruoyi.kcon.mapper.KconSessionMapper;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.kcon.service.IKconCheckinService;
import com.ruoyi.kcon.service.IKconDrawService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 抽奖业务实现
 *
 * 并发控制：
 *   - draw() 为 synchronized 方法级锁，抽奖是单点操作，串行化即可
 *   - 中奖写入前再次校验该端口号是否已有有效中奖记录（status != 2），
 *     保证「一号只中一次」在数据库层面成立
 *
 * @author kcon
 */
@Service
public class KconDrawServiceImpl implements IKconDrawService
{
    private static final Logger log = LoggerFactory.getLogger(KconDrawServiceImpl.class);

    /** 全局抽奖锁（单机部署，方法级锁足够） */
    private final Object drawLock = new Object();

    /**
     * 事务模板。抽奖必须严格按「加锁 → 开事务 → 提交 → 解锁」执行，
     * 因此不能用方法级 @Transactional（那样提交发生在方法返回、锁释放之后）。
     */
    private final TransactionTemplate transactionTemplate;

    public KconDrawServiceImpl(PlatformTransactionManager transactionManager)
    {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Autowired
    private KconSessionMapper sessionMapper;

    @Autowired
    private KconPrizeMapper prizeMapper;

    @Autowired
    private KconWinnerMapper winnerMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    /** 大屏赞助商等运行期配置走系统参数，服务不可用时降级为不影响主流程 */
    @Autowired
    private ISysConfigService sysConfigService;

    /** 大屏赞助商的系统参数键名 */
    private static final String SPONSORS_KEY = "kcon.sponsors";

    @Override
    public Map<String, Object> screenStatus()
    {
        List<KconSession> sessions = sessionMapper.selectSessionList();
        KconSession current = resolveCurrentSession(sessions);
        String sessionCode = current != null ? current.getCode() : "AM";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionCode", sessionCode);
        data.put("sessionName", current != null ? current.getName() : "");
        data.put("checkedInToday", checkinMapper.countToday());
        data.put("poolCount", calcPool(sessionCode).size());
        // 注意：此方法**同时服务免登录的大屏接口**（DrawScreenController /app/screen/status），
        // 因此这里只放「观众可见也不构成泄露」的字段。
        // spec 006 的 excludedCount 刻意不在此处返回——它会让
        // checkedInToday - poolCount 的差额含义被匿名请求读到，等于对外公布筛选口径。
        // 该字段由管理端 DrawAdminController#buildStatus 自行附加（FR-A7/FR-A8）。
        data.put("winnerCount", winnerMapper.countWinners(null));
        data.put("sessions", sessions);

        // 当前应抽档位
        KconPrize tier = prizeMapper.selectCurrentTier(sessionCode);
        if (tier != null)
        {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", tier.getId());
            t.put("tierName", tier.getTierName());
            t.put("quantity", tier.getQuantity());
            t.put("drawnCount", tier.getDrawnCount());
            data.put("currentTier", t);
        }

        // 全部档位及各自已抽数量（大屏做进度可视化用，FR-19）
        // 只给「本场次」的档位，避免两场奖品混在一张进度条上
        KconPrize tierQuery = new KconPrize();
        tierQuery.setSessionCode(sessionCode);
        List<KconPrize> allTiers = prizeMapper.selectPrizeList(tierQuery);
        List<Map<String, Object>> tierViews = new ArrayList<>();
        int totalQuantity = 0;
        int totalDrawn = 0;
        for (KconPrize p : allTiers)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("tierName", p.getTierName());
            m.put("quantity", p.getQuantity());
            m.put("drawnCount", p.getDrawnCount());
            tierViews.add(m);
            if (p.getQuantity() != null)
            {
                totalQuantity += p.getQuantity();
            }
            if (p.getDrawnCount() != null)
            {
                totalDrawn += p.getDrawnCount();
            }
        }
        data.put("tiers", tierViews);
        data.put("totalQuantity", totalQuantity);
        data.put("totalDrawn", totalDrawn);

        // 赞助商（存系统参数 kcon.sponsors，逗号分隔；为空则不显示赞助商条）
        List<String> sponsorList = sponsors();
        data.put("sponsors", sponsorList);

        // 最近一条有效中奖记录：大屏据此显示「在管理端抽奖控制台抽出」的中奖者。
        // 以前这个字段不存在，大屏只能靠 poolCount 推断状态，于是管理端抽了奖、
        // 大屏却一直停在上一轮，而两边文案还写着「看到的是同一个抽奖池」。
        KconWinner last = winnerMapper.selectLatestValidWinner();
        if (last != null)
        {
            String lastWinTime = format(last.getWinTime());
            Map<String, Object> lw = new LinkedHashMap<>();
            lw.put("winnerId", last.getId());
            lw.put("portNo", last.getPortNo());
            lw.put("name", last.getName());
            lw.put("company", last.getCompany());
            lw.put("tierName", last.getTierName());
            lw.put("sessionCode", last.getSessionCode());
            lw.put("winTime", lastWinTime);
            lw.put("operator", last.getOperator());
            data.put("lastWinner", lw);

            // 整批：同一档位 + 同一中奖时间就是「这一次点击抽出的全部人」。
            // 大屏靠它依次揭晓；管理端在控制台代抽也能在大屏上完整播报，不再只播最后一个。
            if (last.getPrizeId() != null && lastWinTime != null)
            {
                List<Map<String, Object>> batchViews = new ArrayList<>();
                for (KconWinner w : winnerMapper.selectBatch(last.getPrizeId(), lastWinTime))
                {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("winnerId", w.getId());
                    m.put("portNo", w.getPortNo());
                    m.put("name", w.getName());
                    m.put("company", w.getCompany());
                    m.put("position", w.getPosition());
                    batchViews.add(m);
                }
                Map<String, Object> lb = new LinkedHashMap<>();
                // 末尾带上人数：批次里有人被作废时这个 key 也会变，
                // 大屏据此察觉「同一批少了一个人」并同步汇总页
                lb.put("batchKey", last.getPrizeId() + "@" + lastWinTime + "@" + batchViews.size());
                lb.put("tierName", last.getTierName());
                lb.put("count", batchViews.size());
                lb.put("winners", batchViews);
                data.put("lastBatch", lb);
            }
        }

        // 增量轮询的版本号：以上任何一项变化都会让它变。
        // 大屏下次把它带回来，未变时后端只回一个极小响应、前端也跳过重渲染
        //（投屏中整份重渲染会打断中奖动画）。
        data.put("version", String.join("|",
                sessionCode,
                String.valueOf(data.get("checkedInToday")),
                String.valueOf(data.get("poolCount")),
                String.valueOf(data.get("winnerCount")),
                String.valueOf(totalDrawn),
                tier != null ? String.valueOf(tier.getId()) : "-",
                tier != null ? String.valueOf(tier.getDrawnCount()) : "-",
                last != null ? String.valueOf(last.getId()) : "-",
                String.join(",", sponsorList)));
        return data;
    }

    /**
     * 当前场次：优先取 status=1 的进行中场次；都没激活时退回排序第一的场次。
     * 大屏展示与「未指定场次时抽奖」共用这一口径，避免后者写死 AM 导致
     * 下午场抽中上午场的档位。
     */
    private KconSession resolveCurrentSession(List<KconSession> sessions)
    {
        if (sessions == null || sessions.isEmpty())
        {
            return null;
        }
        for (KconSession s : sessions)
        {
            if (s.getStatus() != null && s.getStatus() == 1)
            {
                return s;
            }
        }
        return sessions.get(0);
    }

    /**
     * 读取大屏赞助商配置
     * 来源：系统参数 kcon.sponsors（管理员在「抽奖控制台 → 大屏设置」里改）
     */
    private List<String> sponsors()
    {
        List<String> list = new ArrayList<>();
        // 用 SysConfigService 读，RuoYi 会走 sys_config: 前缀的缓存；
        // 自己按裸 key 读 Redis 读不到（SysConfigService 写入时加了前缀）
        String raw = sysConfigService.selectConfigByKey(SPONSORS_KEY);
        if (StringUtils.isEmpty(raw))
        {
            return list;
        }
        for (String item : raw.split("[,，]"))
        {
            String trimmed = item.trim();
            if (StringUtils.isNotEmpty(trimmed))
            {
                list.add(trimmed);
            }
        }
        return list;
    }

    @Override
    public void switchSession(String sessionCode)
    {
        if (sessionCode == null || sessionMapper.selectByCode(sessionCode) == null)
        {
            throw new ServiceException("场次不存在：" + sessionCode);
        }
        sessionMapper.activate(sessionCode);
        log.info("切换场次 -> {}", sessionCode);
    }

    @Override
    public List<Integer> calcPool(String sessionCode)
    {
        // spec 006：资格判定参数优先（系统参数实时生效，改参数当场拦截），
        // 名单显式列 draw_eligible 作基础资格；口径详见 DrawEligibility。
        // 过滤是池计算的一部分而非只在抽奖执行时校验（FR-A2），
        // 否则控制台与大屏的「可抽号数」会虚高，操作员会按错误人数判断节奏。
        Set<String> exclChannels = DrawEligibility.excludeChannels(sysConfigService);
        List<String> exclKeywords = DrawEligibility.excludeTicketKeywords(sysConfigService);

        // 1. 已签到（当日）+ 联表带出渠道/票种/参与标记（selectAllPorts 已 join，
        //    避免对每个端口号逐条回查参会人——千人池 = 千次查询的 N+1）
        List<KconCheckin> todayCheckins = checkinMapper.selectAllPorts();

        // 2. 已中奖端口号（全场，含上午场）
        Set<Integer> wonPorts = new HashSet<>(winnerMapper.selectAllWonPorts());

        // 下午场额外剔除上午场已中奖（其实全场已中奖已包含，这里保持语义清晰）
        List<Integer> pool = new ArrayList<>();
        for (KconCheckin c : todayCheckins)
        {
            if (wonPorts.contains(c.getPortNo()))
            {
                continue;
            }
            if (DrawEligibility.isEligible(c.getDrawEligible(), c.getChannel(),
                    c.getTicketType(), exclChannels, exclKeywords))
            {
                pool.add(c.getPortNo());
            }
        }
        Collections.sort(pool);
        return pool;
    }

    @Override
    public int countExcludedToday()
    {
        // FR-A7 可观测性：控制台解释「今日已签到 N、可抽号数 M」的差值。
        // 与 calcPool 用同一份联表查询 + 同一套判定，保证两个数字口径一致。
        Set<String> exclChannels = DrawEligibility.excludeChannels(sysConfigService);
        List<String> exclKeywords = DrawEligibility.excludeTicketKeywords(sysConfigService);

        int excluded = 0;
        for (KconCheckin c : checkinMapper.selectAllPorts())
        {
            if (!DrawEligibility.isEligible(c.getDrawEligible(), c.getChannel(),
                    c.getTicketType(), exclChannels, exclKeywords))
            {
                excluded++;
            }
        }
        return excluded;
    }

    @Override
    public Map<String, Object> draw(String sessionCode, Long prizeId, String operator)
    {
        // 锁必须在事务提交之后才释放。
        // 原实现是「方法级 @Transactional + 方法体内 synchronized」：
        // synchronized 在方法返回时释放，而事务由 Spring 代理在方法返回之后才提交，
        // 两者之间存在窗口期——另一线程随即进入临界区，在 REPEATABLE READ 下会读到
        // 不含前一次未提交中奖行的快照，第 4 步「二次校验」同样看不到，
        // 于是可能抽到同一端口号（kcon_winner.port_no 并无唯一约束兜底）。
        // 这里改为在锁内用事务模板显式开启/提交，使锁的持有期完全覆盖事务生命周期。
        synchronized (drawLock)
        {
            return transactionTemplate.execute(status -> doDraw(sessionCode, prizeId, operator));
        }
    }

    /**
     * 抽奖主体。由 {@link #draw} 在抽奖锁内通过事务模板调用，不要直接暴露。
     */
    /**
     * 抽奖主体。由 {@link #draw} 在抽奖锁内通过事务模板调用，不要直接暴露。
     *
     * 一次调用把该档位剩余名额全部抽出（舞台上的「一键抽完一档」）：
     * 例如三等奖还剩 3 个名额，就一次抽出 3 个人，大屏再逐个滚动揭晓。
     * 整批在同一事务里完成——要么全成、要么全不成，不会留下「抽了一半」的档位。
     */
    private Map<String, Object> doDraw(String sessionCode, Long prizeId, String operator)
    {
        if (sessionCode == null || sessionCode.trim().isEmpty())
        {
            // 未指定场次时取当前激活场次（与大屏同一口径），不能写死 AM：
            // 否则下午场已激活、调用方漏传 sessionCode 时会抽到上午场的档位。
            KconSession cur = resolveCurrentSession(sessionMapper.selectSessionList());
            sessionCode = cur != null ? cur.getCode() : "AM";
        }

        // 1. 取奖品档位
        KconPrize prize;
        if (prizeId != null)
        {
            prize = prizeMapper.selectPrizeById(prizeId);
            if (prize == null)
            {
                throw new ServiceException("奖品档位不存在");
            }
        }
        else
        {
            prize = prizeMapper.selectCurrentTier(sessionCode);
        }
        if (prize == null)
        {
            throw new ServiceException("该场次奖项已抽完", 4002);
        }

        int quantity = prize.getQuantity() == null ? 0 : prize.getQuantity();
        int drawn = prize.getDrawnCount() == null ? 0 : prize.getDrawnCount();
        int remain = quantity - drawn;
        if (remain <= 0)
        {
            throw new ServiceException("该奖项已抽满", 4002);
        }

        // 2. 计算抽奖池（此刻）
        List<Integer> pool = calcPool(sessionCode);
        if (pool.isEmpty())
        {
            throw new ServiceException("当前无可抽奖号，请先让观众签到", 4001);
        }

        // 一次抽几个 = min(档位剩余名额, 池中人数)。
        // 池里人不够时不报错，只抽得出来的并回报 shortfall：
        // 现场可以再让人签到后补抽（作废会把名额退回），直接报错会卡住流程。
        int drawCount = Math.min(remain, pool.size());
        // 整批共用一个中奖时间：既让同一批可被识别（大屏据此还原整批），
        // 也避免同一事务里多条 INSERT 各自的 now() 跨秒导致批次对不上。
        Date batchTime = new Date();

        List<Map<String, Object>> winnerViews = new ArrayList<>();
        for (int i = 0; i < drawCount; i++)
        {
            // 3. 从池中随机取一个，取走后从本地池移除，避免同一批里重复取到同一个号
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size());
            Integer portNo = pool.remove(idx);

            // 4. 二次校验：该号是否已有有效中奖记录（防并发/防重复）
            KconWinner exist = winnerMapper.selectActiveByPortNo(portNo);
            if (exist != null)
            {
                throw new ServiceException("抽号冲突，请再抽一次", 409);
            }

            // 5. 取该端口号持有人
            KconCheckin checkin = checkinMapper.selectByPortNo(portNo);
            if (checkin == null)
            {
                throw new ServiceException("数据异常：端口号无签到记录，请重试", 409);
            }
            KconAttendee attendee = attendeeMapper.selectAttendeeById(checkin.getAttendeeId());

            // 6. 占用该档位一个名额（条件更新，防超额）
            int ok = prizeMapper.incrementDrawnCount(prize.getId());
            if (ok != 1)
            {
                throw new ServiceException("该奖项已抽满", 4002);
            }

            // 7. 写中奖记录
            KconWinner winner = new KconWinner();
            winner.setPortNo(portNo);
            winner.setAttendeeId(checkin.getAttendeeId());
            winner.setName(attendee != null ? attendee.getName() : "");
            winner.setCompany(attendee != null ? attendee.getCompany() : null);
            winner.setPosition(attendee != null ? attendee.getPosition() : null);
            winner.setPhone(checkin.getPhone());
            // 需求 FR-23：邮箱随中奖记录一起冗余，兑奖时用于二次核对身份
            winner.setEmail(attendee != null ? attendee.getEmail() : null);
            winner.setSessionCode(sessionCode);
            winner.setPrizeId(prize.getId());
            winner.setTierName(prize.getTierName());
            winner.setStatus(KconWinner.STATUS_WON);
            winner.setWinTime(batchTime);
            winner.setOperator(operator);
            winnerMapper.insertWinner(winner);

            log.info("抽奖中奖 session={} portNo={} name={} tier={}",
                    sessionCode, portNo, winner.getName(), prize.getTierName());

            Map<String, Object> v = new LinkedHashMap<>();
            v.put("winnerId", winner.getId());
            v.put("portNo", portNo);
            v.put("name", winner.getName());
            v.put("company", winner.getCompany());
            v.put("position", winner.getPosition());
            v.put("tierName", prize.getTierName());
            winnerViews.add(v);
        }

        log.info("本档一次抽出 {} 人（档位 {}，剩余名额 {}，抽后池中 {}）",
                winnerViews.size(), prize.getTierName(), remain, pool.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("winners", winnerViews);
        data.put("count", winnerViews.size());
        data.put("tierName", prize.getTierName());
        data.put("quantity", quantity);
        // 池里人不够时少抽了几个人：大屏据此提示「还需 N 人签到才能抽满」
        data.put("shortfall", Math.max(0, remain - winnerViews.size()));
        data.put("batchKey", prize.getId() + "@" + format(batchTime) + "@" + winnerViews.size());
        data.put("poolCountAfter", pool.size());
        // 带上最新状态：管理端抽奖控制台可以直接用它乐观更新，不必等下一轮 5 秒轮询。
        // （旧版这里没有 status，管理端 applyStatus 拿到 undefined 等于什么都没做，
        //   表现就是「点了抽奖要等几秒数字才动」。）
        data.put("status", screenStatus());
        return data;
    }

    /** 时间格式化（null 安全），与前端展示格式保持一致 */
    private String format(Date d)
    {
        if (d == null)
        {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidWinner(Long winnerId, String operator)
    {
        KconWinner winner = winnerMapper.selectWinnerById(winnerId);
        if (winner == null)
        {
            throw new ServiceException("中奖记录不存在");
        }
        if (winner.getStatus() != null && winner.getStatus() == KconWinner.STATUS_VOID)
        {
            throw new ServiceException("该记录已作废");
        }
        if (winner.getStatus() != null && winner.getStatus() == KconWinner.STATUS_RECEIVED)
        {
            throw new ServiceException("该奖品已领取，不能作废");
        }

        KconWinner upd = new KconWinner();
        upd.setId(winnerId);
        upd.setStatus(KconWinner.STATUS_VOID);
        upd.setOperator(operator);
        int rows = winnerMapper.updateStatus(upd);
        if (rows != 1)
        {
            throw new ServiceException("作废失败，请刷新后重试");
        }

        // 回退档位已抽数
        if (winner.getPrizeId() != null)
        {
            prizeMapper.decrementDrawnCount(winner.getPrizeId());
        }

        // 号码自动回池：calcPool 基于 status != 2 过滤，作废后该号自然重新进池
        log.info("作废中奖记录 winnerId={} portNo={}", winnerId, winner.getPortNo());
    }

    @Override
    public int voidLastBatch(String sessionCode, String operator)
    {
        // 与 draw() 同样的「锁覆盖事务」模式：整批要么全撤、要么全不撤，
        // 且锁释放前事务已提交，避免并发抽奖读到不一致的档位已抽数。
        synchronized (drawLock)
        {
            return transactionTemplate.execute(status -> doVoidLastBatch(sessionCode, operator));
        }
    }

    /**
     * 撤回最近一档主体。由 {@link #voidLastBatch} 在抽奖锁内通过事务模板调用。
     */
    private int doVoidLastBatch(String sessionCode, String operator)
    {
        // 1. 定位该场次最后一次抽出的「锚点」记录
        KconWinner last = winnerMapper.selectLatestValidWinnerBySession(sessionCode);
        if (last == null || last.getPrizeId() == null || last.getWinTime() == null)
        {
            return 0;
        }

        // 2. 还原整批：同一档位 + 同一中奖时间就是这一次点击抽出的全部人
        List<KconWinner> batch = winnerMapper.selectBatch(last.getPrizeId(), format(last.getWinTime()));
        if (batch.isEmpty())
        {
            return 0;
        }

        // 3. 逐条作废。已领取的不能撤（奖品已发出去），遇到就中断并明确报错，
        //    让操作员知道这一档有人已领奖、不能整档撤回，避免静默漏撤。
        int voided = 0;
        for (KconWinner w : batch)
        {
            if (w.getStatus() != null && w.getStatus() == KconWinner.STATUS_RECEIVED)
            {
                throw new ServiceException(
                        "该档已有奖品被领取（端口号 " + w.getPortNo() + "），不能整档撤回");
            }
            if (w.getStatus() != null && w.getStatus() == KconWinner.STATUS_VOID)
            {
                // 已是作废态（理论上 selectBatch 已过滤，防御性跳过）
                continue;
            }
            KconWinner upd = new KconWinner();
            upd.setId(w.getId());
            upd.setStatus(KconWinner.STATUS_VOID);
            upd.setOperator(operator);
            int rows = winnerMapper.updateStatus(upd);
            if (rows != 1)
            {
                throw new ServiceException("撤回失败，请刷新后重试");
            }
            if (w.getPrizeId() != null)
            {
                prizeMapper.decrementDrawnCount(w.getPrizeId());
            }
            voided++;
        }

        log.info("撤回最近一档 sessionCode={} tierName={} 作废 {} 人",
                sessionCode, last.getTierName(), voided);
        return voided;
    }

    @Override
    public void receive(Long winnerId, String operator)
    {
        KconWinner winner = winnerMapper.selectWinnerById(winnerId);
        if (winner == null)
        {
            throw new ServiceException("中奖记录不存在");
        }
        if (winner.getStatus() != null && winner.getStatus() == KconWinner.STATUS_RECEIVED)
        {
            throw new ServiceException("该奖品已领取");
        }
        if (winner.getStatus() != null && winner.getStatus() == KconWinner.STATUS_VOID)
        {
            throw new ServiceException("该记录已作废，不能领取");
        }
        KconWinner upd = new KconWinner();
        upd.setId(winnerId);
        upd.setStatus(KconWinner.STATUS_RECEIVED);
        upd.setOperator(operator);
        int rows = winnerMapper.updateStatus(upd);
        if (rows != 1)
        {
            throw new ServiceException("状态已变更，请刷新后重试");
        }
        log.info("标记已领取 winnerId={}", winnerId);
    }

    // ---------------- 奖品档位 ----------------

    @Override
    public List<KconPrize> selectPrizeList(KconPrize query)
    {
        return prizeMapper.selectPrizeList(query);
    }

    @Override
    public KconPrize selectPrizeById(Long id)
    {
        return prizeMapper.selectPrizeById(id);
    }

    @Override
    public int insertPrize(KconPrize prize)
    {
        validatePrize(prize, null);
        return prizeMapper.insertPrize(prize);
    }

    @Override
    public int updatePrize(KconPrize prize)
    {
        KconPrize exist = prizeMapper.selectPrizeById(prize.getId());
        if (exist == null)
        {
            throw new ServiceException("奖品档位不存在");
        }
        if (prize.getQuantity() != null && exist.getDrawnCount() != null
                && prize.getQuantity() < exist.getDrawnCount())
        {
            throw new ServiceException("数量不能小于已抽取数量 " + exist.getDrawnCount());
        }
        // 已抽过的档位不允许改场次：否则 drawn_count 会跨场次串味，
        // 出现「上午场档位显示已抽满、下午场却统计不到」的对不上账
        if (exist.getDrawnCount() != null && exist.getDrawnCount() > 0
                && prize.getSessionCode() != null
                && !prize.getSessionCode().equals(exist.getSessionCode()))
        {
            throw new ServiceException("该档位已抽取 " + exist.getDrawnCount() + " 次，不能修改所属场次");
        }
        validatePrize(prize, prize.getId());
        return prizeMapper.updatePrize(prize);
    }

    /**
     * 奖品档位新增/修改的公共校验。
     *
     * 场次必须真实存在：否则会产生「永远不会被抽到」的幽灵档位
     * （selectCurrentTier 只按当前场次取档）。
     * 同场次奖项名不允许重复：重复档位会让大屏进度条出现永远抽不满的项。
     */
    private void validatePrize(KconPrize prize, Long excludeId)
    {
        if (prize == null || StringUtils.isEmpty(prize.getSessionCode()))
        {
            throw new ServiceException("场次不能为空");
        }
        if (sessionMapper.selectByCode(prize.getSessionCode()) == null)
        {
            throw new ServiceException("场次不存在：" + prize.getSessionCode());
        }
        if (StringUtils.isNotEmpty(prize.getTierName())
                && prizeMapper.countBySessionAndTier(
                        prize.getSessionCode(), prize.getTierName(), excludeId) > 0)
        {
            throw new ServiceException("该场次下已存在同名奖项：" + prize.getTierName());
        }
    }

    @Override
    public int deletePrizeById(Long id)
    {
        KconPrize exist = prizeMapper.selectPrizeById(id);
        if (exist == null)
        {
            throw new ServiceException("奖品档位不存在");
        }
        if (exist.getDrawnCount() != null && exist.getDrawnCount() > 0)
        {
            throw new ServiceException("该档位已抽取 " + exist.getDrawnCount() + " 次，不能删除");
        }
        return prizeMapper.deletePrizeById(id);
    }

    // ---------------- 中奖记录 ----------------

    @Override
    public List<KconWinner> selectWinnerList(KconWinner query)
    {
        List<KconWinner> list = winnerMapper.selectWinnerList(query);
        for (KconWinner w : list)
        {
            w.setPhoneMask(IKconCheckinService.maskPhone(w.getPhone()));
            w.setStatusText(statusText(w.getStatus()));
        }
        return list;
    }

    @Override
    public KconWinner selectWinnerById(Long id)
    {
        KconWinner w = winnerMapper.selectWinnerById(id);
        if (w != null)
        {
            w.setPhoneMask(IKconCheckinService.maskPhone(w.getPhone()));
            w.setStatusText(statusText(w.getStatus()));
        }
        return w;
    }

    private String statusText(Integer status)
    {
        if (status == null) return "";
        switch (status)
        {
            case KconWinner.STATUS_WON: return "已中奖";
            case KconWinner.STATUS_RECEIVED: return "已领取";
            case KconWinner.STATUS_VOID: return "已作废";
            default: return "";
        }
    }

    /**
     * 每个场次的完整档位配置 + 该档中奖人，供控制台「奖项进度与中奖记录」按场次横栏展示。
     *
     * 关键点：**没抽过的档位也要返回**（winners 为空数组）。
     * 控制台那张表要回答「没抽的有多少 / 抽了多少 / 抽中的是谁」，
     * 若只返回有中奖记录的档位，未开始的档位就会从表里消失，操作员反而看不到剩余量。
     * 因此这里以 selectPrizeList（该场次全部档位，draw_order 升序）为骨架，
     * 每个档位再补一次中奖人查询。
     *
     * 查询次数 = 场次数 × 档位数（现场 2 × 3 = 6 条复合索引小查询），
     * 控制台 5 秒轮询一次，量级可忽略；不做大 join 是为了保持 mapper 简单可测。
     */
    @Override
    public List<Map<String, Object>> winnerGroups()
    {
        List<KconSession> sessions = sessionMapper.selectSessionList();
        List<Map<String, Object>> groups = new ArrayList<>();
        for (KconSession s : sessions)
        {
            KconPrize tierQuery = new KconPrize();
            tierQuery.setSessionCode(s.getCode());
            List<KconPrize> prizeList = prizeMapper.selectPrizeList(tierQuery);
            if (prizeList == null)
            {
                prizeList = new ArrayList<>();
            }
            // 该场次没配档位时也要返回（tiers 为空数组），不能 continue 跳过：
            // 跳过后前端 sessionPanels 里就没有这个场次，控制台会把「配了场次但没配奖项」
            // 误判成「一个场次都没配」，提示语和 tab 都对不上，操作员找不到问题在哪

            List<Map<String, Object>> tierViews = new ArrayList<>();
            for (KconPrize p : prizeList)
            {
                List<KconWinner> ws = winnerMapper.selectValidByPrize(p.getId());
                List<Map<String, Object>> winnerViews = new ArrayList<>();
                if (ws != null)
                {
                    for (KconWinner w : ws)
                    {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("winnerId", w.getId());
                        m.put("portNo", w.getPortNo());
                        m.put("name", w.getName());
                        m.put("company", w.getCompany());
                        m.put("statusText", statusText(w.getStatus()));
                        m.put("winTime", format(w.getWinTime()));
                        winnerViews.add(m);
                    }
                }
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("tierId", p.getId());
                t.put("tierName", p.getTierName());
                t.put("quantity", p.getQuantity());
                t.put("drawnCount", p.getDrawnCount());
                t.put("winners", winnerViews);
                tierViews.add(t);
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("sessionCode", s.getCode());
            g.put("sessionName", s.getName());
            g.put("tiers", tierViews);
            groups.add(g);
        }
        return groups;
    }
}
