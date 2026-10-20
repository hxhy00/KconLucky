package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.service.IKconChannelMappingService;
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
 * 渠道来源映射管理端
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/channel-mapping")
public class ChannelMappingController extends BaseController
{
    @Autowired
    private IKconChannelMappingService channelMappingService;

    @PreAuthorize("@ss.hasPermi('kcon:channel:list')")
    @GetMapping("/list")
    public TableDataInfo list(KconChannelMapping query)
    {
        startPage();
        List<KconChannelMapping> list = channelMappingService.selectList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('kcon:channel:add')")
    @Log(title = "渠道来源映射", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult add(@Validated @RequestBody KconChannelMapping mapping)
    {
        mapping.setCreateBy(getUsername());
        return toAjax(channelMappingService.insert(mapping));
    }

    @PreAuthorize("@ss.hasPermi('kcon:channel:edit')")
    @Log(title = "渠道来源映射", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody KconChannelMapping mapping)
    {
        mapping.setUpdateBy(getUsername());
        return toAjax(channelMappingService.update(mapping));
    }

    @PreAuthorize("@ss.hasPermi('kcon:channel:remove')")
    @Log(title = "渠道来源映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(channelMappingService.deleteById(id));
    }
}
