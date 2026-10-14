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
 * 限流说明：这两个接口只接受「11 位手机号」或「4 位尾号」，本身已限制了枚举面
 * （尾号全量 10^4 次），但仍需挡住脚本批量拉取参会人姓名。
 * 阈值刻意放宽到 200 次/分钟：现场观众手机共用同一个 WiFi 出口 IP，
 * 限得太紧会误伤排队签到的人；200/分钟 下把 10^4 个尾号扫一遍需 50 分钟以上，
 * 已足以让批量枚举失去性价比。
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
     * 支持后四位是为了「截图丢了当场找回」——让人只需记住自己号码的尾号。
     * 代价是存在枚举面：尾号 + 端口号足以通过兑奖核验（核验接受后四位），
     * 所以这里把限流压到 30 次/分钟，撞号时在服务端留痕便于事后复盘。
     * 若要彻底关掉这条路，需让兑奖核验只接受完整 11 位手机号（现场会多打几个字）。
     */
    @RateLimiter(time = 60, count = 30, limitType = LimitType.IP)
    @GetMapping("/lookup")
    public AjaxResult lookup(@RequestParam(required = false) String keyword)
    {
        return success(checkinService.lookup(keyword));
    }
}
