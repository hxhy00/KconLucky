package com.ruoyi.kcon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.service.IKconDrawService;
import com.ruoyi.kcon.service.IKconVerifyService;

/**
 * 兑奖核验管理端测试：operator 取当前登录用户，参数校验与列表委托。
 */
@ExtendWith(MockitoExtension.class)
class VerifyControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    @Mock
    private IKconVerifyService verifyService;

    @Mock
    private IKconDrawService drawService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        VerifyController controller = new VerifyController();
        ReflectionTestUtils.setField(controller, "verifyService", verifyService);
        ReflectionTestUtils.setField(controller, "drawService", drawService);
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
    @DisplayName("POST /admin/verify：把当前登录用户名作为 operator 传给 verify")
    void verifyPassesCurrentUsernameAsOperator() throws Exception
    {
        stubLoginUser();
        when(verifyService.verify(12345, "13800138000", OPERATOR))
                .thenReturn(Map.of("result", "VALID"));

        mockMvc.perform(post("/admin/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portNo\":12345,\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").value("VALID"));

        verify(verifyService).verify(12345, "13800138000", OPERATOR);
    }

    @Test
    @DisplayName("POST /admin/verify：缺 portNo 时提示请输入端口号，不调用 service")
    void verifyRejectsMissingPortNo() throws Exception
    {
        mockMvc.perform(post("/admin/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请输入端口号"));

        verify(verifyService, never()).verify(anyInt(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/verify：portNo 非数字时提示格式错误")
    void verifyRejectsMalformedPortNo() throws Exception
    {
        mockMvc.perform(post("/admin/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portNo\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("端口号格式错误"));

        verify(verifyService, never()).verify(anyInt(), any(), any());
    }

    @Test
    @DisplayName("POST /admin/verify/receive：发放同样把当前用户名作为 operator")
    void receivePassesCurrentUsernameAsOperator() throws Exception
    {
        stubLoginUser();

        mockMvc.perform(post("/admin/verify/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portNo\":12345,\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("发放成功"));

        verify(verifyService).receive(12345, "13800138000", OPERATOR);
    }

    @Test
    @DisplayName("POST /admin/verify/void/{id}：作废中奖同样记录操作人")
    void voidWinnerPassesCurrentUsernameAsOperator() throws Exception
    {
        stubLoginUser();

        mockMvc.perform(post("/admin/verify/void/33"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("作废成功，端口号已回池"));

        verify(drawService).voidWinner(33L, OPERATOR);
    }

    @Test
    @DisplayName("GET /admin/verify/winners：分页查询委托给 drawService")
    void winnersDelegatesToDrawService() throws Exception
    {
        KconWinner winner = new KconWinner();
        winner.setId(1L);
        winner.setPortNo(12345);
        winner.setWinTime(new Date());
        when(drawService.selectWinnerList(any(KconWinner.class))).thenReturn(List.of(winner));

        mockMvc.perform(get("/admin/verify/winners").param("sessionCode", "AM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].portNo").value(12345));

        verify(drawService).selectWinnerList(any(KconWinner.class));
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }
}
