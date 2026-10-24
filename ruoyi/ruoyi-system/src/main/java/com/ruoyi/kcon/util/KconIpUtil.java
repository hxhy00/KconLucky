package com.ruoyi.kcon.util;

import com.ruoyi.common.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 限流用的客户端 IP 解析。
 *
 * 现场部署在 nginx 之后，nginx 会覆盖写入 {@code X-Real-IP}；
 * 而 {@code X-Forwarded-For} 允许客户端自带，攻击者每次换一个值就能绕过按 IP 限流。
 * 因此这里只信 {@code X-Real-IP}，取不到再退回 {@code remoteAddr}，刻意忽略 XFF。
 *
 * 注意：仅用于限流 key，不改变审计日志的 IP 口径（审计仍走 IpUtils）。
 *
 * @author kcon
 */
public final class KconIpUtil
{
    /** nginx 覆盖写入的真实客户端 IP 头 */
    public static final String HEADER_REAL_IP = "X-Real-IP";

    private KconIpUtil()
    {
    }

    /** 当前请求的客户端 IP（无请求上下文时返回 unknown） */
    public static String resolveClientIp()
    {
        return resolveClientIp(ServletUtils.getRequest());
    }

    /** 从请求解析客户端 IP：X-Real-IP 优先，回退 remoteAddr */
    public static String resolveClientIp(HttpServletRequest request)
    {
        if (request == null)
        {
            return "unknown";
        }
        String ip = request.getHeader(HEADER_REAL_IP);
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip.trim()))
        {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.trim().isEmpty())
        {
            return "unknown";
        }
        String trimmed = ip.trim();
        return "0:0:0:0:0:0:0:1".equals(trimmed) ? "127.0.0.1" : trimmed;
    }
}
