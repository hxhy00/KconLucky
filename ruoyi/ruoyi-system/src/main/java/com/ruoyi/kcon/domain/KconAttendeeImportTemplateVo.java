package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 参会名单导入模板（8 列，必填在前、选填在后）
 *
 * 列顺序固定为：
 *   姓名 / 手机号 / 渠道来源 / 权益类型 / 参与抽奖 / 公司 / 职位 / 邮箱
 *
 * 前 5 列为必填，后 3 列为选填。
 * 导入解析仍按表头名匹配，见 KconAttendeeServiceImpl.HEADER_ALIAS。
 *
 * @author kcon
 */
public class KconAttendeeImportTemplateVo
{
    @Excel(name = "姓名")
    private String name;

    @Excel(name = "手机号")
    private String phone;

    @Excel(name = "渠道来源")
    private String channel;

    @Excel(name = "权益类型")
    private String ticketType;

    @Excel(name = "参与抽奖")
    private String drawEligible;

    @Excel(name = "公司")
    private String company;

    @Excel(name = "职位")
    private String position;

    @Excel(name = "邮箱")
    private String email;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public String getDrawEligible() { return drawEligible; }
    public void setDrawEligible(String drawEligible) { this.drawEligible = drawEligible; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
