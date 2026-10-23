package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconPrize;
import com.ruoyi.kcon.service.IKconDrawService;

/**
 * 奖品档位管理端测试：同名档位拦截与增删改查委托。
 */
@ExtendWith(MockitoExtension.class)
class PrizeControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    @Mock
    private IKconDrawService drawService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        PrizeController controller = new PrizeController();
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
    @DisplayName("GET /list：按场次查询档位")
    void listDelegatesQuery() throws Exception
    {
        KconPrize prize = prize("一等奖", 1);
        when(drawService.selectPrizeList(any(KconPrize.class))).thenReturn(List.of(prize));

        mockMvc.perform(get("/admin/prize/list").param("sessionCode", "AM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].tierName").value("一等奖"));

        ArgumentCaptor<KconPrize> captor = ArgumentCaptor.forClass(KconPrize.class);
        verify(drawService).selectPrizeList(captor.capture());
        assertThat(captor.getValue().getSessionCode()).isEqualTo("AM");
    }

    @Test
    @DisplayName("POST：同场次同名档位被拒绝，不落库")
    void addRejectsDuplicateTierName() throws Exception
    {
        when(drawService.selectPrizeList(any(KconPrize.class))).thenReturn(List.of(prize("一等奖", 1)));

        mockMvc.perform(post("/admin/prize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":\"AM\",\"tierName\":\"一等奖\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该场次已存在同名奖项「一等奖」，请勿重复添加"));

        verify(drawService, never()).insertPrize(any());
    }

    @Test
    @DisplayName("POST：档位不重名时新增并写入 createBy")
    void addInsertsWithOperator() throws Exception
    {
        stubLoginUser();
        when(drawService.selectPrizeList(any(KconPrize.class))).thenReturn(List.of(prize("三等奖", 1)));
        when(drawService.insertPrize(any(KconPrize.class))).thenReturn(1);

        mockMvc.perform(post("/admin/prize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionCode\":\"AM\",\"tierName\":\"一等奖\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<KconPrize> captor = ArgumentCaptor.forClass(KconPrize.class);
        verify(drawService).insertPrize(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("PUT：编辑写入 updateBy")
    void editDelegatesWithOperator() throws Exception
    {
        stubLoginUser();
        when(drawService.updatePrize(any(KconPrize.class))).thenReturn(1);

        mockMvc.perform(put("/admin/prize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"sessionCode\":\"AM\",\"tierName\":\"一等奖\",\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<KconPrize> captor = ArgumentCaptor.forClass(KconPrize.class);
        verify(drawService).updatePrize(captor.capture());
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(OPERATOR);
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("DELETE /{id}：删除委托")
    void removeDelegatesById() throws Exception
    {
        when(drawService.deletePrizeById(4L)).thenReturn(1);

        mockMvc.perform(delete("/admin/prize/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(drawService).deletePrizeById(4L);
    }

    private KconPrize prize(String tierName, int quantity)
    {
        KconPrize prize = new KconPrize();
        prize.setSessionCode("AM");
        prize.setTierName(tierName);
        prize.setQuantity(quantity);
        return prize;
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }
}
