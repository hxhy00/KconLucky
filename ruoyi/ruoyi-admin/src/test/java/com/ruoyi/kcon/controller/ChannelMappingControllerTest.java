package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconChannelMapping;
import com.ruoyi.kcon.service.IKconChannelMappingService;

/**
 * 渠道来源映射管理端测试：CRUD / 下拉 / 重算委托与权限注解回归。
 */
@ExtendWith(MockitoExtension.class)
class ChannelMappingControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    @Mock
    private IKconChannelMappingService channelMappingService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        ChannelMappingController controller = new ChannelMappingController();
        ReflectionTestUtils.setField(controller, "channelMappingService", channelMappingService);
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
    @DisplayName("GET /list：分页查询委托")
    void listDelegatesQuery() throws Exception
    {
        KconChannelMapping row = mapping(1L, "官网");
        when(channelMappingService.selectList(any(KconChannelMapping.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/admin/channel-mapping/list").param("channelSource", "官网"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].channelSource").value("官网"));
    }

    @Test
    @DisplayName("GET /optionselect：不分页返回全量映射")
    void optionselectReturnsAllMappings() throws Exception
    {
        when(channelMappingService.selectList(any(KconChannelMapping.class)))
                .thenReturn(List.of(mapping(1L, "官网"), mapping(2L, "合作方")));

        mockMvc.perform(get("/admin/channel-mapping/optionselect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].channelSource").value("合作方"));
    }

    @Test
    @DisplayName("POST：新增委托并写入 createBy")
    void addDelegatesWithOperator() throws Exception
    {
        stubLoginUser();
        when(channelMappingService.insert(any(KconChannelMapping.class))).thenReturn(1);

        mockMvc.perform(post("/admin/channel-mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelSource\":\"官网\",\"benefitType\":\"全通票\",\"drawEligible\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<KconChannelMapping> captor = ArgumentCaptor.forClass(KconChannelMapping.class);
        verify(channelMappingService).insert(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo(OPERATOR);
        assertThat(captor.getValue().getChannelSource()).isEqualTo("官网");
    }

    @Test
    @DisplayName("PUT：编辑委托并写入 updateBy")
    void editDelegatesWithOperator() throws Exception
    {
        stubLoginUser();
        when(channelMappingService.update(any(KconChannelMapping.class))).thenReturn(1);

        mockMvc.perform(put("/admin/channel-mapping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"channelSource\":\"官网\",\"drawEligible\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<KconChannelMapping> captor = ArgumentCaptor.forClass(KconChannelMapping.class);
        verify(channelMappingService).update(captor.capture());
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(OPERATOR);
        assertThat(captor.getValue().getDrawEligible()).isZero();
    }

    @Test
    @DisplayName("DELETE /{id}：删除委托")
    void removeDelegatesById() throws Exception
    {
        when(channelMappingService.deleteById(3L)).thenReturn(1);

        mockMvc.perform(delete("/admin/channel-mapping/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(channelMappingService).deleteById(3L);
    }

    @Test
    @DisplayName("POST /resync：全量重算并把统计拼成提示")
    void resyncReportsStatistics() throws Exception
    {
        when(channelMappingService.resyncAll()).thenReturn(Map.of("synced", 10, "lockedCount", 2));

        mockMvc.perform(post("/admin/channel-mapping/resync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("重算完成：已同步 10 人，2 人因已签到/已中奖未修改"))
                .andExpect(jsonPath("$.data.synced").value(10));

        verify(channelMappingService).resyncAll();
    }

    @Test
    @DisplayName("权限注解回归：读写接口都挂有 @PreAuthorize")
    void writeEndpointsCarryPreAuthorize() throws Exception
    {
        assertThat(ChannelMappingController.class.getMethod("list", KconChannelMapping.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@ss.hasPermi('kcon:channel:list')");
        assertThat(ChannelMappingController.class.getMethod("optionselect")
                .getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasAnyPermi('kcon:channel:list,kcon:attendee:list')");
        assertThat(ChannelMappingController.class.getMethod("add", KconChannelMapping.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@ss.hasPermi('kcon:channel:add')");
        assertThat(ChannelMappingController.class.getMethod("edit", KconChannelMapping.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@ss.hasPermi('kcon:channel:edit')");
        assertThat(ChannelMappingController.class.getMethod("remove", Long.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@ss.hasPermi('kcon:channel:remove')");
        assertThat(ChannelMappingController.class.getMethod("resync")
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@ss.hasPermi('kcon:channel:edit')");
    }

    private KconChannelMapping mapping(Long id, String channel)
    {
        KconChannelMapping mapping = new KconChannelMapping();
        mapping.setId(id);
        mapping.setChannelSource(channel);
        mapping.setDrawEligible(1);
        return mapping;
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }
}
