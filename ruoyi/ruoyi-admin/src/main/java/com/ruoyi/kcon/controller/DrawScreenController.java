package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.service.IKconDrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 抽奖大屏接口（只读展示）
 *
 * 职责边界：大屏只负责展示，不负责抽奖。抽奖全程由管理端「抽奖控制台」
 * （{@link DrawAdminController}）发起并落库，大屏靠轮询 {@code /status} 被动感知
 * 新批次并播放揭晓动画。因此本控制器只暴露读取状态与切换场次两个能力，
 * 不再提供任何主动抽奖 / 作废入口——避免大屏成为绕过管理端的后门。
 *
 * 大屏部署在投屏机上，免登录开放（现场便利优先）。免登录不等于无防护：
 *   1. {@code @RateLimiter(limitType = IP)} 按来源 IP 限速，挡住脚本刷状态；
 *   2. {@code @Log} 记录每次切换场次，事后可追溯；
 *   3. {@code ResourcesConfig} 默认不开放 CORS，第三方网页无法跨站调用本机大屏接口。
 *
 * 对应接口文档 §3
 *
 * @author kcon
 */
@Anonymous
@RestController
@RequestMapping("/app/screen")
public class DrawScreenController
{
    @Autowired
    private IKconDrawService drawService;

    /**
     * 3.1 当前场次 + 统计 + 当前应抽档位。
     * 带上大屏上次收到的 {@code version} 时走增量：未变化只回一个极小的响应，
     * 大屏据此跳过整份重渲染（投屏中重渲染会打断中奖动画）。
     */
    @GetMapping("/status")
    public AjaxResult status(@RequestParam(required = false) String version)
    {
        Map<String, Object> data = drawService.screenStatus();
        if (version != null && version.equals(String.valueOf(data.get("version"))))
        {
            Map<String, Object> unchanged = new LinkedHashMap<>();
            unchanged.put("unchanged", true);
            unchanged.put("version", version);
            return AjaxResult.success(unchanged);
        }
        return AjaxResult.success(data);
    }

    /** 3.2 切换场次 { "sessionCode": "PM" } */
    @Log(title = "抽奖大屏-切换场次", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @PostMapping("/session")
    public AjaxResult session(@RequestBody Map<String, String> body)
    {
        String code = body == null ? null : body.get("sessionCode");
        drawService.switchSession(code);
        return AjaxResult.success(drawService.screenStatus());
    }

    // 注意：大屏只负责展示，不再提供任何主动抽奖 / 作废入口。
    // 抽奖全程由管理端「抽奖控制台」（DrawAdminController）负责，大屏靠轮询 /status
    // 被动感知新批次并播放揭晓动画。这里曾有的 /draw、/void 免登录端点已删除——
    // 留着就是绕过管理端的后门，违背「控制权唯一」原则。
}
