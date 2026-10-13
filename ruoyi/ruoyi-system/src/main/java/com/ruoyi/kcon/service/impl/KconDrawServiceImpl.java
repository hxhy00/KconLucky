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
import org.springframework.transaction.annotation.Transactional;

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
        KconSession current = null;
        for (KconSession s : sessions)
        {
            if (s.getStatus() != null && s.getStatus() == 1)
            {
                current = s;
                break;
            }
        }
        if (current == null && !sessions.isEmpty())
        {
            current = sessions.get(0);
        }

        String sessionCode = current != null ? current.getCode() : "AM";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionCode", sessionCode);
        data.put("sessionName", current != null ? current.getName() : "");
        data.put("checkedInToday", checkinMapper.countToday());
        data.put("poolCount", calcPool(sessionCode).size());
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
        data.put("sponsors", sponsors());
        return data;
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
        // 1. 已签到的端口号
        Set<Integer> signedPorts = new HashSet<>();
        for (KconCheckin c : checkinMapper.selectAllPorts())
        {
            signedPorts.add(c.getPortNo());
        }

        // 2. 已中奖端口号（全场，含上午场）
        Set<Integer> wonPorts = new HashSet<>(winnerMapper.selectAllWonPorts());

        // 下午场额外剔除上午场已中奖（其实全场已中奖已包含，这里保持语义清晰）
        List<Integer> pool = new ArrayList<>();
        for (Integer p : signedPorts)
        {
            if (!wonPorts.contains(p))
            {
                pool.add(p);
            }
        }
        Collections.sort(pool);
        return pool;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> draw(String sessionCode, Long prizeId, String operator)
    {
        synchronized (drawLock)
        {
            if (sessionCode == null || sessionCode.trim().isEmpty())
            {
                KconSession cur = sessionMapper.selectByCode("AM");
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
            if (prize.getDrawnCount() != null && prize.getDrawnCount() >= prize.getQuantity())
            {
                throw new ServiceException("该奖项已抽满", 4002);
            }

            // 2. 计算抽奖池（此刻）
            List<Integer> pool = calcPool(sessionCode);
            if (pool.isEmpty())
            {
                throw new ServiceException("当前无可抽奖号，请先让观众签到", 4001);
            }

            // 3. 随机取一个（不放回由「已中奖排除」保证）
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size());
            Integer portNo = pool.get(idx);

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
            winner.setOperator(operator);
            winnerMapper.insertWinner(winner);

            log.info("抽奖中奖 session={} portNo={} name={} tier={}",
                    sessionCode, portNo, winner.getName(), prize.getTierName());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("winnerId", winner.getId());
            data.put("portNo", portNo);
            data.put("name", winner.getName());
            data.put("company", winner.getCompany());
            data.put("position", winner.getPosition());
            data.put("tierName", prize.getTierName());
            data.put("winTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(winner.getWinTime() != null ? winner.getWinTime() : new java.util.Date()));
            data.put("poolCountAfter", pool.size() - 1);
            return data;
        }
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
        return prizeMapper.updatePrize(prize);
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
}
