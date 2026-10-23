package com.ruoyi.kcon.mapper;

import com.ruoyi.kcon.domain.KconBenefitGrant;

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

    /** 按参会人 + 类型判断是否已发放（发放按钮置灰与后端预检用） */
    int countByAttendeeAndType(KconBenefitGrant query);

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

    int truncateGrant();
}
