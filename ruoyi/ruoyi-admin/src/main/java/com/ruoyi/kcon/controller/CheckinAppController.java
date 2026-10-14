package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.service.IKconCheckinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 签到端接口（对观众开放，免登录）
 *
 * 对应接口文档 §2
 *
 * 限流说明：查询类接口（query / lookup）都需要挡住脚本批量拉取参会人姓名。
 * lookup 只接受完整 11 位手机号（见下方注释），枚举面 10^11，实际不可枚举，
 * 限流 30 次/分钟只作为防批量拉取的一道闸。
 * query 仍保留 4 位尾号（现场签到第一入口，后四位命中后让人自己认领），
 * 尾号全量 10^4 次，因此 query 阈值刻意放宽到 200 次/分钟：现场观众手机共用
 * 同一个 WiFi 出口 IP，限得太紧会误伤排队签到的人；200/分钟 下把 10^4 个尾号
 * 扫一遍需 50 分钟以上，已足以让批量枚举失去性价比。
 *
 * @author kcon
 */
@Anonymous
@RestController
@RequestMapping("/app/checkin")
public class CheckinAppController extends BaseController
{
    @Autowired
    private IKconCheckinService checkinService;

    /**
     * 2.1 查询参会人
     * POST /app/checkin/query  { "keyword": "13800138000" }
     */
    @RateLimiter(time = 60, count = 200, limitType = LimitType.IP)
    @PostMapping("/query")
    public AjaxResult query(@RequestBody Map<String, String> body)
    {
        String keyword = body == null ? null : body.get("keyword");
        return success(checkinService.queryAttendee(keyword));
    }

    /**
     * 2.2 确认签到
     * POST /app/checkin/submit  { "attendeeId": 12 }
     */
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("attendeeId") == null)
        {
            return error("参数缺失：attendeeId");
        }
        Long attendeeId;
        try
        {
            attendeeId = Long.valueOf(String.valueOf(body.get("attendeeId")));
        }
        catch (NumberFormatException e)
        {
            return error("attendeeId 格式错误");
        }
        return success(checkinService.submitCheckin(attendeeId));
    }

    /**
     * 2.3 回查签到状态与端口号
     * GET /app/checkin/lookup?keyword=13800138000
     *
     * 只接受完整 11 位手机号。曾支持后四位（「截图丢了当场找回」），
     * 但尾号空间仅 10^4，在免登录接口上可被低成本枚举，等于把「谁在场、
     * 尾号是多少、端口号是多少」全量敞开；而兑奖核验的防冒领判定正是
     * 「本人知道完整手机号」，源头可枚举则这层判定形同虚设。
     * 现已砍掉尾号分支：找回与核验统一要求完整 11 位，枚举面从 10^4 收到 10^11。
     * 限流仍保留 30 次/分钟，作为防批量拉取姓名的一道闸。
     */
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    @GetMapping("/lookup")
    public AjaxResult lookup(@RequestParam(required = false) String keyword)
    {
        return success(checkinService.lookup(keyword));
    }
}
