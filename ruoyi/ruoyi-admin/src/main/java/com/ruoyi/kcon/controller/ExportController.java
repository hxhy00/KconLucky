package com.ruoyi.kcon.controller;

import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPortPoolMapper;
import com.ruoyi.kcon.mapper.KconPrizeMapper;
import com.ruoyi.kcon.mapper.KconSessionMapper;
import com.ruoyi.kcon.mapper.KconVerifyLogMapper;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.kcon.domain.KconCheckinExportVo;
import com.ruoyi.kcon.service.SensitiveOperationGuard;
import com.ruoyi.kcon.util.QrCodeUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据导出与清场
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/data")
public class ExportController extends BaseController
{
    @Autowired
    private KconCheckinMapper checkinMapper;

    @Autowired
    private KconAttendeeMapper attendeeMapper;

    @Autowired
    private KconWinnerMapper winnerMapper;

    @Autowired
    private KconPortPoolMapper portPoolMapper;

    @Autowired
    private KconPrizeMapper prizeMapper;

    @Autowired
    private KconVerifyLogMapper verifyLogMapper;

    @Autowired
    private KconSessionMapper sessionMapper;

    @Autowired
    private SensitiveOperationGuard sensitiveOperationGuard;

    /** 导出签到全量明细 */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:export')")
    @Log(title = "签到明细导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response)
    {
        List<Map<String, Object>> rows = checkinMapper.selectExportList();
        // ExcelUtil 靠 @Excel 注解定义表头，无法直接导出 Map（Map 是接口，取父类字段会 NPE），
        // 故把查询结果的中文键映射到导出 VO
        List<KconCheckinExportVo> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows)
        {
            KconCheckinExportVo vo = new KconCheckinExportVo();
            vo.setName(str(row.get("姓名")));
            vo.setPhone(str(row.get("手机号")));
            vo.setChannel(str(row.get("渠道标识")));
            vo.setTicketType(str(row.get("票种")));
            vo.setCompany(str(row.get("公司")));
            vo.setEmail(str(row.get("邮箱")));
            vo.setAttendCount(str(row.get("第几次参加KCon")));
            vo.setPosition(str(row.get("职务/年级")));
            vo.setPortNo(str(row.get("端口号")));
            vo.setCheckinTime(str(row.get("签到时间")));
            vo.setWinnerFlag(str(row.get("是否中奖")));
            vo.setTierName(str(row.get("奖项")));
            vo.setReceiveFlag(str(row.get("是否已领取")));
            list.add(vo);
        }
        ExcelUtil<KconCheckinExportVo> util = new ExcelUtil<>(KconCheckinExportVo.class);
        util.exportExcel(response, list, "KCon2026签到明细");
    }

    /** 导出列取值：null 视为空串，避免 Excel 里出现 "null" 字样 */
    private String str(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 签到页 URL（前端据此本地渲染二维码；离线现场不依赖外部服务）
     */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:list')")
    @GetMapping("/qrcode")
    public AjaxResult qrcode()
    {
        return success(checkinUrl());
    }

    /**
     * 直接返回签到二维码 PNG（离线可用，不依赖外部图片服务）
     * 依赖 ZXing：见 ruoyi-common/pom.xml
     */
    @PreAuthorize("@ss.hasPermi('kcon:attendee:list')")
    @GetMapping("/qrcode.png")
    public void qrcodePng(HttpServletResponse response) throws IOException
    {
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "inline; filename=kcon-checkin-qrcode.png");
        QrCodeUtil.writePng(checkinUrl(), 400, 400, response.getOutputStream());
    }

    /**
     * 观众签到页地址（扫码入口）。
     * 根路径 / 为入口分流页，签到页固定部署在 /sign/。
     */
    private String checkinUrl()
    {
        String lanIp = System.getenv().getOrDefault("LAN_IP", "127.0.0.1");
        return "http://" + lanIp + "/sign/";
    }

    /**
     * 清场：清空名单/签到/中奖/核验日志，端口号池归零，奖品已抽数归零。
     * 正式开场前使用。
     *
     * 双重校验（2026-10-21 起）：
     *   1. @PreAuthorize 要求 kcon:data:reset 权限 —— 该权限只授予超管角色，
     *      工作人员（kcon_staff）即便猜到接口地址也进不来；
     *   2. 再要求输入**当前登录人本人的密码**。第一层挡住没权限的人，
     *      第二层挡住「电脑没锁屏、别人顺手点一下」这类情况，并让每次清场
     *      都能在操作日志里对应到具体执行人。
     *
     * 注意：这里刻意不加 @Transactional。TRUNCATE TABLE 属于 DDL，在 MySQL 中会触发
     * 隐式提交且不可回滚，标注事务只会造成「有原子性」的错觉。若 TRUNCATE 之后的
     * 语句失败，会出现「名单已清空但端口号池未归零」的半清场状态，导致签到报
     * 「端口号已耗尽」、抽奖报「该场次奖项已抽完」。
     * 因此把可回滚的 UPDATE 放在 TRUNCATE 之前：一旦失败，数据仍在。
     */
    @PreAuthorize("@ss.hasPermi('kcon:data:reset')")
    @Log(title = "数据清场", businessType = BusinessType.CLEAN)
    @RepeatSubmit
    @PostMapping("/reset")
    public AjaxResult reset(@RequestBody Map<String, String> body)
    {
        // 密码校验放在所有写操作之前：不通过时数据库一个字节都不动
        sensitiveOperationGuard.verifyCurrentUserPassword(
                body == null ? null : body.get("password"), "数据清场");

        // 可回滚的部分先做
        sessionMapper.deactivateAll();     // 场次状态复位为未开始（测试报告问题 4）
        portPoolMapper.releaseAll();
        prizeMapper.resetDrawnCount();
        // 不可回滚的部分后做
        checkinMapper.truncateCheckin();
        winnerMapper.truncateWinner();
        verifyLogMapper.truncateLog();
        attendeeMapper.truncateAttendee();
        return success("清场完成：名单、签到、中奖、核验日志已清空，端口号池与奖品已抽数已归零，场次状态已复位");
    }
}
