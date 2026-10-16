package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 参会人名单对象 kcon_attendee
 *
 * 导入必填 5 列：姓名 / 手机号 / 渠道标识 / 票种 / 是否参与抽奖
 * 选填 4 列：公司 / 邮箱 / 第几次参加 KCon / 职务·年级
 *
 * @author kcon
 */
public class KconAttendee extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 姓名 */
    @Excel(name = "姓名")
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String name;

    /** 手机号（业务主键） */
    @Excel(name = "手机号")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 渠道标识：S/T/C/H/D/G/V */
    @Excel(name = "渠道标识")
    private String channel;

    /** 票种 */
    @Excel(name = "票种")
    private String ticketType;

    /**
     * 是否参与抽奖：1=是 0=否（spec 006 补充，2026-10-16 用户确认）
     *
     * 导入为第 5 必填列：这是票务侧的显式事实，不由票种/渠道推导；
     * 只有显式填「是」才具备基础资格，留空或无法识别一律按「否」入库
     * （fail-safe：漏填只会失去资格，不会把该被排除的人放错进池）。
     * 池计算时系统参数黑名单参数优先、实时生效，见 DrawEligibility。
     */
    @Excel(name = "是否参与抽奖", readConverterExp = "1=是,0=否")
    private Integer drawEligible;

    /** 公司（选填） */
    @Excel(name = "公司")
    @Size(max = 128, message = "公司名称长度不能超过 128")
    private String company;

    /** 邮箱（选填） */
    @Excel(name = "邮箱")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 第几次参加 KCon（选填） */
    @Excel(name = "第几次参加KCon")
    private Integer attendCount;

    /** 职务/年级（选填） */
    @Excel(name = "职务/年级")
    @Size(max = 64, message = "职务/年级长度不能超过 64")
    private String position;

    /** 手机号后四位（冗余） */
    private String phoneTail4;

    /** 导入批次号 */
    private String importBatch;

    // ---- 以下为查询/展示用非持久化字段 ----

    /** 端口号（联表带出） */
    @Excel(name = "端口号")
    private Integer portNo;

    /** 签到状态：0未签到 1已签到（联表带出） */
    private Integer signed;

    /** 签到时间（联表带出） */
    private String checkinTime;

    /** 手机号掩码（返回前端用，不入库） */
    private String phoneMask;

    /**
     * 乐观锁标记（不入库）：读取时的 ifnull(update_time, create_time) 文本。
     * 编辑提交时原样带回，服务端用它做条件更新——若期间已被他人改动，条件不成立、
     * 更新 0 行，于是明确拒绝，避免后提交的人静默覆盖前一个人的修改。
     * 用格式化文本而非 Date，是为了绕开 Date 序列化的时区与精度往返问题。
     */
    private String version;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public Integer getDrawEligible() { return drawEligible; }
    public void setDrawEligible(Integer drawEligible) { this.drawEligible = drawEligible; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAttendCount() { return attendCount; }
    public void setAttendCount(Integer attendCount) { this.attendCount = attendCount; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getPhoneTail4() { return phoneTail4; }
    public void setPhoneTail4(String phoneTail4) { this.phoneTail4 = phoneTail4; }

    public String getImportBatch() { return importBatch; }
    public void setImportBatch(String importBatch) { this.importBatch = importBatch; }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public Integer getSigned() { return signed; }
    public void setSigned(Integer signed) { this.signed = signed; }

    public String getCheckinTime() { return checkinTime; }
    public void setCheckinTime(String checkinTime) { this.checkinTime = checkinTime; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }
}
