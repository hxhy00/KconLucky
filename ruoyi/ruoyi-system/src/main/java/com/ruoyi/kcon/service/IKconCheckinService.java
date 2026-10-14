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
     * @param attendeeId 参会人ID
     */
    Map<String, Object> submitCheckin(Long attendeeId);

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
     */
    default Map<String, Object> toPublicView(KconAttendee a)
    {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("phoneMask", maskPhone(a.getPhone()));
        m.put("channel", a.getChannel());
        m.put("ticketType", a.getTicketType());
        m.put("company", a.getCompany());
        m.put("attendCount", a.getAttendCount());
        m.put("position", a.getPosition());
        return m;
    }
}
