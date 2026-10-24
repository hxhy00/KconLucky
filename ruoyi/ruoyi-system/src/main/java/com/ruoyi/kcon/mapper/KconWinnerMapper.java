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

    /**
     * 最近一条有效（未作废）中奖记录。
     * 大屏作废接口免登录，以此为基准把可作废范围限制为最新一条。
     */
    KconWinner selectLatestValidWinner();

    /**
     * 指定场次最近一条有效（未作废）中奖记录。
     * 「撤回最近一档」用它定位该场次最后一次抽出的整批。
     */
    KconWinner selectLatestValidWinnerBySession(@Param("sessionCode") String sessionCode);

    /**
     * 同一批抽出的中奖记录（同一档位 + 同一中奖时间）。
     * 一次点击会抽出整档的人，大屏据此还原整批并逐个揭晓。
     *
     * @param prizeId 档位 id
     * @param winTime 中奖时间（yyyy-MM-dd HH:mm:ss，整批由服务端写入同一个值）
     */
    List<KconWinner> selectBatch(@Param("prizeId") Long prizeId, @Param("winTime") String winTime);

    /**
     * 某档位全部有效（未作废）中奖记录，按中奖时间升序（先抽的在前）。
     * 控制台「中奖记录（按场次 × 档位）」分组展示用。
     */
    List<KconWinner> selectValidByPrize(@Param("prizeId") Long prizeId);

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
