package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 奖品发放日志导出行（kcon_benefit_grant type=PRIZE → Excel）
 *
 * 与「日志 → 奖品发放」页面同列口径：端口号 / 姓名 / 手机号（掩码）/ 场次 / 奖项 / 操作人 / 发放时间。
 * 手机号统一由 Service 层传入掩码，明文不出后端。
 *
 * @author kcon
 */
public class KconPrizeGrantExportVo
{
    @Excel(name = "端口号")
    private Integer portNo;

    @Excel(name = "姓名")
    private String name;

    @Excel(name = "手机号")
    private String phoneMask;

    @Excel(name = "场次")
    private String sessionText;

    @Excel(name = "奖项")
    private String tierName;

    @Excel(name = "操作人")
    private String operator;

    @Excel(name = "发放时间")
    private String createTime;

    public KconPrizeGrantExportVo() {}

    public KconPrizeGrantExportVo(Integer portNo, String name, String phoneMask,
                                  String sessionText, String tierName, String operator, String createTime)
    {
        this.portNo = portNo;
        this.name = name;
        this.phoneMask = phoneMask;
        this.sessionText = sessionText;
        this.tierName = tierName;
        this.operator = operator;
        this.createTime = createTime;
    }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneMask() { return phoneMask; }
    public void setPhoneMask(String phoneMask) { this.phoneMask = phoneMask; }

    public String getSessionText() { return sessionText; }
    public void setSessionText(String sessionText) { this.sessionText = sessionText; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
