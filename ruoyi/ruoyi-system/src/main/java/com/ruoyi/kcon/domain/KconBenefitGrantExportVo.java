package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 权益发放日志导出行（kcon_benefit_grant type=BENEFIT → Excel）
 *
 * 与「日志 → 权益发放」页面同列口径：端口号 / 姓名 / 手机号（掩码）/ 权益类型 / 操作人 / 发放时间。
 * 手机号统一由 Service 层传入掩码，明文不出后端。
 * 奖品发放导出另见 {@link KconPrizeGrantExportVo}。
 *
 * @author kcon
 */
public class KconBenefitGrantExportVo
{
    @Excel(name = "端口号")
    private Integer portNo;

    @Excel(name = "姓名")
    private String name;

    @Excel(name = "手机号")
    private String phoneMask;

    @Excel(name = "权益类型")
    private String item;

    @Excel(name = "操作人")
    private String operator;

    @Excel(name = "发放时间")
    private String createTime;

    public KconBenefitGrantExportVo() {}

    public KconBenefitGrantExportVo(Integer portNo, String name, String phoneMask,
                                    String item, String operator, String createTime)
    {
        this.portNo = portNo;
        this.name = name;
        this.phoneMask = phoneMask;
        this.item = item;
        this.operator = operator;
        this.createTime = createTime;
    }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
