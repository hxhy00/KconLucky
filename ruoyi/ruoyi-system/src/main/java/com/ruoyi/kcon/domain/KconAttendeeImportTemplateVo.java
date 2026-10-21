package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 参会名单导入模板（6 列，必填在前、选填在后）
 *
 * 列顺序固定为：
 *   姓名 / 手机号 / 渠道来源 / 公司 / 职位 / 邮箱
 *
 * 前 3 列为必填（姓名 / 手机号 / 渠道来源），公司 / 职位 / 邮箱为选填。
 *
 * 不含「权益类型」「参与抽奖」两列：这两项由渠道来源唯一决定，
 * 映射表 kcon_channel_mapping 是唯一事实源。模板里再留这两列，
 * 只会让人多填一遍，且填了也不生效（映射优先），徒增困惑。
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

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
