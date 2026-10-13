package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 签到明细导出对象（参会名单 + 端口号 + 签到情况 + 中奖与领取情况）
 *
 * ExcelUtil 依赖类上的 @Excel 注解来定义表头与列顺序，不能直接导出 Map
 * （Map 是接口，既取不到字段注解，父类也会 NPE），故用独立 VO 承载导出列。
 *
 * 对应 SQL：KconCheckinMapper.xml#selectExportList
 *
 * @author kcon
 */
public class KconCheckinExportVo
{
    @Excel(name = "姓名")
    private String name;

    @Excel(name = "手机号")
    private String phone;

    @Excel(name = "渠道标识")
    private String channel;

    @Excel(name = "票种")
    private String ticketType;

    @Excel(name = "公司")
    private String company;

    @Excel(name = "邮箱")
    private String email;

    @Excel(name = "第几次参加KCon")
    private String attendCount;

    @Excel(name = "职务/年级")
    private String position;

    @Excel(name = "端口号")
    private String portNo;

    @Excel(name = "签到时间")
    private String checkinTime;

    @Excel(name = "是否中奖")
    private String winnerFlag;

    @Excel(name = "奖项")
    private String tierName;

    @Excel(name = "是否已领取")
    private String receiveFlag;

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

    public String getCompany()
    {
        return company;
    }

    public void setCompany(String company)
    {
        this.company = company;
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getAttendCount()
    {
        return attendCount;
    }

    public void setAttendCount(String attendCount)
    {
        this.attendCount = attendCount;
    }

    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    public String getPortNo()
    {
        return portNo;
    }

    public void setPortNo(String portNo)
    {
        this.portNo = portNo;
    }

    public String getCheckinTime()
    {
        return checkinTime;
    }

    public void setCheckinTime(String checkinTime)
    {
        this.checkinTime = checkinTime;
    }

    public String getWinnerFlag()
    {
        return winnerFlag;
    }

    public void setWinnerFlag(String winnerFlag)
    {
        this.winnerFlag = winnerFlag;
    }

    public String getTierName()
    {
        return tierName;
    }

    public void setTierName(String tierName)
    {
        this.tierName = tierName;
    }

    public String getReceiveFlag()
    {
        return receiveFlag;
    }

    public void setReceiveFlag(String receiveFlag)
    {
        this.receiveFlag = receiveFlag;
    }
}