package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconPortPool;

/**
 * 端口号池 Mapper
 *
 * @author kcon
 */
public interface KconPortPoolMapper
{
    /**
     * 统计空闲端口号数量
     */
    int countFree();

    /**
     * 随机取一个空闲端口号
     */
    Integer selectRandomFreePort();

    /**
     * 条件占用：仅当该号仍空闲时占用成功（返回 1）。
     * 这是并发安全的关键 —— UPDATE ... WHERE status = 0
     */
    int occupyPort(KconPortPool param);

    /**
     * 释放端口号（作废重抽等场景暂不使用，保留）
     */
    int releasePort(Integer portNo);

    /**
     * 全部释放（清场）
     */
    int releaseAll();
}
