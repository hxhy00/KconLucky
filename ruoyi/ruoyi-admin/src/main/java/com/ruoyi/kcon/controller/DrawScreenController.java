package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.kcon.service.IKconDrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 抽奖大屏接口
 *
 * 大屏部署在投屏机上，投屏与运行同机，操作员现场直接操作，故为免登录开放
 * （现场便利优先，不引入登录步骤）。
 *
 * 免登录不等于无防护，靠以下四条不增加操作步骤的约束兜住业务完整性：
 *   1. {@code @RateLimiter(limitType = IP)} 按来源 IP 限速，挡住脚本刷奖/遍历作废；
 *   2. {@code @Log} 记录每次抽奖与作废，事后可追溯；
 *   3. {@code ResourcesConfig} 默认不开放 CORS，第三方网页无法跨站调用本机大屏接口；
 *   4. 作废只允许针对「最近一条有效中奖记录」，与大屏 UI 能力一致，无法批量清空奖池。
 *
 * 如需收紧，去掉类上的 {@code @Anonymous} 改为需登录，并由操作员登录后访问。
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

    @Autowired
    private KconWinnerMapper winnerMapper;

    /** 3.1 当前场次 + 统计 + 当前应抽档位 */
    @GetMapping("/status")
    public AjaxResult status()
    {
        return AjaxResult.success(drawService.screenStatus());
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

    /** 3.3 抽一个 { "sessionCode": "AM", "prizeId": 1 } */
    @Log(title = "抽奖大屏-抽奖", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @PostMapping("/draw")
    public AjaxResult draw(@RequestBody Map<String, Object> body)
    {
        // 用 String.valueOf 而非 (String) 强转：前端若传 {"sessionCode":123}，
        // 强转会抛 ClassCastException，变成看不懂的 500
        String sessionCode = body == null || body.get("sessionCode") == null
                ? null : String.valueOf(body.get("sessionCode"));
        Long prizeId = null;
        if (body != null && body.get("prizeId") != null
                && !"".equals(String.valueOf(body.get("prizeId"))))
        {
            try
            {
                prizeId = Long.valueOf(String.valueOf(body.get("prizeId")));
            }
            catch (NumberFormatException e)
            {
                return AjaxResult.error("prizeId 格式错误");
            }
        }
        return AjaxResult.success(drawService.draw(sessionCode, prizeId, "大屏操作员"));
    }

    /** 3.4 作废重抽 { "winnerId": 33 } */
    @Log(title = "抽奖大屏-作废中奖", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @PostMapping("/void")
    public AjaxResult doVoid(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("winnerId") == null)
        {
            return AjaxResult.error("参数缺失：winnerId");
        }
        Long winnerId;
        try
        {
            winnerId = Long.valueOf(String.valueOf(body.get("winnerId")));
        }
        catch (NumberFormatException e)
        {
            return AjaxResult.error("winnerId 格式错误");
        }
        // 本接口免登录（大屏是固定在现场的展示终端），所以不能像管理端那样放开作废任意记录。
        // 只允许作废「最近一条有效中奖记录」——正是大屏当前展示、操作员要撤掉的那一条，
        // 与大屏 UI 的能力一致。这样即使有人从内网直连调用，也只能影响最新一条，
        // 且必然被现场立刻察觉，无法用脚本批量清空奖池。
        KconWinner latest = winnerMapper.selectLatestValidWinner();
        if (latest == null || !latest.getId().equals(winnerId))
        {
            return AjaxResult.error("只能作废最近一条中奖记录，请在大屏上确认后再试");
        }
        drawService.voidWinner(winnerId, "大屏操作员");
        return AjaxResult.success(drawService.screenStatus());
    }
}
