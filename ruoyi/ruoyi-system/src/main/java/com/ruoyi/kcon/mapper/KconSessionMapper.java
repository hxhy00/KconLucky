package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconSession;

import java.util.List;

/**
 * 抽奖场次 Mapper
 *
 * @author kcon
 */
public interface KconSessionMapper
{
    List<KconSession> selectSessionList();

    KconSession selectByCode(String code);

    /**
     * 设为进行中，其余未结束的场次置为未开始（已结束场次保持结束态）
     */
    int activate(String code);

    /**
     * 所有场次状态复位为未开始（数据清场时调用）
     */
    int deactivateAll();
}
