package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.service.IKconDrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 抽奖大屏接口（只读展示）
 *
 * 职责边界：大屏只负责展示，不负责抽奖、也不负责切场次。抽奖与切场次全程由管理端
 * 「抽奖控制台」（{@link DrawAdminController}）发起并落库，大屏靠轮询 {@code /status}
 * 被动感知新批次 / 场次变化并播放揭晓动画。因此本控制器是纯只读的，只暴露读取状态
 * 一个能力，不再提供任何主动抽奖 / 作废 / 切场次入口——避免大屏成为绕过管理端的后门。
 *
 * 大屏部署在投屏机上，免登录开放（现场便利优先）。免登录不等于无防护：
 *   1. 接口全部只读，改不了任何状态，最多被刷状态；
 *   2. {@code ResourcesConfig} 默认不开放 CORS，第三方网页无法跨站调用本机大屏接口。
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
     *
     * 限流口径必须覆盖大屏自身轮询：大屏 1.5s 一拍 = 40 次/分钟，
     * 曾经只给 30 次/分钟，大屏跑 45 秒就把自己限流到 500，
     * 前端连续失败 3 次亮「与后端失去连接」，下个窗口又恢复——
     * 现场表现就是大屏周期性「未连接」。放宽到 100 次/分钟：
     * 正常轮询（40/min）留足余量，仍能挡住批量枚举端口号的脚本。
     */
    @RateLimiter(time = 60, count = 100, limitType = LimitType.IP)
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

    // 注意：大屏只负责展示，不再提供任何写入口。曾有的免登录端点及删除原因：
    //   /session 切场次 → 现由管理端 DrawAdminController#session（带 kcon:draw:operate 权限）
    //   /draw    抽一档 → 现由管理端 DrawAdminController 发起
    //   /void    作废重抽 → 现由管理端 DrawAdminController 发起
    // 大屏一律靠轮询 /status 被动感知，留着这些端点就是绕过权限控制的后门，
    // 违背「控制权唯一」原则。
}
