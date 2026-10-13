package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.kcon.domain.KconVerifyLog;
import com.ruoyi.kcon.domain.KconVerifyLogExportVo;
import com.ruoyi.kcon.service.IKconVerifyService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 兑奖核验日志查询（管理端）
 *
 * 只读接口：每次核验与发放都已在 KconVerifyServiceImpl 留痕，本页只查不改。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/verifylog")
public class VerifyLogController extends BaseController
{
    @Autowired
    private IKconVerifyService verifyService;

    /**
     * 核验日志列表（分页 + 筛选）
     *
     * GET /admin/verifylog/list
     *   pageNum / pageSize   分页
     *   portNo               端口号，精确匹配
     *   result               核验结果：VALID / NOT_ISSUED / PHONE_MISMATCH / RECEIVED
     *   params.beginTime     操作时间起 yyyy-MM-dd HH:mm:ss
     *   params.endTime       操作时间止 yyyy-MM-dd HH:mm:ss
     */
    @PreAuthorize("@ss.hasPermi('kcon:verify:query')")
    // 不加 @Log：列表查询会被频繁翻页/筛选，每次写一条操作日志没有追溯价值，
    // 反而把 sys_oper_log 撑爆。需要留痕的是「导出」这种敏感动作，下面已加。
    @GetMapping("/list")
    public TableDataInfo list(KconVerifyLog query)
    {
        startPage();
        List<KconVerifyLog> list = verifyService.selectVerifyLogList(query);
        return getDataTable(list);
    }

    /** 导出核验日志，条件与列表一致，手机号同样脱敏 */
    @PreAuthorize("@ss.hasPermi('kcon:verify:query')")
    @Log(title = "兑奖日志导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KconVerifyLog query)
    {
        List<KconVerifyLogExportVo> list = verifyService.selectVerifyLogExportList(query);
        ExcelUtil<KconVerifyLogExportVo> util = new ExcelUtil<>(KconVerifyLogExportVo.class);
        util.exportExcel(response, list, "兑奖核验日志");
    }
}
