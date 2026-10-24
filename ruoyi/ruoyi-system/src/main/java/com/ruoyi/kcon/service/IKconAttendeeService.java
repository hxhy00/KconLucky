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

    /**
     * 名单导出取数：与列表同一套筛选条件，但保留完整手机号
     * （导出是受权限控制的独立动作，列表接口才做明文脱敏）。
     */
    List<KconAttendee> selectAttendeeExportList(KconAttendee query);

    KconAttendee selectAttendeeById(Long id);

    /**
     * 按手机号查询（唯一性校验用）
     */
    KconAttendee selectByPhone(String phone);

    int insertAttendee(KconAttendee attendee);

    int updateAttendee(KconAttendee attendee);

    /**
     * 恢复某人的权益类型为「跟随渠道来源映射」：清掉人工修改标记，并按映射回填权益。
     * 用于人工误改后撤销。只影响权益类型，不改参与抽奖。
     */
    int clearManualTicketType(Long id);

    int deleteAttendeeByIds(Long[] ids);

    /**
     * Excel/CSV 导入。
     * 按表头名识别列、顺序不限；只认 6 列标准模板（姓名/手机号/渠道来源/公司/职位/邮箱），
     * 必填 3 列（姓名/手机号/渠道来源），选填 3 列（公司/职位/邮箱），多余列忽略。
     * <p>旧模板（含「渠道标识」「票种」「是否参与抽奖」等表头）<b>不再兼容</b>，
     * 会直接报「无法识别表头」或「缺少必填列」。
     *
     * <p>权益类型与参与抽奖不读文件，由「渠道来源映射」决定；渠道来源必须已配置映射，
     * 否则该行计为阻塞问题且正式导入整批拒绝（映射表即允许导入的渠道白名单）。
     * 预检与正式导入共用同一解析逻辑，但整批拒绝只在导入时抛出，预检仍需返回完整报告。
     *
     * @param is        文件流
     * @param fileName  原文件名（判断 xls/xlsx/csv）
     * @param mode      append=按手机号追加更新；replace=先清空再导入
     * @param operator  操作人
     *
     * @return 统计：total / inserted / updated / skipped
     */
    Map<String, Object> importAttendees(InputStream is, String fileName, String mode, String operator);

    /**
     * 导入预检：只解析、不落库。
     * 与 {@link #importAttendees} 共用同一套解析逻辑，保证预检结论与实际导入一致。
     *
     * @return level(BLOCK/WARN/OK) / totalRows / willInsert / willUpdate / willSkip /
     *         blockers / warnings / channelStats / unmappedChannels / skipDetails
     */
    Map<String, Object> previewImport(InputStream is, String fileName, String mode);

    /**
     * 看板统计
     */
    Map<String, Object> dashboardStat();

    /**
     * 统一签到表数据（按姓名排序）
     */
    List<KconAttendee> selectSignSheet();
}
