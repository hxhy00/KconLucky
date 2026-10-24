package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.RepeatSubmit;
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

    /**
     * 作废上一次中奖 { "winnerId": 33 }，号码回池可重抽
     *
     * 作废是防作弊的最后一道闸：抽奖台操作员若同时能作废，就能把已抽出的记录撤回重抽，
     * 因此这里用独立的 {@code kcon:winner:void} 权限，不再与抽奖操作共用。
     */
    @PreAuthorize("@ss.hasPermi('kcon:winner:void')")
    @Log(title = "抽奖控制台-作废中奖", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @RepeatSubmit
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

    /**
     * 撤回最近一档 { "sessionCode": "PM" }：把该场次最后一次抽出的整批全部作废，号码回池
     *
     * 与单条作废同理，必须使用 {@code kcon:winner:void}，抽奖台操作员不得自带撤回能力。
     */
    @PreAuthorize("@ss.hasPermi('kcon:winner:void')")
    @Log(title = "抽奖控制台-撤回最近一档", businessType = BusinessType.OTHER)
    @RateLimiter(time = 10, count = 20, limitType = LimitType.IP)
    @RepeatSubmit
    @PostMapping("/void-batch")
    public AjaxResult doVoidBatch(@RequestBody Map<String, Object> body)
    {
        // 与 draw 一致用 String.valueOf：前端误传数字时不会 ClassCastException
        String sessionCode = body == null || body.get("sessionCode") == null
                ? null : String.valueOf(body.get("sessionCode"));
        int voided = drawService.voidLastBatch(sessionCode, getUsername());
        if (voided == 0)
        {
            return error("当前场次没有可撤回的中奖记录");
        }
        // 顺带回一份刷新后的状态，控制台一次请求完成「撤回 + 更新计数」
        Map<String, Object> result = buildStatus();
        result.put("voidedCount", voided);
        return success(result);
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
     * + lastWinner（本场次最近一条仍可作废的中奖记录，撤回按钮的锚点）
     * + winnerGroups（全部有效中奖，按场次 × 档位分组）
     */
    private Map<String, Object> buildStatus()
    {
        Map<String, Object> data = drawService.screenStatus();
        Object code = data.get("sessionCode");
        String sessionCode = code == null ? "AM" : String.valueOf(code);

        // 0. spec 006 FR-A7/A8：以下三项只在管理端返回，**不放进 screenStatus()**。
        //    checkedInToday 与 poolCount 的差额 = 被排除 + 已中奖，匿名请求同样能反推口径。
        data.put("excludedCount", drawService.countExcludedToday());
        data.put("checkedInToday", drawService.countCheckedInToday());
        data.put("winnerCount", drawService.countWinnersAll());

        // 1. 各档位奖池进度：直接复用奖品档位查询，mapper 已按 draw_order 升序
        KconPrize tierQuery = new KconPrize();
        tierQuery.setSessionCode(sessionCode);
        data.put("tiers", drawService.selectPrizeList(tierQuery));

        // 2. 上一次中奖：只取「已中奖未领取未作废」，mapper 已按 win_time desc, id desc 排序，
        //    取首条即为最近一次。已领取的记录 Service 会拒绝作废，这里提前过滤掉，
        //    免得控制台把一个必然失败的按钮摆给操作员。
        //    startPage() 走 RuoYi 默认分页（1 页 10 条）限制返回量，取完首条即可。
        //    分页参数存在 ThreadLocal，必须 try/finally 清掉：
        //    若查询抛异常，残留参数会污染同一 Tomcat 线程上的下一个请求。
        KconWinner winnerQuery = new KconWinner();
        winnerQuery.setSessionCode(sessionCode);
        winnerQuery.setStatus(KconWinner.STATUS_WON);
        List<KconWinner> winners;
        startPage();
        try
        {
            winners = drawService.selectWinnerList(winnerQuery);
        }
        finally
        {
            clearPage();
        }
        data.put("lastWinner", winners == null || winners.isEmpty() ? null : toLastWinner(winners.get(0)));

        // 3. 中奖记录（按场次 × 档位分组）：控制台不再只显示全局最近一位，
        //    操作员能一眼核对每档已抽出的人。放在 lastWinner 之后执行，
        //    避免其内部多条查询消耗掉 startPage() 的分页上下文。
        data.put("winnerGroups", drawService.winnerGroups());

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