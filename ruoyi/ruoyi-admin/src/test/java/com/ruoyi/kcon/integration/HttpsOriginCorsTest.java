package com.ruoyi.kcon.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * HTTPS 外层反代下的 CORS 误判回归测试。
 *
 * HTTPS 在外层终止时，后端收到的是 http 请求而浏览器 Origin 是 https；
 * Spring 6 的 CORS 判定按 scheme/host/port 逐项比对，会把这种请求误判成
 * 跨域，被 CorsFilter 拒成 403 "Invalid CORS request"（HTTP 部署不会触发，
 * 所以老服务器一直正常，HTTPS 化后才暴露）。
 *
 * 修复：server.forward-headers-strategy=framework —— Spring Boot 据此注册
 * ForwardedHeaderFilter，把请求的 scheme/host 还原为外层的 https/域名，
 * 同源判定自然通过。这里用 standalone MockMvc 挂同样的过滤器组合复现该场景，
 * 不需要外部 MySQL/Redis，参与常规 `mvn test`。
 *
 * @author kcon
 */
class HttpsOriginCorsTest
{
    private static final String DOMAIN = "kcon-luck.knownsec.com";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        // 与生产一致的过滤器组合：ForwardedHeaderFilter（forward-headers-strategy=framework
        // 注册的即是它）+ ResourcesConfig 的 CorsFilter（cors.allowed-origins 为空 = 白名单空）。
        // ForwardedHeaderFilter 必须在 CorsFilter 之前，还原 scheme 后 CORS 判定才拿得到真实协议
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration());
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyLoginController())
                .addFilters(new ForwardedHeaderFilter(), new CorsFilter(source))
                .build();
    }

    @Test
    @DisplayName("HTTPS 反代：带 X-Forwarded-* 时 https Origin 不再被拒 403")
    void httpsOriginWithForwardedHeadersPasses() throws Exception
    {
        mockMvc.perform(post("/login")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", DOMAIN)
                .header("Origin", "https://" + DOMAIN)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无 X-Forwarded-*：https Origin 仍被拒 403（复现线上问题）")
    void httpsOriginWithoutForwardedHeadersRejected() throws Exception
    {
        mockMvc.perform(post("/login")
                .header("Origin", "https://" + DOMAIN)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP 部署：同源 http Origin 不判跨域（老服务器一直正常的原因）")
    void httpSameOriginPasses() throws Exception
    {
        mockMvc.perform(post("/login")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "123.57.9.62")
                .header("Origin", "http://123.57.9.62")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    /** 只用于验证过滤器链的放行/拒绝，不实现真实登录 */
    @RestController
    static class DummyLoginController
    {
        @PostMapping("/login")
        public java.util.Map<String, Object> login()
        {
            return java.util.Map.of("code", 200);
        }
    }
}
