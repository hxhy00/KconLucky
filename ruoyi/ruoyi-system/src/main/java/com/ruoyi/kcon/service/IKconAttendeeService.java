package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconAttendee;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 参会人名单管理 Service
 *
 * @author kcon
 */
public interface IKconAttendeeService
{
    List<KconAttendee> selectAttendeeList(KconAttendee query);

    KconAttendee selectAttendeeById(Long id);

    /**
     * 按手机号查询（唯一性校验用）
     */
    KconAttendee selectByPhone(String phone);

    int insertAttendee(KconAttendee attendee);

    int updateAttendee(KconAttendee attendee);

    int deleteAttendeeByIds(Long[] ids);

    /**
     * Excel/CSV 导入。
     * 按表头名识别列、顺序不限；必填 5 列（姓名/手机号/渠道标识/票种/是否参与抽奖，spec 006）；
     * 选填 4 列（公司/邮箱/第几次参加 KCon/职务·年级）；多余列忽略。
     *
     * @param is        文件流
     * @param fileName  原文件名（判断 xls/xlsx/csv）
     * @param mode      append=按手机号追加更新；replace=先清空再导入
     * @param operator  操作人
     * @return 统计：total / inserted / updated / skipped
     */
    Map<String, Object> importAttendees(InputStream is, String fileName, String mode, String operator);

    /**
     * 看板统计
     */
    Map<String, Object> dashboardStat();

    /**
     * 统一签到表数据（按姓名排序）
     */
    List<KconAttendee> selectSignSheet();
}
