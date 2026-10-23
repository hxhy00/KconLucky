package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconBenefitGrantExportVo;
import com.ruoyi.kcon.service.IKconBenefitGrantService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 参会人权益发放管理端
 *
 * 权益发放：名单页「发放」按钮调用，一人一条，防重复领取；
 * 权益发放日志：只查 BENEFIT 类型，奖品发放另见 PrizeGrantController。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/benefit")
public class BenefitGrantController extends BaseController
{
    @Autowired
    private IKconBenefitGrantService grantService;

    /**
     * 权益发放
     *
     * POST /admin/benefit/grant/{attendeeId}
     *
     * 必须已签到；一人一条（DB 唯一键兜底）；发放后不可撤销。
     */
    @PreAuthorize("@ss.hasPermi('kcon:benefit:grant')")
    @Log(title = "权益发放", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/grant/{attendeeId}")
    public AjaxResult grant(@PathVariable Long attendeeId)
    {
        grantService.grantBenefit(attendeeId, getUsername());
        return success("发放成功");
    }

    /**
     * 权益发放日志列表（分页 + 筛选）
     *
     * GET /admin/benefit/list
     *   portNo              端口号，精确匹配
     *   name                姓名，模糊匹配
     *   params.beginTime    发放时间起 yyyy-MM-dd HH:mm:ss
     *   params.endTime      发放时间止 yyyy-MM-dd HH:mm:ss
     *
     * 类型固定为 BENEFIT，请求参数无法改写——权益与奖品是两个页面，不共用查询。
     */
    @PreAuthorize("@ss.hasPermi('kcon:benefit:list')")
    // 不加 @Log：列表查询频繁翻页/筛选，无追溯价值
    @GetMapping("/list")
    public TableDataInfo list(KconBenefitGrant query)
    {
        query.setGrantType(KconBenefitGrant.TYPE_BENEFIT);
        startPage();
        List<KconBenefitGrant> list = grantService.selectGrantList(query);
        return getDataTable(list);
    }

    /** 导出权益发放日志，条件与列表一致，手机号同样脱敏 */
    @PreAuthorize("@ss.hasPermi('kcon:benefit:export')")
    @Log(title = "权益发放日志导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KconBenefitGrant query)
    {
        query.setGrantType(KconBenefitGrant.TYPE_BENEFIT);
        List<KconBenefitGrantExportVo> list = grantService.selectBenefitGrantExportList(query);
        ExcelUtil<KconBenefitGrantExportVo> util = new ExcelUtil<>(KconBenefitGrantExportVo.class);
        util.exportExcel(response, list, "权益发放日志");
    }
}
