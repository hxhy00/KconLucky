package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.kcon.service.IKconCheckinService;

/**
 * 签到端匿名接口测试：入参传递、返回结构，且不依赖任何登录态。
 */
@ExtendWith(MockitoExtension.class)
class CheckinAppControllerTest
{
    @Mock
    private IKconCheckinService checkinService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        CheckinAppController controller = new CheckinAppController();
        ReflectionTestUtils.setField(controller, "checkinService", checkinService);
        // 刻意不 mock SecurityUtils：若误用了登录态，这里会直接失败
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("POST /query：透传 keyword 并返回 service 结果")
    void queryPassesKeyword() throws Exception
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchType", "single");
        when(checkinService.queryAttendee("8000")).thenReturn(data);

        mockMvc.perform(post("/app/checkin/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"8000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.matchType").value("single"));

        verify(checkinService).queryAttendee("8000");
    }

    @Test
    @DisplayName("POST /query：空 body 时 keyword 传 null，不抛异常")
    void queryWithEmptyBodyPassesNullKeyword() throws Exception
    {
        when(checkinService.queryAttendee(null)).thenReturn(Map.of("matchType", "none"));

        mockMvc.perform(post("/app/checkin/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchType").value("none"));

        verify(checkinService).queryAttendee(null);
    }

    @Test
    @DisplayName("POST /submit：attendeeId 与完整手机号一起透传")
    void submitPassesAttendeeIdAndPhone() throws Exception
    {
        when(checkinService.submitCheckin(12L, "13800138000"))
                .thenReturn(Map.of("portNo", 10086));

        mockMvc.perform(post("/app/checkin/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeId\":12,\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.portNo").value(10086));

        verify(checkinService).submitCheckin(12L, "13800138000");
    }

    @Test
    @DisplayName("POST /submit：缺 attendeeId 时直接报参数缺失，不调用 service")
    void submitRejectsMissingAttendeeId() throws Exception
    {
        mockMvc.perform(post("/app/checkin/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("参数缺失：attendeeId"));

        verify(checkinService, never()).submitCheckin(anyLong(), any());
    }

    @Test
    @DisplayName("POST /submit：attendeeId 非数字时提示格式错误，不调用 service")
    void submitRejectsMalformedAttendeeId() throws Exception
    {
        mockMvc.perform(post("/app/checkin/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendeeId\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("attendeeId 格式错误"));

        verify(checkinService, never()).submitCheckin(anyLong(), any());
    }

    @Test
    @DisplayName("GET /lookup：透传完整手机号并返回 service 结果")
    void lookupPassesKeyword() throws Exception
    {
        when(checkinService.lookup("13800138000")).thenReturn(Map.of("signed", true));

        mockMvc.perform(get("/app/checkin/lookup").param("keyword", "13800138000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.signed").value(true));

        verify(checkinService).lookup("13800138000");
    }

    @Test
    @DisplayName("GET /lookup：不带 keyword 时以 null 查询，不报 500")
    void lookupWithoutKeywordPassesNull() throws Exception
    {
        when(checkinService.lookup(null)).thenReturn(Map.of("signed", false));

        mockMvc.perform(get("/app/checkin/lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signed").value(false));

        verify(checkinService).lookup(null);
    }

    @Test
    @DisplayName("匿名接口不读取登录态：无 SecurityContext 也能正常响应")
    void anonymousEndpointsDoNotRequireLogin() throws Exception
    {
        when(checkinService.queryAttendee(eq("13900139000"))).thenReturn(Map.of("matchType", "single"));

        int status0 = mockMvc.perform(post("/app/checkin/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"13900139000\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status0).isEqualTo(200);
    }
}
