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
     */
    @GetMapping("/lookup")
    public AjaxResult lookup(@RequestParam(required = false) String keyword)
    {
        return success(checkinService.lookup(keyword));
    }
}
