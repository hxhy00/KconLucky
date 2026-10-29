package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
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
 * 限流说明：原先三个接口都挂了按 IP 的 @RateLimiter，现场直接去掉了。
 * 原因是观众手机都走同一个 WiFi 出口（部分场景还是运营商 CGNAT），
 * 全场的请求共用同一个限流桶，人多时大面积 429，排队签到的观众被挡在门外，
 * 代价远大于「挡住脚本批量拉取」的收益。
 * query / lookup 仍然只接受完整 11 位手机号，枚举面 10^11，脚本批量拉取的
 * 成本本身已经足够高；若日后要恢复，按「手机号维度」而不是按 IP 做二级限流。
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
    @PostMapping("/query")
    public AjaxResult query(@RequestBody Map<String, String> body)
    {
        String keyword = body == null ? null : body.get("keyword");
        return success(checkinService.queryAttendee(keyword));
    }

    /**
     * 2.2 确认签到
     * POST /app/checkin/submit  { "attendeeId": 12, "phone": "13800138000" }
     *
     * 公网版加固：必须同时提交完整 11 位手机号，服务端与名单等值比对后才落库。
     * 后四位只用于 query 定位，不能作为提交凭证——尾号空间只有 10^4，不构成秘密。
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
        String phone = body.get("phone") == null ? null : String.valueOf(body.get("phone"));
        return success(checkinService.submitCheckin(attendeeId, phone));
    }

    /**
     * 2.3 回查签到状态与端口号
     * POST /app/checkin/lookup  { "keyword": "13800138000" }
     *
     * 只接受完整 11 位手机号。曾支持后四位（「截图丢了当场找回」），
     * 但尾号空间仅 10^4，在免登录接口上可被低成本枚举，等于把「谁在场、
     * 尾号是多少、端口号是多少」全量敞开；而兑奖核验的防冒领判定正是
     * 「本人知道完整手机号」，源头可枚举则这层判定形同虚设。
     * 现已砍掉尾号分支：找回与核验统一要求完整 11 位，枚举面从 10^4 收到 10^11。
     *
     * 用 POST 而非 GET：nginx access_log 的 $request 会记录完整请求行，
     * GET 查询串会把观众手机号明文写进访问日志，POST body 则不会。
     */
    @PostMapping("/lookup")
    public AjaxResult lookup(@RequestBody(required = false) Map<String, String> body)
    {
        String keyword = body == null ? null : body.get("keyword");
        return success(checkinService.lookup(keyword));
    }
}
