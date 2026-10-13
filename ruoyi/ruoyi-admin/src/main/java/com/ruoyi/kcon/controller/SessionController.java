package com.ruoyi.kcon.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.kcon.domain.KconSession;
import com.ruoyi.kcon.mapper.KconSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场次管理端只读接口
 *
 * 需求 FR-16：奖品档位与中奖记录都要按场次维度筛选。
 * 此前前端把 AM/PM 写死在场次下拉里，与 kcon_session 表的实际场次名
 * （「上午场（极客装备轮）」等）不一致，临时加第三场时也选不到。
 * 这里把场次列表从库里取出来给前端用。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/session")
public class SessionController extends BaseController
{
    @Autowired
    private KconSessionMapper sessionMapper;

    /**
     * 全部场次（按 sort 排序）
     *
     * @return AjaxResult.data = [{code, name, sort, status}]
     */
    @PreAuthorize("@ss.hasPermi('kcon:session:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        List<KconSession> sessions = sessionMapper.selectSessionList();
        return AjaxResult.success(sessions);
    }
}