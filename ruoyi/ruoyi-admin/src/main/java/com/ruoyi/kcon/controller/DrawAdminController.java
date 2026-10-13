package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.service.IKconDrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽奖控制台管理端（FR-16 场次管理 / FR-19 抽奖池实时显示 / FR-20 无人可抽提示）
 *
 * 与大屏端 {@code DrawScreenController} 的关系：
 *   两者共用同一个 {@link IKconDrawService}，抽奖池计算、档位占用、号码回池等业务逻辑
 *   全部在 Service 层，本类只做「鉴权 + 取用户名 + 组装响应」，不复制任何业务规则。
 *
 * 差异点：
 *   1. 大屏在投屏机上、免登录；本类需登录并校验 {@code kcon:draw:query / kcon:draw:operate}，
 *      操作人取登录用户名（{@code getUsername()}）而非大屏写死的「大屏操作员」，便于事后追溯。
 *   2. 大屏只需要当前应抽档位；控制台还要展示各档位奖池进度与「上一次中奖」（供作废按钮用），
 *      故 {@link #buildStatus()} 在 screenStatus() 之上补了 tiers / lastWinner 两项。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/draw")
public class DrawAdminController extends BaseController
{
    @Autowired
    private IKconDrawService drawService;

    /** 抽奖池实时状态：当前场次 + 统计 + 当前应抽档位 + 档位进度 + 上一次中奖 */
    @PreAuthorize("@ss.hasPermi('kcon:draw:query')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(buildStatus());
    }

    /** 抽一个 { "sessionCode": "AM", "prizeId": 1 }；prizeId 为空时自动取当前应抽档位 */
    @PreAuthorize("@ss.hasPermi('kcon:draw:operate')")
    @Log(title = "抽奖控制台-抽奖", businessType = BusinessType.OTHER)
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
                return error("prizeId 格式错误");
            }
        }
        Map<String, Object> result = drawService.draw(sessionCode, prizeId, getUsername());
        // 抽奖结果里顺带回一份刷新后的状态，控制台一次请求即可完成「出结果 + 更新计数」
        result.put("status", buildStatus());
        return success(result);
    }

    /** 作废上一次中奖 { "winnerId": 33 }，号码回池可重抽 */
    @PreAuthorize("@ss.hasPermi('kcon:draw:operate')")
    @Log(title = "抽奖控制台-作废中奖", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @PostMapping("/void")
    public AjaxResult doVoid(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("winnerId") == null)
        {
            return error("参数缺失：winnerId");
        }
        Long winnerId;
        try
        {
            winnerId = Long.valueOf(String.valueOf(body.get("winnerId")));
        }
        catch (NumberFormatException e)
        {
            return error("winnerId 格式错误");
        }
        drawService.voidWinner(winnerId, getUsername());
        return success(buildStatus());
    }

    /** 切换场次 { "sessionCode": "PM" } */
    @PreAuthorize("@ss.hasPermi('kcon:draw:operate')")
    @Log(title = "抽奖控制台-切换场次", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @PostMapping("/session")
    public AjaxResult session(@RequestBody Map<String, String> body)
    {
        String code = body == null ? null : body.get("sessionCode");
        drawService.switchSession(code);
        return success(buildStatus());
    }

    /**
     * 组装控制台状态
     *
     * = screenStatus()（当前场次 / 三项统计 / sessions / currentTier）
     * + tiers（本场次各档位奖池进度）
     * + lastWinner（本场次最近一条仍可作废的中奖记录）
     */
    private Map<String, Object> buildStatus()
    {
        Map<String, Object> data = drawService.screenStatus();
        Object code = data.get("sessionCode");
        String sessionCode = code == null ? "AM" : String.valueOf(code);

        // 1. 各档位奖池进度：直接复用奖品档位查询，mapper 已按 draw_order 升序
        KconPrize tierQuery = new KconPrize();
        tierQuery.setSessionCode(sessionCode);
        data.put("tiers", drawService.selectPrizeList(tierQuery));

        // 2. 上一次中奖：只取「已中奖未领取未作废」，mapper 已按 win_time desc, id desc 排序，
        //    取首条即为最近一次。已领取的记录 Service 会拒绝作废，这里提前过滤掉，
        //    免得控制台把一个必然失败的按钮摆给操作员。
        //    startPage() 走 RuoYi 默认分页（1 页 10 条）限制返回量，取完首条即可。
        KconWinner winnerQuery = new KconWinner();
        winnerQuery.setSessionCode(sessionCode);
        winnerQuery.setStatus(KconWinner.STATUS_WON);
        startPage();
        List<KconWinner> winners = drawService.selectWinnerList(winnerQuery);
        data.put("lastWinner", winners == null || winners.isEmpty() ? null : toLastWinner(winners.get(0)));

        return data;
    }

    private Map<String, Object> toLastWinner(KconWinner w)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("portNo", w.getPortNo());
        m.put("name", w.getName());
        m.put("company", w.getCompany());
        m.put("tierName", w.getTierName());
        m.put("winTime", w.getWinTime() == null ? null
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(w.getWinTime()));
        return m;
    }
}