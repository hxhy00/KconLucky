package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconBenefitGrantExportVo;
import com.ruoyi.kcon.domain.KconPrizeGrantExportVo;

import java.util.List;

/**
 * 参会人发放记录（发放日志）Service
 *
 * @author kcon
 */
public interface IKconBenefitGrantService
{
    /**
     * 权益发放：把某参会人的权益（ticket_type 文本）登记为已发放。
     *
     * 业务规则：
     *   - 必须已签到（未签到先引导去签到台，防冒领）
     *   - 权益类型为空的人无可发放内容，拒绝
     *   - 一人一条：DB 层 uk_attendee_type 唯一键兜底，重复发放抛错
     *   - 不可撤销：不提供删除/回退接口
     *
     * @param attendeeId 参会人ID
     * @param operator   操作人
     */
    void grantBenefit(Long attendeeId, String operator);

    /**
     * 奖品发放留痕：兑奖确认成功后调用，写一条 PRIZE 记录。
     * 仅留痕，不影响发放结果；写失败打日志不抛错（主流程 kcon_winner 状态已落库）。
     *
     * @param portNo    中奖端口号
     * @param winner    中奖记录（取姓名/手机号/场次/奖项快照）
     * @param operator  操作人
     */
    void recordPrizeGrant(Integer portNo, com.ruoyi.kcon.domain.KconWinner winner, String operator);

    /**
     * 发放日志条件查询（分页/导出共用）。
     * 调用方负责在 query 里固定 grantType：权益发放页传 BENEFIT，奖品发放页传 PRIZE。
     * 返回前统一做手机号兜底掩码，明文不出后端。
     */
    List<KconBenefitGrant> selectGrantList(KconBenefitGrant query);

    /**
     * 权益发放日志导出数据（query.grantType 由调用方固定为 BENEFIT）。
     */
    List<KconBenefitGrantExportVo> selectBenefitGrantExportList(KconBenefitGrant query);

    /**
     * 奖品发放日志导出数据（query.grantType 由调用方固定为 PRIZE）。
     */
    List<KconPrizeGrantExportVo> selectPrizeGrantExportList(KconBenefitGrant query);
}
