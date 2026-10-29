package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconCheckin;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 签到记录 Mapper
 *
 * @author kcon
 */
public interface KconCheckinMapper
{
    /**
     * 按手机号查签到记录
     */
    KconCheckin selectByPhone(String phone);

    /**
     * 按参会人 ID 查签到记录（权益发放前校验「必须已签到」用）
     */
    KconCheckin selectByAttendeeId(Long attendeeId);

    /**
     * 按端口号查签到记录
     */
    KconCheckin selectByPortNo(Integer portNo);

    /**
     * 新增（回填 id）
     */
    int insertCheckin(KconCheckin checkin);

    /**
     * 今日已签到数
     */
    int countToday();

    /**
     * 签到总条数（不限日期）。
     * 用于「名单替换模式」前的拦截：只要有过签到记录，就不允许替换名单，
     * 否则已签到人员会变成孤儿记录（端口号仍在抽奖池里，但查不到持有人）。
     */
    int countAll();

    /**
     * 统计指定参会人中有多少已签到（批量删除前的拦截，FR-13.3）
     *
     * @param attendeeIds 参会人 ID 数组
     * @return 已签到的参会人数量
     */
    int countSignedByAttendeeIds(@Param("attendeeIds") Long[] attendeeIds);

    /**
     * 查询指定参会人中已签到的姓名（用于删除时明确提示是谁）
     *
     * @param attendeeIds 参会人 ID 数组
     * @return 已签到人员的姓名列表
     */
    List<String> selectSignedNamesByAttendeeIds(@Param("attendeeIds") Long[] attendeeIds);

    /**
     * 全部签到记录（抽奖池计算用，千人级数据量）
     */
    List<KconCheckin> selectAllPorts();

    /**
     * 导出用：签到全量明细（联表名单 + 中奖信息）。
     * 条数上限下推到 SQL（limit），与发放/核验日志导出同一口径。
     */
    List<java.util.Map<String, Object>> selectExportList(@org.apache.ibatis.annotations.Param("limit") int limit);

    /**
     * 清空签到记录
     */
    int truncateCheckin();
}
