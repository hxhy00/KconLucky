package com.ruoyi.kcon.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.kcon.domain.KconSession;
import com.ruoyi.kcon.mapper.KconSessionMapper;

/**
 * 场次只读接口测试。
 */
@ExtendWith(MockitoExtension.class)
class SessionControllerTest
{
    @Mock
    private KconSessionMapper sessionMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        SessionController controller = new SessionController();
        ReflectionTestUtils.setField(controller, "sessionMapper", sessionMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /list：返回全部场次的 code/name/sort/status")
    void listReturnsAllSessions() throws Exception
    {
        KconSession morning = new KconSession();
        morning.setId(1L);
        morning.setCode("AM");
        morning.setName("上午场");
        morning.setSort(1);
        morning.setStatus(1);
        when(sessionMapper.selectSessionList()).thenReturn(List.of(morning));

        mockMvc.perform(get("/admin/session/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].code").value("AM"))
                .andExpect(jsonPath("$.data[0].name").value("上午场"))
                .andExpect(jsonPath("$.data[0].sort").value(1))
                .andExpect(jsonPath("$.data[0].status").value(1));

        verify(sessionMapper).selectSessionList();
    }
}
