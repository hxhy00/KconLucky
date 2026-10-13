package com.ruoyi.kcon.domain;

/**
 * 抽奖场次对象 kcon_session
 *
 * @author kcon
 */
public class KconSession
{
    private Long id;

    /** 场次编码 AM/PM */
    private String code;

    /** 场次名 */
    private String name;

    /** 0=未开始 1=进行中 2=已结束 */
    private Integer status;

    /** 排序 */
    private Integer sort;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
