package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 中奖记录对象 kcon_winner
 *
 * status: 0=已中奖 1=已领取 2=已作废
 *
 * @author kcon
 */
public class KconWinner extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态常量 */
    public static final int STATUS_WON = 0;
    public static final int STATUS_RECEIVED = 1;
    public static final int STATUS_VOID = 2;

    private Long id;

    /** 中奖端口号 */
    @Excel(name = "端口号")
    private Integer portNo;

    /** 参会人ID */
    private Long attendeeId;

    /** 姓名（冗余） */
    @Excel(name = "姓名")
    private String name;

    /** 公司（冗余） */
    private String company;

    /** 职务/年级（冗余） */
    private String position;

    /** 手机号 */
    private String phone;

    /** 邮箱（冗余自名单，兑奖时用于二次核对身份，需求 FR-23） */
    @Excel(name = "邮箱")
    private String email;

    /** 场次 AM/PM */
    @Excel(name = "场次")
    private String sessionCode;

    /** 奖品档位ID */
    private Long prizeId;

    /** 奖项名（冗余） */
    @Excel(name = "奖项")
    private String tierName;

    /** 0=已中奖 1=已领取 2=已作废 */
    private Integer status;

    /** 中奖时间 */
    @Excel(name = "中奖时间")
    private Date winTime;

    /** 领取时间 */
    private Date receiveTime;

    /** 作废时间 */
    private Date voidTime;

    /** 操作人 */
    private String operator;

    /** 手机号掩码（展示用） */
    @Excel(name = "手机号")
    private String phoneMask;

    /** 状态文本（展示用） */
    private String statusText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public Long getAttendeeId() { return attendeeId; }
    public void setAttendeeId(Long attendeeId) { this.attendeeId = attendeeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public Long getPrizeId() { return prizeId; }
    public void setPrizeId(Long prizeId) { this.prizeId = prizeId; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Date getWinTime() { return winTime; }
    public void setWinTime(Date winTime) { this.winTime = winTime; }

    public Date getReceiveTime() { return receiveTime; }
    public void setReceiveTime(Date receiveTime) { this.receiveTime = receiveTime; }

    public Date getVoidTime() { return voidTime; }
    public void setVoidTime(Date voidTime) { this.voidTime = voidTime; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
}
