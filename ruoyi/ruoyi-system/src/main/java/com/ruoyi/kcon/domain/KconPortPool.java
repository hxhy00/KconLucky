package com.ruoyi.kcon.domain;

import java.util.Date;

/**
 * 端口号池对象 kcon_port_pool
 *
 * @author kcon
 */
public class KconPortPool
{
    /** 端口号 1-65535 */
    private Integer portNo;

    /** 0=空闲 1=已分配 */
    private Integer status;

    /** 分配给的手机号 */
    private String phone;

    /** 分配时间 */
    private Date usedTime;

    public Integer getPortNo() { return portNo; }
    public void setPortNo(Integer portNo) { this.portNo = portNo; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Date getUsedTime() { return usedTime; }
    public void setUsedTime(Date usedTime) { this.usedTime = usedTime; }
}
