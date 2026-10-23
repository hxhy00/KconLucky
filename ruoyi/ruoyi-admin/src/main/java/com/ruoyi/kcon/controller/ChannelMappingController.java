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
import java.util.Map;

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

    /**
     * 下拉框数据源：不分页返回全量映射。
     * 名单页/导入预检的渠道下拉若走分页 /list，写死 pageSize 会在映射条数
     * 超过页大小时静默截断；映射表是白名单且量级很小（几十条），直接全量返回。
     *
     * 权限：除渠道映射维护者（kcon:channel:list）外，能给名单选渠道的角色
     * （kcon:attendee:list，如现场工作人员）也必须可读——否则进入参会名单页
     * 时下拉数据拉取失败，会弹「没有权限」，而该页本身是工作人员有权使用的。
     * 这里只返回「渠道 → 权益/资格」配置，不含任何凭据。
     */
    @PreAuthorize("@ss.hasAnyPermi('kcon:channel:list,kcon:attendee:list')")
    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        return success(channelMappingService.selectList(new KconChannelMapping()));
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

    /**
     * 全量重算：把当前所有渠道映射重新应用到存量名单。
     * 用于修复「先导入、后补映射」留下的资格错乱数据。
     * POST /admin/channel-mapping/resync
     */
    @PreAuthorize("@ss.hasPermi('kcon:channel:edit')")
    @Log(title = "渠道映射重算", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/resync")
    public AjaxResult resync()
    {
        Map<String, Object> data = channelMappingService.resyncAll();
        String msg = String.format("重算完成：已同步 %s 人，%s 人因已签到/已中奖未修改",
                data.get("synced"), data.get("lockedCount"));
        return AjaxResult.success(msg, data);
    }
}
