package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 统一签到表导出对象（供签到台打印后手工打钩）
 *
 * 需求 FR-25.1：姓名 / 手机号 / 渠道标识 / 票种 / 打钩列，按姓名排序。
 *
 * 为什么不复用 KconAttendee：
 *   名单实体带公司、邮箱、参加次数、职务、端口号等 9 列，与签到台无关，
 *   打印宽度会被撑开，而且缺了最关键的「打钩列」。
 *
 * @author kcon
 */
public class KconSignSheetVo
{
    @Excel(name = "姓名")
    private String name;

    @Excel(name = "手机号")
    private String phone;

    @Excel(name = "渠道标识")
    private String channel;

    @Excel(name = "票种")
    private String ticketType;

    /** 打钩列：留空给签到台人工打钩，导出时全为空 */
    @Excel(name = "签到确认")
    private String signed;

    public KconSignSheetVo()
    {
    }

    public KconSignSheetVo(String name, String phone, String channel, String ticketType)
    {
        this.name = name;
        this.phone = phone;
        this.channel = channel;
        this.ticketType = ticketType;
        this.signed = "";
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getTicketType()
    {
        return ticketType;
    }

    public void setTicketType(String ticketType)
    {
        this.ticketType = ticketType;
    }

    public String getSigned()
    {
        return signed;
    }

    public void setSigned(String signed)
    {
        this.signed = signed;
    }
}