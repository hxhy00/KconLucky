package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;

/**
 * 大屏设置测试：赞助商文案读取（逗号拆分）与保存（新增/更新/清空）。
 */
@ExtendWith(MockitoExtension.class)
class DrawSettingControllerTest
{
    private static final String SPONSORS_KEY = "kcon.sponsors";

    @Mock
    private ISysConfigService sysConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        DrawSettingController controller = new DrawSettingController();
        ReflectionTestUtils.setField(controller, "sysConfigService", sysConfigService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /setting：中英文逗号都能拆分，去空格并丢弃空项")
    void getSettingSplitsSponsorsByComma() throws Exception
    {
        when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn(" 极客邦 , KCon，某某科技, ,");

        mockMvc.perform(get("/admin/draw/setting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.sponsors.length()").value(3))
                .andExpect(jsonPath("$.sponsors[0]").value("极客邦"))
                .andExpect(jsonPath("$.sponsors[1]").value("KCon"))
                .andExpect(jsonPath("$.sponsors[2]").value("某某科技"));
    }

    @Test
    @DisplayName("GET /setting：配置为空时返回空数组")
    void getSettingReturnsEmptyListWhenConfigMissing() throws Exception
    {
        when(sysConfigService.selectConfigByKey(SPONSORS_KEY)).thenReturn(null);

        mockMvc.perform(get("/admin/draw/setting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsors").isArray())
                .andExpect(jsonPath("$.sponsors.length()").value(0));
    }

    @Test
    @DisplayName("POST /setting：配置不存在时新增，值做 trim")
    void saveSettingInsertsWhenConfigMissing() throws Exception
    {
        when(sysConfigService.selectConfigList(any(SysConfig.class))).thenReturn(List.of());

        mockMvc.perform(post("/admin/draw/setting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsors\":\"  极客邦, KCon  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("已保存，大屏将在 1.5 秒内自动刷新显示"));

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigService).insertConfig(captor.capture());
        SysConfig saved = captor.getValue();
        assertThat(saved.getConfigKey()).isEqualTo(SPONSORS_KEY);
        assertThat(saved.getConfigValue()).isEqualTo("极客邦, KCon");
        assertThat(saved.getConfigName()).isEqualTo("大屏赞助商");
        assertThat(saved.getConfigType()).isEqualTo("N");
        verify(sysConfigService, never()).updateConfig(any());
    }

    @Test
    @DisplayName("POST /setting：配置已存在时更新原记录")
    void saveSettingUpdatesExistingConfig() throws Exception
    {
        SysConfig existing = new SysConfig();
        existing.setConfigId(77L);
        when(sysConfigService.selectConfigList(any(SysConfig.class))).thenReturn(List.of(existing));

        mockMvc.perform(post("/admin/draw/setting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsors\":\"新赞助商\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigService).updateConfig(captor.capture());
        assertThat(captor.getValue().getConfigId()).isEqualTo(77L);
        assertThat(captor.getValue().getConfigValue()).isEqualTo("新赞助商");
        verify(sysConfigService, never()).insertConfig(any());
    }

    @Test
    @DisplayName("POST /setting：显式传空串视为清空，删除配置项")
    void saveSettingDeletesConfigWhenValueEmpty() throws Exception
    {
        SysConfig existing = new SysConfig();
        existing.setConfigId(77L);
        when(sysConfigService.selectConfigList(any(SysConfig.class))).thenReturn(List.of(existing));

        mockMvc.perform(post("/admin/draw/setting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsors\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("已清空赞助商，大屏将在 1.5 秒内自动刷新"));

        verify(sysConfigService).deleteConfigByIds(new Long[] { 77L });
        verify(sysConfigService, never()).insertConfig(any());
        verify(sysConfigService, never()).updateConfig(any());
    }

    @Test
    @DisplayName("POST /setting：未携带 sponsors 字段时拒绝，不修改线上配置")
    void saveSettingRejectsMissingSponsorsField() throws Exception
    {
        mockMvc.perform(post("/admin/draw/setting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("缺少 sponsors 字段，未做任何修改"));

        verifyNoInteractions(sysConfigService);
    }
}
