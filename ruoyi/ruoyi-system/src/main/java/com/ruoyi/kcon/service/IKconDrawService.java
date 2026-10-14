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
     * 撤回最近一档：把指定场次最后一次抽出的整批全部作废，号码回池、档位已抽数回退。
     * 整批在同一事务内完成，要么全撤、要么全不撤。
     *
     * @param sessionCode 场次编码；为空时按全场最近一档处理
     * @return 实际作废的人数（0 表示该场次没有可撤回的记录）
     */
    int voidLastBatch(String sessionCode, String operator);

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
     * 全部有效中奖记录，按场次 × 档位分组。
     * 每个已配置的场次都会出现（含没配档位的场次，其 tiers 为空数组）；
     * 档位以该场次全部档位为骨架（含从未抽过的档位，winners 为空数组），
     * 按 draw_order 升序（与抽取顺序一致：三等 → 二等 → 一等），
     * 只统计未作废（status != 2）的记录。
     * 控制台「奖项进度与中奖记录」横栏展示用。
     */
    List<Map<String, Object>> winnerGroups();

    /**
     * 标记已领取
     */
    void receive(Long winnerId, String operator);
}
