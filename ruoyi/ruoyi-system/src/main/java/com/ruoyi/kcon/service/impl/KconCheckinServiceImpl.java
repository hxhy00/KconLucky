package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPortPool;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPortPoolMapper;
import com.ruoyi.kcon.service.IKconCheckinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到业务实现
 *
 * 端口号分配策略（不放回）：
 *   1. 从 kcon_port_pool 随机取一个 status=0 的号
 *   2. 用条件 UPDATE（where status=0）尝试占用，返回 1 才算成功
 *   3. 失败说明被别人抢先，重试（最多 N 次）
 *   4. 成功后写 kcon_checkin（phone 唯一索引兜底，防同号并发）
 *
 * @author kcon
 */
@Service
public class KconCheckinServiceImpl implements IKconCheckinService
{
    private static final Logger log = LoggerFactory.getLogger(KconCheckinServiceImpl.class);

    /** 端口号占用最大重试次数 */
    private static final int MAX_OCCUPY_RETRY = 20;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private KconPortPoolMapper portPoolMapper;

    @Override
    public Map<String, Object> queryAttendee(String keyword)
    {
        if (keyword == null || keyword.trim().isEmpty())
        {
            throw new ServiceException("请输入手机号或后四位");
        }
        String kw = keyword.trim();
        if (!kw.matches("\\d{4}|\\d{11}"))
        {
            throw new ServiceException("请输入 11 位手机号或 4 位尾号");
        }

        Map<String, Object> result = new LinkedHashMap<>();

        if (kw.length() == 11)
        {
            KconAttendee a = attendeeMapper.selectAttendeeByPhone(kw);
            if (a == null)
            {
                throw new ServiceException("查无此人，请联系现场工作人员");
            }
            result.put("matchType", "single");
            result.put("signed", a.getSigned() != null && a.getSigned() == 1);
            result.put("attendee", toPublicView(a));
            return result;
        }

        // 后四位：可能命中多人
        List<KconAttendee> list = attendeeMapper.selectByTail4(kw);
        if (list == null || list.isEmpty())
        {
            throw new ServiceException("查无此人，请联系现场工作人员");
        }
        if (list.size() == 1)
        {
            KconAttendee a = list.get(0);
            result.put("matchType", "single");
            result.put("signed", a.getSigned() != null && a.getSigned() == 1);
            result.put("attendee", toPublicView(a));
            return result;
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (KconAttendee a : list)
        {
            candidates.add(toPublicView(a));
        }
        result.put("matchType", "multiple");
        result.put("candidates", candidates);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitCheckin(Long attendeeId, String phone)
    {
        KconAttendee attendee = attendeeMapper.selectAttendeeById(attendeeId);
        if (attendee == null)
        {
            throw new ServiceException("查无此人，请联系现场工作人员");
        }

        // 公网版加固：submit 免登录，仅凭自增 attendeeId 就能替他人签到、耗尽端口池。
        // 要求提交完整 11 位手机号并与名单等值比对，把「猜 ID」和「枚举尾号」都挡在落库之前。
        verifyPhone(attendee, phone);

        // 已签到 → 返回原端口号，不变号
        KconCheckin exist = checkinMapper.selectByPhone(attendee.getPhone());
        if (exist != null)
        {
            return buildSuccess(attendee, exist.getPortNo(), exist.getCheckinTime(), true);
        }

        // 分配端口号
        int portNo = allocatePort(attendee.getPhone());

        Date now = new Date();
        KconCheckin checkin = new KconCheckin();
        checkin.setAttendeeId(attendee.getId());
        checkin.setPhone(attendee.getPhone());
        checkin.setPortNo(portNo);
        checkin.setCheckinDate(now);
        checkin.setCheckinTime(now);
        try
        {
            checkinMapper.insertCheckin(checkin);
        }
        catch (Exception e)
        {
            // 极少数并发场景：同一手机号同时提交，唯一索引拦截。
            // 事务会整体回滚（端口号占用一并归还），这里只能提示重试：
            // 在当前事务已标记 rollback-only 的情况下无法再读库返回已有记录，
            // 且调用方重试后必然命中上面的「已签到 → 返回原端口号」分支。
            log.warn("并发签到冲突，phone={}，提示调用方重试", attendee.getPhone());
            throw new ServiceException("签到处理中，请稍后重试");
        }

        log.info("签到成功 name={} portNo={}", attendee.getName(), portNo);
        return buildSuccess(attendee, portNo, now, false);
    }

    /**
     * 二次校验：提交的手机号必须与该参会人一致。
     *
     * 只接受完整 11 位。后四位不构成秘密（10^4 可枚举），若允许它作为提交凭证，
     * 等于把「查得到尾号就能替人签到」写进接口，和没有校验一样。
     * 查询入口仍保留后四位，是为了现场快速定位；确认签到这一步必须补全。
     */
    private void verifyPhone(KconAttendee attendee, String phone)
    {
        if (phone == null || phone.trim().isEmpty())
        {
            throw new ServiceException("请输入完整 11 位手机号");
        }
        String input = phone.trim();
        if (!input.matches("1[3-9]\\d{9}"))
        {
            throw new ServiceException("请输入完整 11 位手机号");
        }
        String registered = attendee.getPhone();
        if (registered == null || !registered.equals(input))
        {
            throw new ServiceException("手机号与参会人信息不匹配，请重新查询");
        }
    }

    /**
     * 分配一个未被占用的端口号（不放回）
     */
    private int allocatePort(String phone)
    {
        for (int i = 0; i < MAX_OCCUPY_RETRY; i++)
        {
            Integer candidate = portPoolMapper.selectRandomFreePort();
            if (candidate == null)
            {
                throw new ServiceException("端口号已耗尽，请联系技术人员");
            }
            KconPortPool param = new KconPortPool();
            param.setPortNo(candidate);
            param.setPhone(phone);
            int rows = portPoolMapper.occupyPort(param);
            if (rows == 1)
            {
                return candidate;
            }
            // 被抢先，重试
        }
        throw new ServiceException("现场签到繁忙，请重试");
    }

    private Map<String, Object> buildSuccess(KconAttendee attendee, Integer portNo, Date checkinTime, boolean repeat)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("signed", true);
        data.put("portNo", portNo);
        data.put("checkinTime", format(checkinTime));
        data.put("repeat", repeat);
        data.put("name", attendee.getName());

        // spec 006 FR-A4：非买票者照常签到、照常拿端口号，不拦截。
        // 刻意**不**在此返回抽奖资格：签到端是免登录接口，一旦回传该标记，
        // 任何人都能自查「我在不在池里」，等于把筛选口径对外开放。
        // 判定结果只留在服务端，供抽奖池与统计使用。
        data.put("tip", "您的抽奖端口号为 " + portNo + "，请您截屏保存，用于兑换抽奖礼品；请向工作人员出示此截图领取胸牌");
        return data;
    }

    private String format(Date d)
    {
        if (d == null)
        {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public Map<String, Object> lookup(String keyword)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (keyword == null || keyword.trim().isEmpty())
        {
            data.put("signed", false);
            // 与下方格式错误分支保持同一返回结构：前端只按 data.msg 提示，
            // 少了它就会掉进通用兜底文案，提示不到「要输什么」
            data.put("msg", "请输入完整 11 位手机号");
            return data;
        }
        String kw = keyword.trim();
        // 只接受完整 11 位手机号。
        // 这里曾允许「4 位尾号」，理由是截图丢了的人只需记住尾号就能当场找回端口号。
        // 但 /lookup 是免登录接口，尾号空间只有 10^4，枚举一遍成本极低，等于把
        // 「谁在场、尾号是多少、端口号是多少」全量敞开；而兑奖核验靠「本人知道完整手机号」
        // 来判定，尾号一旦可被枚举，这层判定就被从源头削弱了。
        // 因此这里砍掉尾号分支：核验与找回统一要求完整 11 位，攻击面从 10^4 收敛到 10^11。
        if (!kw.matches("\\d{11}"))
        {
            data.put("signed", false);
            data.put("msg", "请输入完整 11 位手机号（为防冒领，找回端口号不再支持只输后四位）");
            return data;
        }
        KconAttendee byPhone = attendeeMapper.selectAttendeeByPhone(kw);
        if (byPhone == null)
        {
            data.put("signed", false);
            data.put("msg", "查无此人");
            return data;
        }
        return fillLookup(data, byPhone);
    }

    /**
     * 唯一命中人选时补齐端口号信息。
     */
    private Map<String, Object> fillLookup(Map<String, Object> data, KconAttendee attendee)
    {
        KconCheckin checkin = checkinMapper.selectByPhone(attendee.getPhone());
        data.put("signed", checkin != null);
        if (checkin != null)
        {
            data.put("portNo", checkin.getPortNo());
            data.put("checkinTime", format(checkin.getCheckinTime()));
            data.put("name", attendee.getName());
            // 同 submit：不回传 drawEligible，避免免登录接口暴露抽奖筛选口径
        }
        else
        {
            data.put("msg", "该手机号尚未签到");
        }
        return data;
    }
}
