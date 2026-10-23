package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.domain.KconWinner;
import com.ruoyi.kcon.service.IKconDrawService;

/**
 * 抽奖控制台管理端测试：状态组装、抽奖/作废/撤回/切场次的参数传递。
 */
@ExtendWith(MockitoExtension.class)
class DrawAdminControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    @Mock
    private IKconDrawService drawService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        DrawAdminController controller = new DrawAdminController();
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
    @DisplayName("GET /status：管理端返回统计字段、档位进度、上一次中奖与分组记录")
    void statusCombinesAdminOnlyFields() throws Exception
    {
        Date winTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2026-10-23 20:00:00");
        stubStatus(winTime);

        mockMvc.perform(get("/admin/draw/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionCode").value("AM"))
                // 管理端专属字段：与大屏不同，这里必须下发
                .andExpect(jsonPath("$.data.excludedCount").value(12))
                .andExpect(jsonPath("$.data.checkedInToday").value(100))
                .andExpect(jsonPath("$.data.winnerCount").value(5))
                .andExpect(jsonPath("$.data.tiers[0].tierName").value("一等奖"))
                .andExpect(jsonPath("$.data.lastWinner.id").value(33))
                .andExpect(jsonPath("$.data.lastWinner.portNo").value(10086))
                .andExpect(jsonPath("$.data.lastWinner.name").value("张三"))
                .andExpect(jsonPath("$.data.lastWinner.company").value("KCon"))
                .andExpect(jsonPath("$.data.lastWinner.tierName").value("一等奖"))
                .andExpect(jsonPath("$.data.lastWinner.winTime").value("2026-10-23 20:00:00"))
                .andExpect(jsonPath("$.data.winnerGroups[0].sessionCode").value("AM"));

        ArgumentCaptor<KconPrize> prizeQuery = ArgumentCaptor.forClass(KconPrize.class);
        verify(drawService).selectPrizeList(prizeQuery.capture());
        assertThat(prizeQuery.getValue().getSessionCode()).isEqualTo("AM");

        ArgumentCaptor<KconWinner> winnerQuery = ArgumentCaptor.forClass(KconWinner.class);
        verify(drawService).selectWinnerList(winnerQuery.capture());
        assertThat(winnerQuery.getValue().getSessionCode()).isEqualTo("AM");
        assertThat(winnerQuery.getValue().getStatus()).isEqualTo(KconWinner.STATUS_WON);
    }

    @Test
    @DisplayName("GET /status：没有中奖记录时 lastWinner 为 null")
    void statusHasNullLastWinnerWhenNoWinner() throws Exception
    {
        stubStatus(null);

        mockMvc.perform(get("/admin/draw/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastWinner").isEmpty());
    }

    @Test
    @DisplayName("POST /draw：场次与档位透传，并附带刷新后的状态")
    void drawPassesParamsAndAppendsStatus() throws Exception
    {
        stubLoginUser();
        stubStatus(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portNo", 12345);
        when(drawService.draw("AM", 3L, OPERATOR)).thenReturn(result);

        mockMvc.perform(post("/admin/draw/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":\"AM\",\"prizeId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.portNo").value(12345))
                .andExpect(jsonPath("$.data.status.sessionCode").value("AM"));

        verify(drawService).draw("AM", 3L, OPERATOR);
    }

    @Test
    @DisplayName("POST /draw：prizeId 非数字时提示格式错误，不调用抽奖")
    void drawRejectsMalformedPrizeId() throws Exception
    {
        mockMvc.perform(post("/admin/draw/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":\"AM\",\"prizeId\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("prizeId 格式错误"));

        verify(drawService, never()).draw(any(), any(), any());
    }

    @Test
    @DisplayName("POST /void：作废指定中奖记录后回传最新状态")
    void voidDelegatesAndReturnsStatus() throws Exception
    {
        stubLoginUser();
        stubStatus(null);

        mockMvc.perform(post("/admin/draw/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerId\":33}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionCode").value("AM"));

        verify(drawService).voidWinner(33L, OPERATOR);
    }

    @Test
    @DisplayName("POST /void：缺 winnerId / 格式错误分别给出明确提示")
    void voidRejectsMissingOrMalformedWinnerId() throws Exception
    {
        mockMvc.perform(post("/admin/draw/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.msg").value("参数缺失：winnerId"));

        mockMvc.perform(post("/admin/draw/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerId\":\"abc\"}"))
                .andExpect(jsonPath("$.msg").value("winnerId 格式错误"));

        verify(drawService, never()).voidWinner(any(), any());
    }

    @Test
    @DisplayName("POST /void-batch：sessionCode 传数字不会 500，按字符串透传")
    void voidBatchAcceptsNumericSessionCode() throws Exception
    {
        stubLoginUser();
        stubStatus(null);
        when(drawService.voidLastBatch("123", OPERATOR)).thenReturn(2);

        mockMvc.perform(post("/admin/draw/void-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":123}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.voidedCount").value(2))
                .andExpect(jsonPath("$.data.sessionCode").value("AM"));

        verify(drawService).voidLastBatch("123", OPERATOR);
    }

    @Test
    @DisplayName("POST /void-batch：没有可撤回记录时提示错误")
    void voidBatchReportsNothingToVoid() throws Exception
    {
        stubLoginUser();
        when(drawService.voidLastBatch(null, OPERATOR)).thenReturn(0);

        mockMvc.perform(post("/admin/draw/void-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("当前场次没有可撤回的中奖记录"));

        verify(drawService).voidLastBatch(null, OPERATOR);
    }

    @Test
    @DisplayName("POST /session：切换场次并回传最新状态")
    void sessionSwitchesAndReturnsStatus() throws Exception
    {
        stubStatus(null);

        mockMvc.perform(post("/admin/draw/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":\"PM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionCode").value("AM"));

        verify(drawService).switchSession("PM");
    }

    /** 组装管理端状态所需的最小桩：screenStatus + 三项统计 + 档位 + 中奖 + 分组 */
    private void stubStatus(Date winTime)
    {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("sessionCode", "AM");
        base.put("version", 7);
        base.put("poolCount", 88);
        when(drawService.screenStatus()).thenReturn(base);
        when(drawService.countExcludedToday()).thenReturn(12);
        when(drawService.countCheckedInToday()).thenReturn(100);
        when(drawService.countWinnersAll()).thenReturn(5);

        KconPrize prize = new KconPrize();
        prize.setId(1L);
        prize.setSessionCode("AM");
        prize.setTierName("一等奖");
        prize.setQuantity(1);
        when(drawService.selectPrizeList(any(KconPrize.class))).thenReturn(List.of(prize));

        if (winTime != null)
        {
            KconWinner winner = new KconWinner();
            winner.setId(33L);
            winner.setPortNo(10086);
            winner.setName("张三");
            winner.setCompany("KCon");
            winner.setTierName("一等奖");
            winner.setWinTime(winTime);
            when(drawService.selectWinnerList(any(KconWinner.class))).thenReturn(List.of(winner));
        }

        when(drawService.winnerGroups()).thenReturn(List.of(Map.of("sessionCode", "AM")));
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }
}
