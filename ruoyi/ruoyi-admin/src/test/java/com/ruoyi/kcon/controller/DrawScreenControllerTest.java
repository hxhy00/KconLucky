package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.kcon.service.IKconDrawService;

/**
 * 抽奖大屏匿名接口测试：只透传 screenStatus，绝不携带管理端专属统计字段。
 */
@ExtendWith(MockitoExtension.class)
class DrawScreenControllerTest
{
    @Mock
    private IKconDrawService drawService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        DrawScreenController controller = new DrawScreenController();
        ReflectionTestUtils.setField(controller, "drawService", drawService);
        // 匿名接口：刻意不 mock SecurityUtils
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /status：只返回 screenStatus 内容，不含 excludedCount/checkedInToday/winnerCount 等管理端字段")
    void statusOnlyExposesScreenStatusFields() throws Exception
    {
        when(drawService.screenStatus()).thenReturn(screenStatus());

        mockMvc.perform(get("/app/screen/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionCode").value("AM"))
                .andExpect(jsonPath("$.data.poolCount").value(88))
                .andExpect(jsonPath("$.data.currentTier.tierName").value("三等奖"))
                // 以下均为管理端字段，匿名大屏不得下发
                .andExpect(jsonPath("$.data.excludedCount").doesNotExist())
                .andExpect(jsonPath("$.data.checkedInToday").doesNotExist())
                .andExpect(jsonPath("$.data.winnerCount").doesNotExist())
                .andExpect(jsonPath("$.data.lastWinner").doesNotExist())
                .andExpect(jsonPath("$.data.tiers").doesNotExist())
                .andExpect(jsonPath("$.data.winnerGroups").doesNotExist());

        verify(drawService).screenStatus();
    }

    @Test
    @DisplayName("GET /status：version 与上次一致时只回 unchanged 增量响应")
    void statusReturnsUnchangedPayloadWhenVersionMatches() throws Exception
    {
        when(drawService.screenStatus()).thenReturn(screenStatus());

        mockMvc.perform(get("/app/screen/status").param("version", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unchanged").value(true))
                .andExpect(jsonPath("$.data.version").value("7"))
                .andExpect(jsonPath("$.data.sessionCode").doesNotExist());
    }

    @Test
    @DisplayName("GET /status：version 不一致时返回完整状态")
    void statusReturnsFullPayloadWhenVersionDiffers() throws Exception
    {
        when(drawService.screenStatus()).thenReturn(screenStatus());

        mockMvc.perform(get("/app/screen/status").param("version", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unchanged").doesNotExist())
                .andExpect(jsonPath("$.data.sessionCode").value("AM"));
    }

    @Test
    @DisplayName("GET /status：不带 version 时返回完整状态，不依赖登录态")
    void statusReturnsFullPayloadWithoutVersion() throws Exception
    {
        when(drawService.screenStatus()).thenReturn(screenStatus());

        int httpStatus = mockMvc.perform(get("/app/screen/status"))
                .andReturn().getResponse().getStatus();

        assertThat(httpStatus).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /status：匿名大屏接口按 IP 限流")
    void statusIsRateLimited() throws Exception
    {
        RateLimiter limiter = DrawScreenController.class
                .getDeclaredMethod("status", String.class).getAnnotation(RateLimiter.class);

        assertThat(limiter).isNotNull();
        assertThat(limiter.limitType()).isEqualTo(LimitType.IP);
        assertThat(limiter.count()).isEqualTo(30);
        assertThat(limiter.time()).isEqualTo(60);
    }

    private Map<String, Object> screenStatus()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionCode", "AM");
        data.put("version", 7);
        data.put("poolCount", 88);
        data.put("currentTier", Map.of("tierName", "三等奖"));
        return data;
    }
}
