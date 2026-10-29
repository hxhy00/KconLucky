package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconBenefitGrant;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 参会人发放记录（发放日志）Mapper
 *
 * @author kcon
 */
public interface KconBenefitGrantMapper
{
    /**
     * 写入一条发放记录。
     * 权益发放（BENEFIT）依赖 uk_attendee_type 唯一键防重：同一参会人重复插入会抛
     * DuplicateKeyException，由 Service 捕获转为「已发放」提示。
     */
    int insertGrant(KconBenefitGrant grant);

    /**
     * 更新已有的 PRIZE 发放记录（按 attendee_id + grant_type='PRIZE'）。
     * 中奖→作废→重抽→再领奖时，同一参会人的第二条 PRIZE 日志会撞
     * uk_attendee_type 唯一键；此时改为更新旧记录（刷新端口号/奖项/场次/操作人），
     * 保证发放追溯不断档。BENEFIT 不走此方法。
     */
    int updatePrizeGrant(KconBenefitGrant grant);

    /**
     * 条件查询发放日志（分页/导出共用）
     *   grantType           BENEFIT/PRIZE
     *   portNo              精确匹配
     *   name                模糊匹配
     *   params.beginTime    发放时间起
     *   params.endTime      发放时间止
     * 按发放时间倒序（同时间按主键倒序，保证分页排序稳定）。
     */
    List<KconBenefitGrant> selectGrantList(KconBenefitGrant query);

    /**
     * 导出用：同一套查询条件，但条数上限下推到 SQL（limit），
     * 避免先全量查回再 subList 造成无谓的内存与网络开销。
     */
    List<KconBenefitGrant> selectGrantListLimited(@Param("query") KconBenefitGrant query,
                                                  @Param("limit") int limit);

    int truncateGrant();
}
