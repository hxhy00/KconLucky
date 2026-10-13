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
}
