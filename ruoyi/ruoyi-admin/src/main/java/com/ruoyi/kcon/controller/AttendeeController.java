package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.domain.KconSignSheetVo;
import com.ruoyi.kcon.service.IKconAttendeeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 参会人名单管理端
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/attendee")
public class AttendeeController extends BaseController
{
    @Autowired
    private IKconAttendeeService attendeeService;

    /** 名单列表（分页 + 筛选） */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:list')")
    @GetMapping("/list")
    public TableDataInfo list(KconAttendee query)
    {
        startPage();
        List<KconAttendee> list = attendeeService.selectAttendeeList(query);
        return getDataTable(list);
    }

    /** 详情 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return success(attendeeService.selectAttendeeById(id));
    }

    /** 新增 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:add')")
    @Log(title = "参会人名单", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public AjaxResult add(@Validated @RequestBody KconAttendee attendee)
    {
        KconAttendee exist = attendeeService.selectByPhone(attendee.getPhone());
        if (exist != null)
        {
            return error("手机号已存在，请勿重复添加");
        }
        attendee.setCreateBy(getUsername());
        attendeeService.insertAttendee(attendee);
        // 回传带自增主键的实体：前端拿到 id 后可以立刻把这一行乐观插入列表，
        // 不用等下一轮轮询才看到自己刚新增的人
        return success(attendee);
    }

    /** 编辑（用于补录公司/邮箱/参加次数/职务） */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:edit')")
    @Log(title = "参会人名单", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody KconAttendee attendee)
    {
        // 手机号唯一性校验（排除自身）
        KconAttendee exist = attendeeService.selectByPhone(attendee.getPhone());
        if (exist != null && !exist.getId().equals(attendee.getId()))
        {
            return error("手机号已被其他参会人使用");
        }
        attendee.setUpdateBy(getUsername());
        return toAjax(attendeeService.updateAttendee(attendee));
    }

    /** 删除 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:remove')")
    @Log(title = "参会人名单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(attendeeService.deleteAttendeeByIds(ids));
    }

    /**
     * 导入名单
     * POST /admin/attendee/import?mode=append|replace
     */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:import')")
    @Log(title = "名单导入", businessType = BusinessType.IMPORT)
    @RepeatSubmit
    @PostMapping("/import")
    public AjaxResult importData(@RequestParam("file") MultipartFile file,
                                 @RequestParam(defaultValue = "append") String mode)
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择文件");
        }
        String name = file.getOriginalFilename();
        try (InputStream is = file.getInputStream())
        {
            Map<String, Object> stat = attendeeService.importAttendees(is, name, mode, getUsername());
            String msg = String.format("导入完成：新增 %s，更新 %s，跳过 %s（有效名单共 %s 人）",
                    stat.get("inserted"), stat.get("updated"), stat.get("skipped"), stat.get("total"));
            return AjaxResult.success(msg, stat);
        }
        catch (ServiceException e)
        {
            return error(e.getMessage());
        }
        catch (IOException e)
        {
            return error("文件读取失败：" + e.getMessage());
        }
    }

    /** 下载导入模板（含 8 列表头） */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:import')")
    @Log(title = "名单模板下载", businessType = BusinessType.EXPORT)
    @PostMapping("/template")
    public void template(HttpServletResponse response) throws IOException
    {
        ExcelUtil<KconAttendee> util = new ExcelUtil<>(KconAttendee.class);
        // 模板排除「端口号」列：导入时不解析该列，端口号在签到时才随机分配，
        // 留着只会误导用户以为可以预填（2026-10-15 测试报告问题 5）。
        util.hideColumn("portNo");
        util.exportExcel(response, new ArrayList<>(), "参会名单导入模板");
    }

    /** 导出全部名单明细 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:export')")
    @Log(title = "名单导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KconAttendee query)
    {
        List<KconAttendee> list = attendeeService.selectAttendeeList(query);
        ExcelUtil<KconAttendee> util = new ExcelUtil<>(KconAttendee.class);
        util.exportExcel(response, list, "参会名单");
    }

    /** 导出统一签到表（打印打钩用） */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:export')")
    @Log(title = "签到表导出", businessType = BusinessType.EXPORT)
    @PostMapping("/signsheet")
    public void signSheet(HttpServletResponse response)
    {
        // 需求 FR-25.1：姓名 / 手机号 / 渠道标识 / 票种 / 打钩列，按姓名排序。
        // 不复用 KconAttendee 的通用导出列（含公司/邮箱/端口号等 9 列，缺打钩列，打印宽度也被撑开）
        List<KconAttendee> rows = attendeeService.selectSignSheet();
        List<KconSignSheetVo> list = new ArrayList<>(rows.size());
        for (KconAttendee a : rows)
        {
            list.add(new KconSignSheetVo(a.getName(), a.getPhone(), a.getChannel(), a.getTicketType()));
        }
        ExcelUtil<KconSignSheetVo> util = new ExcelUtil<>(KconSignSheetVo.class);
        util.exportExcel(response, list, "统一签到表");
    }

    /** 看板统计 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(attendeeService.dashboardStat());
    }
}
