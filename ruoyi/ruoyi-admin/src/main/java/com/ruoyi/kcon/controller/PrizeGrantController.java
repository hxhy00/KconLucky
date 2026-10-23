package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconPrizeGrantExportVo;
import com.ruoyi.kcon.service.IKconBenefitGrantService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 奖品发放日志管理端
 *
 * 奖品发放由兑奖核验「确认发放」写入（kcon_benefit_grant，type=PRIZE），本控制器只读。
 * 与权益发放日志是两个独立页面，类型固定为 PRIZE，请求参数无法改写。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/prize-grant")
public class PrizeGrantController extends BaseController
{
    @Autowired
    private IKconBenefitGrantService grantService;

    /**
     * 奖品发放日志列表（分页 + 筛选）
     *
     * GET /admin/prize-grant/list
     *   portNo              端口号，精确匹配
     *   name                姓名，模糊匹配
     *   params.beginTime    发放时间起 yyyy-MM-dd HH:mm:ss
     *   params.endTime      发放时间止 yyyy-MM-dd HH:mm:ss
     */
    @PreAuthorize("@ss.hasPermi('kcon:prizegrant:list')")
    // 不加 @Log：列表查询频繁翻页/筛选，无追溯价值
    @GetMapping("/list")
    public TableDataInfo list(KconBenefitGrant query)
    {
        query.setGrantType(KconBenefitGrant.TYPE_PRIZE);
        startPage();
        List<KconBenefitGrant> list = grantService.selectGrantList(query);
        return getDataTable(list);
    }

    /** 导出奖品发放日志，条件与列表一致，手机号同样脱敏 */
    @PreAuthorize("@ss.hasPermi('kcon:prizegrant:export')")
    @Log(title = "奖品发放日志导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KconBenefitGrant query)
    {
        query.setGrantType(KconBenefitGrant.TYPE_PRIZE);
        List<KconPrizeGrantExportVo> list = grantService.selectPrizeGrantExportList(query);
        ExcelUtil<KconPrizeGrantExportVo> util = new ExcelUtil<>(KconPrizeGrantExportVo.class);
        util.exportExcel(response, list, "奖品发放日志");
    }
}
