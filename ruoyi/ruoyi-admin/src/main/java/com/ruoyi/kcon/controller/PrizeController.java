package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.service.IKconDrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 奖品档位管理端
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/prize")
public class PrizeController extends BaseController
{
    @Autowired
    private IKconDrawService drawService;

    @PreAuthorize("@ss.hasPermi('kcon:prize:list')")
    @GetMapping("/list")
    public TableDataInfo list(KconPrize query)
    {
        startPage();
        List<KconPrize> list = drawService.selectPrizeList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('kcon:prize:add')")
    @Log(title = "奖品档位", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult add(@Validated @RequestBody KconPrize prize)
    {
        // 同一场次里出现同名档位，「当前应抽档位」会同时命中两条，
        // 现场没法判断该抽哪个。前端也加了提交中禁用，但慢响应下拦不住，这里兜底。
        KconPrize query = new KconPrize();
        query.setSessionCode(prize.getSessionCode());
        for (KconPrize p : drawService.selectPrizeList(query))
        {
            if (p.getTierName() != null && p.getTierName().equals(prize.getTierName()))
            {
                return error("该场次已存在同名奖项「" + prize.getTierName() + "」，请勿重复添加");
            }
        }
        prize.setCreateBy(getUsername());
        return toAjax(drawService.insertPrize(prize));
    }

    @PreAuthorize("@ss.hasPermi('kcon:prize:edit')")
    @Log(title = "奖品档位", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody KconPrize prize)
    {
        prize.setUpdateBy(getUsername());
        return toAjax(drawService.updatePrize(prize));
    }

    @PreAuthorize("@ss.hasPermi('kcon:prize:remove')")
    @Log(title = "奖品档位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(drawService.deletePrizeById(id));
    }
}
