package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.mapper.KconAttendeeMapper;
import com.ruoyi.kcon.mapper.KconBenefitGrantMapper;
import com.ruoyi.kcon.mapper.KconCheckinMapper;
import com.ruoyi.kcon.mapper.KconPortPoolMapper;
import com.ruoyi.kcon.mapper.KconPrizeMapper;
import com.ruoyi.kcon.mapper.KconSessionMapper;
import com.ruoyi.kcon.mapper.KconVerifyLogMapper;
import com.ruoyi.kcon.mapper.KconWinnerMapper;
import com.ruoyi.kcon.service.SensitiveOperationGuard;

/**
 * 数据导出与清场测试：清场必须先校验密码再动数据，导出/二维码委托。
 */
@ExtendWith(MockitoExtension.class)
class ExportControllerTest
{
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Mock
    private KconCheckinMapper checkinMapper;

    @Mock
    private KconAttendeeMapper attendeeMapper;

    @Mock
    private KconWinnerMapper winnerMapper;

    @Mock
    private KconPortPoolMapper portPoolMapper;

    @Mock
    private KconPrizeMapper prizeMapper;

    @Mock
    private KconVerifyLogMapper verifyLogMapper;

    @Mock
    private KconBenefitGrantMapper benefitGrantMapper;

    @Mock
    private KconSessionMapper sessionMapper;

    @Mock
    private SensitiveOperationGuard sensitiveOperationGuard;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        ExportController controller = new ExportController();
        ReflectionTestUtils.setField(controller, "checkinMapper", checkinMapper);
        ReflectionTestUtils.setField(controller, "attendeeMapper", attendeeMapper);
        ReflectionTestUtils.setField(controller, "winnerMapper", winnerMapper);
        ReflectionTestUtils.setField(controller, "portPoolMapper", portPoolMapper);
        ReflectionTestUtils.setField(controller, "prizeMapper", prizeMapper);
        ReflectionTestUtils.setField(controller, "verifyLogMapper", verifyLogMapper);
        ReflectionTestUtils.setField(controller, "benefitGrantMapper", benefitGrantMapper);
        ReflectionTestUtils.setField(controller, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(controller, "sensitiveOperationGuard", sensitiveOperationGuard);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("POST /reset：密码校验失败时一个字节都不动")
    void resetStopsBeforeAnyWriteWhenPasswordRejected() throws Exception
    {
        doThrow(new ServiceException("管理员密码不正确，还可尝试 4 次"))
                .when(sensitiveOperationGuard).verifyCurrentUserPassword("wrong", "数据清场");

        assertThatThrownBy(() -> mockMvc.perform(post("/admin/data/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}")))
                .hasRootCauseInstanceOf(ServiceException.class)
                .rootCause()
                .hasMessage("管理员密码不正确，还可尝试 4 次");

        verifyNoInteractions(sessionMapper, portPoolMapper, prizeMapper, checkinMapper,
                winnerMapper, verifyLogMapper, benefitGrantMapper, attendeeMapper);
    }

    @Test
    @DisplayName("POST /reset：先校验密码，再按可回滚 -> 不可回滚的顺序清场")
    void resetVerifiesPasswordBeforeClearing() throws Exception
    {
        mockMvc.perform(post("/admin/data/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pwd123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("清场完成：名单、签到、中奖、核验日志、发放日志已清空，"
                        + "端口号池与奖品已抽数已归零，场次状态已复位"));

        InOrder inOrder = inOrder(sensitiveOperationGuard, sessionMapper, portPoolMapper, prizeMapper,
                checkinMapper, winnerMapper, verifyLogMapper, benefitGrantMapper, attendeeMapper);
        inOrder.verify(sensitiveOperationGuard).verifyCurrentUserPassword("pwd123456", "数据清场");
        inOrder.verify(sessionMapper).deactivateAll();
        inOrder.verify(portPoolMapper).releaseAll();
        inOrder.verify(prizeMapper).resetDrawnCount();
        inOrder.verify(checkinMapper).truncateCheckin();
        inOrder.verify(winnerMapper).truncateWinner();
        inOrder.verify(verifyLogMapper).truncateLog();
        inOrder.verify(benefitGrantMapper).truncateGrant();
        inOrder.verify(attendeeMapper).truncateAttendee();
    }

    @Test
    @DisplayName("POST /export：签到明细转成导出 VO 并输出 Excel")
    void exportCheckinWritesExcel() throws Exception
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("姓名", "张三");
        row.put("手机号", "138****8000");
        row.put("渠道标识", "官网");
        row.put("票种", "全通票");
        row.put("端口号", 10086);
        when(checkinMapper.selectExportList()).thenReturn(List.of(row));

        MvcResult result = mockMvc.perform(post("/admin/data/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("GET /qrcode：返回签到页地址")
    void qrcodeReturnsCheckinUrl() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/admin/data/qrcode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("http://").contains("/sign/");
    }

    @Test
    @DisplayName("GET /qrcode.png：直接输出 PNG")
    void qrcodePngWritesPng() throws Exception
    {
        mockMvc.perform(get("/admin/data/qrcode.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .contains("kcon-checkin-qrcode.png"));
    }
}
