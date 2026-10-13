package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconPrize;

import java.util.List;

/**
 * 奖品档位 Mapper
 *
 * @author kcon
 */
public interface KconPrizeMapper
{
    List<KconPrize> selectPrizeList(KconPrize query);

    KconPrize selectPrizeById(Long id);

    /**
     * 取该场次当前应抽的档位（draw_order 最小且未抽满；若都抽满则返回 null）
     */
    KconPrize selectCurrentTier(String sessionCode);

    int insertPrize(KconPrize prize);

    int updatePrize(KconPrize prize);

    int deletePrizeById(Long id);

    /**
     * 已抽数 +1（带条件：不超过 quantity，返回 1 表示成功）
     */
    int incrementDrawnCount(Long id);

    /**
     * 已抽数 -1（作废回退）
     */
    int decrementDrawnCount(Long id);

    int resetDrawnCount();
}
