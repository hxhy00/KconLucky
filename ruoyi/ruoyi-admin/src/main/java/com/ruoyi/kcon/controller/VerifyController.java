package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.service.IKconDrawService;
import com.ruoyi.kcon.service.IKconVerifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 兑奖核验管理端
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/verify")
public class VerifyController extends BaseController
{
    @Autowired
    private IKconVerifyService verifyService;

    @Autowired
    private IKconDrawService drawService;

    /**
     * 核验 { "portNo": 12345, "phone": "13800138000" 或后四位 }
     *
     * 这里刻意不加 @RepeatSubmit：核验是只读判定，同一组输入被重复提交是合法的——
     * 发放后要带着同一组输入再核一次刷新成「已领取」，核验失败也要能立刻重试。
     * 加了防重会把这些正常操作拦成「不允许重复提交」，而前端又是静默 catch，
     * 表现为「发放成功但表单没清空」这种很难查的现象。
     * 真正需要防重的是下面的 /receive（发放），它有状态校验且不可撤销。
     */
    @PreAuthorize("@ss.hasPermi('kcon:verify:query')")
    @PostMapping
    public AjaxResult verify(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("portNo") == null)
        {
            return error("请输入端口号");
        }
        Integer portNo;
        try
        {
            portNo = Integer.valueOf(String.valueOf(body.get("portNo")));
        }
        catch (NumberFormatException e)
        {
            return error("端口号格式错误");
        }
        String phone = body.get("phone") == null ? null : String.valueOf(body.get("phone"));
        return success(verifyService.verify(portNo, phone, getUsername()));
    }

    /**
     * 确认发放 { "portNo": 12345, "phone": "13800138000" }
     */
    @PreAuthorize("@ss.hasPermi('kcon:verify:receive')")
    @Log(title = "兑奖发放", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/receive")
    public AjaxResult receive(@RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("portNo") == null)
        {
            return error("请输入端口号");
        }
        Integer portNo;
        try
        {
            portNo = Integer.valueOf(String.valueOf(body.get("portNo")));
        }
        catch (NumberFormatException e)
        {
            return error("端口号格式错误");
        }
        String phone = body.get("phone") == null ? null : String.valueOf(body.get("phone"));
        verifyService.receive(portNo, phone, getUsername());
        return success("发放成功");
    }

    /**
     * 作废重抽：状态置 2，端口号自动回池（接口文档 3.4）
     */
    @PreAuthorize("@ss.hasPermi('kcon:winner:void')")
    @Log(title = "中奖作废", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/void/{winnerId}")
    public AjaxResult voidWinner(@PathVariable Long winnerId)
    {
        drawService.voidWinner(winnerId, getUsername());
        return success("作废成功，端口号已回池");
    }

    /** 中奖记录列表 */
    @PreAuthorize("@ss.hasPermi('kcon:winner:list')")
    @GetMapping("/winners")
    public TableDataInfo winners(KconWinner query)
    {
        startPage();
        List<KconWinner> list = drawService.selectWinnerList(query);
        return getDataTable(list);
    }
}
