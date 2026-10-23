package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.kcon.domain.KconVerifyLog;
import com.ruoyi.kcon.domain.KconVerifyLogExportVo;
import com.ruoyi.kcon.service.IKconVerifyService;

/**
 * 兑奖核验日志管理端测试：列表与导出委托。
 */
@ExtendWith(MockitoExtension.class)
class VerifyLogControllerTest
{
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Mock
    private IKconVerifyService verifyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        VerifyLogController controller = new VerifyLogController();
        ReflectionTestUtils.setField(controller, "verifyService", verifyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void tearDown()
    {
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("GET /list：查询条件透传并返回分页结构")
    void listDelegatesQuery() throws Exception
    {
        KconVerifyLog row = new KconVerifyLog();
        row.setId(1L);
        row.setPortNo(10086);
        row.setResult(KconVerifyLog.RESULT_VALID);
        when(verifyService.selectVerifyLogList(any(KconVerifyLog.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/admin/verifylog/list")
                        .param("portNo", "10086")
                        .param("result", "VALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].portNo").value(10086))
                .andExpect(jsonPath("$.rows[0].result").value("VALID"));

        ArgumentCaptor<KconVerifyLog> captor = ArgumentCaptor.forClass(KconVerifyLog.class);
        verify(verifyService).selectVerifyLogList(captor.capture());
        assertThat(captor.getValue().getPortNo()).isEqualTo(10086);
        assertThat(captor.getValue().getResult()).isEqualTo("VALID");
    }

    @Test
    @DisplayName("POST /export：导出委托并返回 Excel 流")
    void exportDelegatesAndWritesExcel() throws Exception
    {
        when(verifyService.selectVerifyLogExportList(any(KconVerifyLog.class)))
                .thenReturn(List.<KconVerifyLogExportVo>of());

        MvcResult result = mockMvc.perform(post("/admin/verifylog/export").param("result", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();

        ArgumentCaptor<KconVerifyLog> captor = ArgumentCaptor.forClass(KconVerifyLog.class);
        verify(verifyService).selectVerifyLogExportList(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("RECEIVED");
    }
}
