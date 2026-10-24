package com.ruoyi.kcon.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 限流 IP 解析测试：只信 nginx 写入的 X-Real-IP，忽略可伪造的 X-Forwarded-For。
 */
class KconIpUtilTest
{
    @Test
    @DisplayName("优先使用 X-Real-IP")
    void prefersRealIpHeader()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Real-IP", "203.0.113.7");

        assertThat(KconIpUtil.resolveClientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("忽略 X-Forwarded-For，防止客户端伪造 IP 绕过限流")
    void ignoresForwardedFor()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(KconIpUtil.resolveClientIp(request)).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("X-Real-IP 缺失或为 unknown 时回退 remoteAddr")
    void fallsBackToRemoteAddr()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        assertThat(KconIpUtil.resolveClientIp(request)).isEqualTo("10.0.0.9");

        request.addHeader("X-Real-IP", " unknown ");
        assertThat(KconIpUtil.resolveClientIp(request)).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("无请求上下文时返回 unknown")
    void handlesNullRequest()
    {
        assertThat(KconIpUtil.resolveClientIp(null)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("IPv6 本机回环归一为 127.0.0.1")
    void normalizesIpv6Loopback()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        assertThat(KconIpUtil.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }
}
