package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 签到记录对象 kcon_checkin（一人一条，端口号不变）
 *
 * @author kcon
 */
public class KconCheckin extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 参会人ID */
    private Long attendeeId;

    /** 手机号 */
    private String phone;

    /** 分配到的端口号 1-65535 */
    @Excel(name = "端口号")
    private Integer portNo;

    /** 签到日期 */
    private Date checkinDate;

    /** 签到时间 */
    @Excel(name = "签到时间")
    private Date checkinTime;

    /** 渠道标识（联表带出，抽奖池过滤用，非本表列） */
    private String channel;

    /** 票种（联表带出，抽奖池过滤用，非本表列） */
    private String ticketType;

    /** 是否参与抽奖：1=是 0=否（联表带出，抽奖池过滤用，非本表列） */
    private Integer drawEligible;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAttendeeId() { return attendeeId; }
    public void setAttendeeId(Long attendeeId) { this.attendeeId = attendeeId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public Date getCheckinDate() { return checkinDate; }
    public void setCheckinDate(Date checkinDate) { this.checkinDate = checkinDate; }

    public Date getCheckinTime() { return checkinTime; }
    public void setCheckinTime(Date checkinTime) { this.checkinTime = checkinTime; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public Integer getDrawEligible() { return drawEligible; }
    public void setDrawEligible(Integer drawEligible) { this.drawEligible = drawEligible; }
}
