package com.ruoyi.kcon.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏展示设置
 *
 * 抽奖大屏投屏给全场观众，赞助商名称这类内容运营会频繁调整，
 * 所以做成可在管理端直接改的配置，而不是让技术改代码重新发版。
 * 配置落在系统参数表（sys_config，键名 kcon.sponsors），大屏轮询时读取。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/draw")
public class DrawSettingController extends BaseController
{
    private static final String SPONSORS_KEY = "kcon.sponsors";

    @Autowired
    private ISysConfigService sysConfigService;

    /**
     * 读取大屏设置
     */
    @PreAuthorize("@ss.hasPermi('kcon:draw:query')")
    @GetMapping("/setting")
    public AjaxResult getSetting()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("sponsors", splitSponsors(sysConfigService.selectConfigByKey(SPONSORS_KEY)));
        return ajax;
    }

    /**
     * 保存大屏设置
     *
     * @param body { "sponsors": "某某科技, 某某网络" }
     *        前端 POST 走的是 application/json，所以这里必须用 @RequestBody，
     *        用 @RequestParam 会收不到值（表现为保存成功但配置没变）
     */
    @PreAuthorize("@ss.hasPermi('kcon:draw:operate')")
    @Log(title = "大屏设置", businessType = BusinessType.UPDATE)
    @PostMapping("/setting")
    public AjaxResult saveSetting(@RequestBody Map<String, String> body)
    {
        // 区分「没带 sponsors 字段」和「显式清空」：空 body / {} 提交不该把线上
        // 赞助商静默清掉（2026-10-15 测试报告问题 3），只有显式传 {"sponsors":""}
        // 才视为清空意图。
        if (body == null || !body.containsKey("sponsors"))
        {
            return error("缺少 sponsors 字段，未做任何修改");
        }
        String value = StringUtils.trimToEmpty(body.get("sponsors"));

        // 走 ISysConfigService 是因为它会同时写库与刷新 Redis 缓存，
        // 直接往 Redis 塞值容器重启就丢了。
        SysConfig query = new SysConfig();
        query.setConfigKey(SPONSORS_KEY);
        List<SysConfig> list = sysConfigService.selectConfigList(query);

        if (StringUtils.isEmpty(value))
        {
            // 清空 = 删除该配置项。
            // 不能靠 updateConfig 传空串：框架 mapper 的 XML 里写的是
            // <if test="configValue != null and configValue != ''">，
            // 空串会被这个条件跳过，改了等于没改（清不掉的坑）。
            if (!list.isEmpty())
            {
                sysConfigService.deleteConfigByIds(new Long[] { list.get(0).getConfigId() });
            }
            return success("已清空赞助商，大屏将在 3 秒内自动刷新");
        }

        SysConfig config = list.isEmpty() ? new SysConfig() : list.get(0);
        config.setConfigKey(SPONSORS_KEY);
        config.setConfigValue(value);
        config.setConfigName("大屏赞助商");
        config.setConfigType("N");
        config.setRemark("抽奖大屏底部「支持单位」显示内容，多个用逗号分隔；留空则不显示");
        if (list.isEmpty())
        {
            sysConfigService.insertConfig(config);
        }
        else
        {
            sysConfigService.updateConfig(config);
        }

        // 大屏每 1.5 秒轮询一次状态接口，改完最多等一轮就会显示出来
        return success("已保存，大屏将在 1.5 秒内自动刷新显示");
    }

    /** 把逗号/中文逗号分隔的配置值拆成列表 */
    private List<String> splitSponsors(String raw)
    {
        List<String> list = new ArrayList<>();
        if (StringUtils.isEmpty(raw))
        {
            return list;
        }
        for (String item : raw.split("[,，]"))
        {
            String trimmed = item.trim();
            if (StringUtils.isNotEmpty(trimmed))
            {
                list.add(trimmed);
            }
        }
        return list;
    }
}