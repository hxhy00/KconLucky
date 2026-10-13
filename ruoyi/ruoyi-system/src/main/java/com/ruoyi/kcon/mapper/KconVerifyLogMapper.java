package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconVerifyLog;

import java.util.List;

/**
 * 兑奖核验日志 Mapper
 *
 * @author kcon
 */
public interface KconVerifyLogMapper
{
    int insertLog(KconVerifyLog log);

    int truncateLog();

    /**
     * 条件查询核验日志（管理端审计用）
     *   端口号 portNo      精确匹配
     *   核验结果 result    精确匹配（VALID/NOT_ISSUED/PHONE_MISMATCH/RECEIVED）
     *   时间范围           params.beginTime / params.endTime
     * 按操作时间倒序（同时间按主键倒序，保证分页排序稳定）。
     */
    List<KconVerifyLog> selectVerifyLogList(KconVerifyLog query);
}
