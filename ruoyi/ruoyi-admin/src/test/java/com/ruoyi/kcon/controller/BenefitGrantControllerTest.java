package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconBenefitGrant;
import com.ruoyi.kcon.domain.KconBenefitGrantExportVo;
import com.ruoyi.kcon.service.IKconBenefitGrantService;

/**
 * 权益发放管理端测试：发放委托、列表/导出强制 BENEFIT 类型。
 */
@ExtendWith(MockitoExtension.class)
class BenefitGrantControllerTest
{
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Mock
    private IKconBenefitGrantService grantService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        BenefitGrantController controller = new BenefitGrantController();
        ReflectionTestUtils.setField(controller, "grantService", grantService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
        securityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown()
    {
        securityUtils.close();
        PageHelper.clearPage();
    }

    @Test
    @DisplayName("POST /grant/{id}：把参会人 ID 与当前操作人传给 grantBenefit，并返回发放成功")
    void grantDelegatesToServiceWithCurrentOperator() throws Exception
    {
        stubLoginUser();

        mockMvc.perform(post("/admin/benefit/grant/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("发放成功"));

        verify(grantService).grantBenefit(42L, "tester");
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName("tester");
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }

    @Test
    @DisplayName("GET /list：请求带 grantType=PRIZE 也会被强制改写为 BENEFIT")
    void listForcesBenefitGrantType() throws Exception
    {
        KconBenefitGrant row = new KconBenefitGrant();
        row.setId(1L);
        row.setGrantType(KconBenefitGrant.TYPE_BENEFIT);
        when(grantService.selectGrantList(any(KconBenefitGrant.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/admin/benefit/list")
                        .param("grantType", KconBenefitGrant.TYPE_PRIZE)
                        .param("name", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].id").value(1));

        ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
        verify(grantService).selectGrantList(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo(KconBenefitGrant.TYPE_BENEFIT);
        assertThat(captor.getValue().getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("POST /export：导出同样强制 BENEFIT，返回 Excel 流")
    void exportForcesBenefitGrantType() throws Exception
    {
        when(grantService.selectBenefitGrantExportList(any(KconBenefitGrant.class)))
                .thenReturn(List.<KconBenefitGrantExportVo>of());

        MvcResult result = mockMvc.perform(post("/admin/benefit/export")
                        .param("grantType", KconBenefitGrant.TYPE_PRIZE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();

        ArgumentCaptor<KconBenefitGrant> captor = ArgumentCaptor.forClass(KconBenefitGrant.class);
        verify(grantService).selectBenefitGrantExportList(captor.capture());
        assertThat(captor.getValue().getGrantType()).isEqualTo(KconBenefitGrant.TYPE_BENEFIT);
    }
}
