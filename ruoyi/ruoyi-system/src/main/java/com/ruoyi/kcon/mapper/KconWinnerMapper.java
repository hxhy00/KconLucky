package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconWinner;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 中奖记录 Mapper
 *
 * @author kcon
 */
public interface KconWinnerMapper
{
    List<KconWinner> selectWinnerList(KconWinner query);

    KconWinner selectWinnerById(Long id);

    /**
     * 按端口号查当前有效的中奖记录（status != 2）
     */
    KconWinner selectActiveByPortNo(Integer portNo);

    int insertWinner(KconWinner winner);

    /**
     * 状态流转（领取/作废），带条件避免重复操作
     */
    int updateStatus(KconWinner winner);

    /**
     * 已中奖人数（去重端口号，status != 2）。
     * sessionCode 传 null 表示全场统计。
     */
    int countWinners(@Param("sessionCode") String sessionCode);

    /**
     * 全部已中奖端口号（用于抽奖池排除）
     */
    List<Integer> selectAllWonPorts();

    /**
     * 某场次已中奖端口号（下午场剔除上午场用）
     */
    List<Integer> selectWonPortsBySession(String sessionCode);

    /**
     * 今日中奖统计（按场次）
     */
    List<Map<String, Object>> statBySession();

    int truncateWinner();
}
