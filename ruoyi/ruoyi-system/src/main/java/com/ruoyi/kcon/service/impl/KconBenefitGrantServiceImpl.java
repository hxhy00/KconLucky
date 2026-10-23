package com.ruoyi.kcon.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconBenefitGrantExportVo;
import com.ruoyi.kcon.domain.KconCheckin;
import com.ruoyi.kcon.domain.KconPrizeGrantExportVo;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconBenefitGrantMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.service.IKconBenefitGrantService;
import com.ruoyi.kcon.service.IKconCheckinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 参会人发放记录（发放日志）实现
 *
 * @author kcon
 */
@Service
public class KconBenefitGrantServiceImpl implements IKconBenefitGrantService
{
    private static final Logger log = LoggerFactory.getLogger(KconBenefitGrantServiceImpl.class);

    /** 导出条数上限：发放记录随现场持续增长，与核验日志导出同一口径 */
    private static final int EXPORT_MAX = 10000;

    @Autowired
    private KconBenefitGrantMapper grantMapper;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Autowired
    private KconCheckinMapper checkinMapper;

    @Override
    public void grantBenefit(Long attendeeId, String operator)
    {
        KconAttendee attendee = attendeeMapper.selectAttendeeById(attendeeId);
        if (attendee == null)
        {
            throw new ServiceException("参会人不存在");
        }

        // 必须已签到：未签到先引导去签到台，避免凭名单冒领
        KconCheckin checkin = checkinMapper.selectByAttendeeId(attendeeId);
        if (checkin == null)
        {
            throw new ServiceException("该观众尚未签到，请先完成签到再发放权益");
        }

        // 无权益内容的人没有可发放的东西（映射未配权益/手工清空过）
        if (attendee.getTicketType() == null || attendee.getTicketType().isBlank())
        {
            throw new ServiceException("该参会人没有配置权益类型，无法发放");
        }

        KconBenefitGrant grant = new KconBenefitGrant();
        grant.setGrantType(KconBenefitGrant.TYPE_BENEFIT);
        grant.setAttendeeId(attendeeId);
        grant.setPortNo(checkin.getPortNo());
        grant.setName(attendee.getName());
        grant.setPhone(IKconCheckinService.maskPhone(attendee.getPhone()));
        grant.setItem(attendee.getTicketType());
        grant.setOperator(operator);

        try
        {
            grantMapper.insertGrant(grant);
        }
        catch (DuplicateKeyException e)
        {
            // uk_attendee_type 唯一键兜底：并发/重复点击到这里也会被 DB 拦下
            throw new ServiceException("该参会人的权益已发放过，请勿重复发放");
        }
        log.info("权益发放成功 attendeeId={} portNo={} operator={}", attendeeId, checkin.getPortNo(), operator);
    }

    @Override
    public void recordPrizeGrant(Integer portNo, KconWinner winner, String operator)
    {
        // 只留痕，不影响发放主流程：主流程 kcon_winner.status 0→1 已落库，
        // 这里写失败只打日志，不回滚发放。
        try
        {
            KconBenefitGrant grant = new KconBenefitGrant();
            grant.setGrantType(KconBenefitGrant.TYPE_PRIZE);
            grant.setAttendeeId(winner.getAttendeeId());
            grant.setPortNo(portNo);
            grant.setName(winner.getName());
            grant.setPhone(IKconCheckinService.maskPhone(winner.getPhone()));
            grant.setItem(winner.getTierName());
            grant.setSessionCode(winner.getSessionCode());
            grant.setTierName(winner.getTierName());
            grant.setOperator(operator);
            grantMapper.insertGrant(grant);
        }
        catch (Exception e)
        {
            log.warn("写奖品发放日志失败 portNo={} operator={}", portNo, operator, e);
        }
    }

    @Override
    public List<KconBenefitGrant> selectGrantList(KconBenefitGrant query)
    {
        List<KconBenefitGrant> list = grantMapper.selectGrantList(query);
        for (KconBenefitGrant item : list)
        {
            fillDisplay(item);
        }
        return list;
    }

    @Override
    public List<KconBenefitGrantExportVo> selectBenefitGrantExportList(KconBenefitGrant query)
    {
        List<KconBenefitGrantExportVo> rows = new java.util.ArrayList<>();
        for (KconBenefitGrant item : loadForExport(query))
        {
            rows.add(new KconBenefitGrantExportVo(
                    item.getPortNo(),
                    item.getName(),
                    item.getPhoneMask(),
                    item.getItem(),
                    item.getOperator(),
                    formatTime(item.getCreateTime())));
        }
        return rows;
    }

    @Override
    public List<KconPrizeGrantExportVo> selectPrizeGrantExportList(KconBenefitGrant query)
    {
        List<KconPrizeGrantExportVo> rows = new java.util.ArrayList<>();
        for (KconBenefitGrant item : loadForExport(query))
        {
            rows.add(new KconPrizeGrantExportVo(
                    item.getPortNo(),
                    item.getName(),
                    item.getPhoneMask(),
                    item.getSessionCode(),
                    item.getTierName(),
                    item.getOperator(),
                    formatTime(item.getCreateTime())));
        }
        return rows;
    }

    /**
     * 导出取数：上限 EXPORT_MAX，超限导最近 N 条；逐条做手机号兜底掩码。
     */
    private List<KconBenefitGrant> loadForExport(KconBenefitGrant query)
    {
        List<KconBenefitGrant> list = grantMapper.selectGrantList(query);
        if (list.size() > EXPORT_MAX)
        {
            list = list.subList(0, EXPORT_MAX);
            log.warn("发放日志导出超过上限，仅导出最近 {} 条", EXPORT_MAX);
        }
        for (KconBenefitGrant item : list)
        {
            fillDisplay(item);
        }
        return list;
    }

    /**
     * 写入时已掩码，这里再掩一次是防御性兜底（与核验日志 fillDisplay 同理）：
     * maskPhone 对 138****0001 这类已掩码值幂等，明文落库的老数据也会被兜住。
     */
    private void fillDisplay(KconBenefitGrant item)
    {
        item.setPhoneMask(IKconCheckinService.maskPhone(item.getPhone()));
        item.setPhone(null);
    }

    /** 时间格式化，null 安全 */
    private String formatTime(java.util.Date time)
    {
        if (time == null)
        {
            return "";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }
}
