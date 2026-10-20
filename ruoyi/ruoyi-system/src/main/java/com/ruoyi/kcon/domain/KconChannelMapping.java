package com.ruoyi.kcon.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 渠道来源映射对象 kcon_channel_mapping
 *
 * 一条记录直接表达三个字段：渠道来源、权益类型、是否参与抽奖。
 *
 * @author kcon
 */
public class KconChannelMapping extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 渠道来源（唯一） */
    @NotBlank(message = "渠道来源不能为空")
    @Size(max = 64, message = "渠道来源长度不能超过 64")
    private String channelSource;

    /** 权益类型 */
    @Size(max = 128, message = "权益类型长度不能超过 128")
    private String benefitType;

    /** 是否参与抽奖：1=是 0=否 */
    @NotNull(message = "是否参与抽奖不能为空")
    @Min(value = 0, message = "是否参与抽奖只能为 0 或 1")
    @Max(value = 1, message = "是否参与抽奖只能为 0 或 1")
    private Integer drawEligible;

    /** 排序（小的在前） */
    private Integer sort;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChannelSource() { return channelSource; }
    public void setChannelSource(String channelSource) { this.channelSource = channelSource; }

    public String getBenefitType() { return benefitType; }
    public void setBenefitType(String benefitType) { this.benefitType = benefitType; }

    public Integer getDrawEligible() { return drawEligible; }
    public void setDrawEligible(Integer drawEligible) { this.drawEligible = drawEligible; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
