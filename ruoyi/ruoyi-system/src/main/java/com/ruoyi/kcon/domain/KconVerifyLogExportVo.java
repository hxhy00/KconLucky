package com.ruoyi.kcon.domain;

import com.ruoyi.common.annotation.Excel;

/**
 * 兑奖核验日志导出对象
 *
 * ExcelUtil 依赖类上的 @Excel 注解来定义表头与列顺序，父类字段取不到注解，
 * 故用独立 VO 承载导出列（与 KconCheckinExportVo 同一做法）。
 *
 * 全部为 String 类型：导出列已经过脱敏与文案转换，不再需要原始类型。
 *
 * @author kcon
 */
public class KconVerifyLogExportVo
{
    @Excel(name = "端口号")
    private String portNo;

    @Excel(name = "输入手机号")
    private String inputPhoneMask;

    @Excel(name = "核验结果")
    private String resultText;

    @Excel(name = "操作人")
    private String operator;

    @Excel(name = "操作时间")
    private String createTime;

    public String getPortNo()
    {
        return portNo;
    }

    public void setPortNo(String portNo)
    {
        this.portNo = portNo;
    }

    public String getInputPhoneMask()
    {
        return inputPhoneMask;
    }

    public void setInputPhoneMask(String inputPhoneMask)
    {
        this.inputPhoneMask = inputPhoneMask;
    }

    public String getResultText()
    {
        return resultText;
    }

    public void setResultText(String resultText)
    {
        this.resultText = resultText;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public String getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(String createTime)
    {
        this.createTime = createTime;
    }
}
