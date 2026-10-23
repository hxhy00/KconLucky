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
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconPrizeGrantExportVo;
import com.ruoyi.kcon.service.IKconBenefitGrantService;

/**
 * 奖品发放日志管理端测试：列表/导出强制 PRIZE 类型。
 */
@ExtendWith(MockitoExtension.class)
class PrizeGrantControllerTest
{
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Mock
    private IKconBenefitGrantService grantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        PrizeGrantController controller = new PrizeGrantController();
        ReflectionTestUtils.setField(controller, "grantService", grantService);
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
    @DisplayName("GET /list：请求带 grantType=BENEFIT 也会被强制改写为 PRIZE")
    void listForcesPrizeGrantType() throws Exception
    {
        KconBenefitGrant row = new KconBenefitGrant();
        row.setId(9L);
        row.setGrantType(KconBenefitGrant.TYPE_PRIZE);
        when(grantService.selectGrantList(any(KconBenefitGrant.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/admin/prize-grant/list")
                        .param("grantType", KconBenefitGrant.TYPE_BENEFIT)
                        .param("portNo", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].id").value(9));

        ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
        verify(grantService).selectGrantList(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo(KconBenefitGrant.TYPE_PRIZE);
        assertThat(captor.getValue().getPortNo()).isEqualTo(12345);
    }

    @Test
    @DisplayName("POST /export：请求带 grantType=BENEFIT 也会被强制改写为 PRIZE")
    void exportForcesPrizeGrantType() throws Exception
    {
        when(grantService.selectPrizeGrantExportList(any(KconBenefitGrant.class)))
                .thenReturn(List.<KconPrizeGrantExportVo>of());

        MvcResult result = mockMvc.perform(post("/admin/prize-grant/export")
                        .param("grantType", KconBenefitGrant.TYPE_BENEFIT))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();

        ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
        verify(grantService).selectPrizeGrantExportList(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo(KconBenefitGrant.TYPE_PRIZE);
    }
}
