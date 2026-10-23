package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 参会人发放记录对象 kcon_benefit_grant（发放日志）
 *
 * 一张表记录「有人给参会人发放了什么」，按类型分两个管理页面：
 *   grant_type = BENEFIT 权益发放（名单页「发放」按钮，一人一条，DB 唯一键防重）
 *   grant_type = PRIZE    奖品发放（兑奖确认成功时写一条，仅留痕）
 * 两个页面各自固定 grantType 查询，互不串数据。
 *
 * 继承 BaseEntity 复用 params[beginTime]/params[endTime] 时间范围筛选；
 * 建表列 create_time 复用父类 createTime。
 *
 * @author kcon
 */
public class KconBenefitGrant extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 发放类型：权益发放 */
    public static final String TYPE_BENEFIT = "BENEFIT";

    /** 发放类型：奖品发放 */
    public static final String TYPE_PRIZE = "PRIZE";

    private Long id;

    /** BENEFIT=权益发放 PRIZE=奖品发放 */
    private String grantType;

    /** 参会人ID（奖品发放可能为空） */
    private Long attendeeId;

    /** 端口号（奖品发放必有；权益发放已签到时带出） */
    private Integer portNo;

    /** 姓名快照 */
    private String name;

    /** 手机号（写入时掩码存储，查询时不出后端） */
    private String phone;

    /** 发放内容快照：权益文本 / 奖项名 */
    private String item;

    /** 场次（奖品发放带出） */
    private String sessionCode;

    /** 奖项（奖品发放带出） */
    private String tierName;

    /** 操作人 */
    private String operator;

    // ---- 以下为查询/展示用非持久化字段 ----

    /** 手机号掩码（138****0001），前端展示与导出用 */
    private String phoneMask;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGrantType() { return grantType; }
    public void setGrantType(String grantType) { this.grantType = grantType; }

    public Long getAttendeeId() { return attendeeId; }
    public void setAttendeeId(Long attendeeId) { this.attendeeId = attendeeId; }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }
}
