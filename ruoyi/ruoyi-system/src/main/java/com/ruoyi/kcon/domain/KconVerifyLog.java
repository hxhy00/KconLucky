package com.ruoyi.kcon.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 兑奖核验日志对象 kcon_verify_log
 *
 * 手机号说明：本表为历史日志表，写入已下线；查询返回时统一做掩码兜底，
 * 明文不返回后端，前端与导出只用 inputPhoneMask。
 *
 * 继承 BaseEntity 是为了复用 RuoYi 标准的 params[beginTime] / params[endTime] 时间范围筛选；
 * 建表列 create_time 直接复用父类的 createTime 字段（本表没有 update_time / remark 等列）。
 *
 * @author kcon
 */
public class KconVerifyLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 核验结果常量 */
    public static final String RESULT_VALID = "VALID";
    public static final String RESULT_NOT_ISSUED = "NOT_ISSUED";
    public static final String RESULT_PHONE_MISMATCH = "PHONE_MISMATCH";
    public static final String RESULT_RECEIVED = "RECEIVED";

    private Long id;

    /** 被查询的端口号 */
    private Integer portNo;

    /** 输入的手机号（写入时已掩码存储；查询时置空，不出后端） */
    private String inputPhone;

    /** VALID / NOT_ISSUED / PHONE_MISMATCH / RECEIVED */
    private String result;

    /** 操作人 */
    private String operator;

    // ---- 以下为查询/展示用非持久化字段 ----

    /** 输入手机号掩码（138****0001），前端展示与导出用 */
    private String inputPhoneMask;

    /** 核验结果中文文案，导出用 */
    private String resultText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public String getInputPhone() { return inputPhone; }
    public void setInputPhone(String inputPhone) { this.inputPhone = inputPhone; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getInputPhoneMask() { return inputPhoneMask; }
    public void setInputPhoneMask(String inputPhoneMask) { this.inputPhoneMask = inputPhoneMask; }

    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
}
