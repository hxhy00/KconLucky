package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 奖品档位对象 kcon_prize
 *
 * @author kcon
 */
public class KconPrize extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 场次 AM/PM */
    @NotBlank(message = "场次不能为空")
    private String sessionCode;

    /** 档位名：特等奖/一等奖/二等奖/三等奖 */
    @Excel(name = "奖项")
    @NotBlank(message = "奖项名称不能为空")
    private String tierName;

    /** 数量 */
    @Excel(name = "数量")
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;

    /** 已抽出数量 */
    private Integer drawnCount;

    /** 抽取顺序（小的先抽） */
    private Integer drawOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public String getTierName() { return tierName; }
    public void setTierName(String tierName) { this.tierName = tierName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getDrawnCount() { return drawnCount; }
    public void setDrawnCount(Integer drawnCount) { this.drawnCount = drawnCount; }

    public Integer getDrawOrder() { return drawOrder; }
    public void setDrawOrder(Integer drawOrder) { this.drawOrder = drawOrder; }
}
