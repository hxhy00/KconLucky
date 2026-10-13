package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.domain.KconWinner;

import java.util.List;
import java.util.Map;

/**
 * 抽奖业务 Service
 *
 * @author kcon
 */
public interface IKconDrawService
{
    /**
     * 大屏状态：当前场次 + 统计 + 当前应抽档位（接口文档 3.1）
     */
    Map<String, Object> screenStatus();

    /**
     * 切换场次（接口文档 3.2）
     */
    void switchSession(String sessionCode);

    /**
     * 抽一个（接口文档 3.3，核心）
     *
     * @param sessionCode 场次 AM/PM
     * @param prizeId     奖品档位 ID（可空，空则自动取当前应抽档位）
     * @param operator    操作人
     */
    Map<String, Object> draw(String sessionCode, Long prizeId, String operator);

    /**
     * 作废重抽（接口文档 3.4）：状态置 2，号码自动回池
     */
    void voidWinner(Long winnerId, String operator);

    /**
     * 计算抽奖池端口号（已签到 ∩ 未中奖；下午场再剔除上午场已中奖）
     */
    List<Integer> calcPool(String sessionCode);

    /**
     * 奖品档位 CRUD
     */
    List<KconPrize> selectPrizeList(KconPrize query);

    KconPrize selectPrizeById(Long id);

    int insertPrize(KconPrize prize);

    int updatePrize(KconPrize prize);

    int deletePrizeById(Long id);

    /**
     * 中奖记录
     */
    List<KconWinner> selectWinnerList(KconWinner query);

    KconWinner selectWinnerById(Long id);

    /**
     * 标记已领取
     */
    void receive(Long winnerId, String operator);
}
