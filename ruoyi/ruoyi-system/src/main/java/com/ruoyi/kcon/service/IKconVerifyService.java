package com.ruoyi.kcon.service;

import com.ruoyi.kcon.domain.KconVerifyLog;
import com.ruoyi.kcon.domain.KconVerifyLogExportVo;

import java.util.List;
import java.util.Map;

/**
 * 兑奖核验 Service
 *
 * @author kcon
 */
public interface IKconVerifyService
{
    /**
     * 核验（接口文档 4.1）：输入端口号 + 手机号
     *   VALID          核验通过（该号中奖且手机号匹配，且未领取）
     *   NOT_ISSUED     该端口号无中奖记录
     *   PHONE_MISMATCH 手机号与持号人不符
     *   RECEIVED       已领取过
     *
     * 纯判定，不写核验日志；发放追溯统一走 kcon_benefit_grant。
     *
     * @param portNo  端口号
     * @param phone   输入的手机号（完整 11 位）
     * @param operator 操作人
     */
    Map<String, Object> verify(Integer portNo, String phone, String operator);

    /**
     * 标记已领取
     */
    void receive(Integer portNo, String phone, String operator);

    /**
     * 兑奖核验日志条件查询（管理端审计用）。
     * 返回前统一做手机号兜底掩码，明文不出后端。
     */
    List<KconVerifyLog> selectVerifyLogList(KconVerifyLog query);

    /**
     * 兑奖核验日志导出数据，复用同一套查询条件与脱敏逻辑。
     */
    List<KconVerifyLogExportVo> selectVerifyLogExportList(KconVerifyLog query);
}
