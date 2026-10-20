package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconAttendee;

import java.util.Map;

/**
 * 签到业务 Service
 *
 * @author kcon
 */
public interface IKconCheckinService
{
    /**
     * 签到端查询：按手机号全号或后四位匹配。
     * 返回结构对应接口文档 2.1：
     *   matchType = single   → attendee
     *   matchType = multiple → candidates
     *   无匹配 → 抛 ServiceException
     *
     * @param keyword 手机号全号或后四位
     */
    Map<String, Object> queryAttendee(String keyword);

    /**
     * 确认签到（重复签到返回原端口号，不变号）。
     * 返回结构对应接口文档 2.2。
     *
     * 公网版加固：submit 免登录且会真实落库，仅凭自增 attendeeId 可被批量代签，
     * 因此要求同时提交完整 11 位手机号，由服务端与名单等值比对。
     * 后四位只用于 query 定位，不作为提交凭证——尾号只有 10^4 种，不构成秘密。
     *
     * @param attendeeId 参会人ID
     * @param phone      完整 11 位手机号，用于二次校验
     */
    Map<String, Object> submitCheckin(Long attendeeId, String phone);

    /**
     * 按手机号回查签到状态与端口号（接口文档 2.3）
     */
    Map<String, Object> lookup(String keyword);

    /**
     * 手机号掩码：138****0001；短输入（如误存的尾号）返回 "***"，null/空串原样返回
     */
    static String maskPhone(String phone)
    {
        // 短输入（如核验日志里误存的 4 位尾号）原样返回会明文落库（kcon_verify_log.input_phone），
        // 一律固定掩码；null/空串仍原样返回，由上游判空保证。
        if (phone == null || phone.isEmpty())
        {
            return phone;
        }
        if (phone.length() < 7)
        {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 把实体转成签到端要展示的脱敏信息
     *
     * 刻意不返回 ticketType 与 channel：两者都是抽奖筛选规则的直接输入
     * （票种关键字黑名单 / 渠道黑名单），按 spec 006 的黑箱口径
     * （判据是报文里有没有这个字段，而不是前端渲不渲染）只留在服务端。
     */
    default Map<String, Object> toPublicView(KconAttendee a)
    {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("phoneMask", maskPhone(a.getPhone()));
        m.put("company", a.getCompany());
        m.put("attendCount", a.getAttendCount());
        m.put("position", a.getPosition());
        return m;
    }
}
