package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.kcon.domain.KconAttendee;
import com.ruoyi.kcon.service.IKconAttendeeService;

/**
 * 参会人名单管理端测试：关键 CRUD、导入预检/正式导入、模板下载与权益恢复的委托与参数绑定。
 */
@ExtendWith(MockitoExtension.class)
class AttendeeControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Mock
    private IKconAttendeeService attendeeService;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        AttendeeController controller = new AttendeeController();
        ReflectionTestUtils.setField(controller, "attendeeService", attendeeService);
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
    @DisplayName("GET /list：查询条件透传并按分页结构返回")
    void listDelegatesQuery() throws Exception
    {
        KconAttendee row = attendee(1L, "张三", "13800138000");
        when(attendeeService.selectAttendeeList(any(KconAttendee.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/admin/attendee/list").param("name", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("张三"));

        ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
        verify(attendeeService).selectAttendeeList(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("GET /{id}：详情委托并返回实体")
    void detailDelegatesById() throws Exception
    {
        when(attendeeService.selectAttendeeById(7L)).thenReturn(attendee(7L, "李四", "13900139000"));

        mockMvc.perform(get("/admin/attendee/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("李四"));

        verify(attendeeService).selectAttendeeById(7L);
    }

    @Test
    @DisplayName("POST /admin/attendee：手机号不重复时新增，回填 createBy 与自增 ID")
    void addInsertsAndReturnsGeneratedId() throws Exception
    {
        stubLoginUser();
        when(attendeeService.selectByPhone("13800138000")).thenReturn(null);
        when(attendeeService.insertAttendee(any(KconAttendee.class))).thenAnswer(invocation -> {
            KconAttendee target = invocation.getArgument(0);
            target.setId(99L);
            return 1;
        });

        mockMvc.perform(post("/admin/attendee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"13800138000\",\"channel\":\"官网\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(99));

        ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
        verify(attendeeService).insertAttendee(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("POST /admin/attendee：手机号已存在时拒绝新增")
    void addRejectsDuplicatePhone() throws Exception
    {
        when(attendeeService.selectByPhone("13800138000"))
                .thenReturn(attendee(1L, "张三", "13800138000"));

        mockMvc.perform(post("/admin/attendee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("手机号已存在，请勿重复添加"));

        verify(attendeeService, never()).insertAttendee(any());
    }

    @Test
    @DisplayName("PUT /admin/attendee：编辑委托并记录 updateBy")
    void editUpdatesAndRecordsOperator() throws Exception
    {
        stubLoginUser();
        when(attendeeService.selectByPhone("13800138000"))
                .thenReturn(attendee(5L, "张三", "13800138000"));
        when(attendeeService.updateAttendee(any(KconAttendee.class))).thenReturn(1);

        mockMvc.perform(put("/admin/attendee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"name\":\"张三\",\"phone\":\"13800138000\",\"company\":\"KCon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<KconAttendee> captor = ArgumentCaptor.forClass(KconAttendee.class);
        verify(attendeeService).updateAttendee(captor.capture());
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(OPERATOR);
        assertThat(captor.getValue().getCompany()).isEqualTo("KCon");
    }

    @Test
    @DisplayName("PUT /admin/attendee：手机号属于其他人时拒绝编辑")
    void editRejectsPhoneUsedByAnotherAttendee() throws Exception
    {
        when(attendeeService.selectByPhone("13800138000"))
                .thenReturn(attendee(6L, "李四", "13800138000"));

        mockMvc.perform(put("/admin/attendee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"name\":\"张三\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("手机号已被其他参会人使用"));

        verify(attendeeService, never()).updateAttendee(any());
    }

    @Test
    @DisplayName("DELETE /{ids}：批量删除按数组绑定")
    void removeDelegatesIds() throws Exception
    {
        when(attendeeService.deleteAttendeeByIds(any(Long[].class))).thenReturn(2);

        mockMvc.perform(delete("/admin/attendee/1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(attendeeService).deleteAttendeeByIds(new Long[] { 1L, 2L });
    }

    @Test
    @DisplayName("PUT /{id}/ticket-type/reset：恢复权益委托 clearManualTicketType")
    void resetTicketTypeDelegates() throws Exception
    {
        when(attendeeService.clearManualTicketType(5L)).thenReturn(1);

        mockMvc.perform(put("/admin/attendee/5/ticket-type/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(attendeeService).clearManualTicketType(5L);
    }

    @Test
    @DisplayName("POST /import/preview：文件名、模式与文件流都传给预检")
    void previewImportPassesFileAndMode() throws Exception
    {
        when(attendeeService.previewImport(any(InputStream.class), eq("roster.xlsx"), eq("replace")))
                .thenReturn(Map.of("level", "OK", "totalRows", 10));
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/admin/attendee/import/preview").file(file).param("mode", "replace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("OK"))
                .andExpect(jsonPath("$.data.totalRows").value(10));

        verify(attendeeService).previewImport(any(InputStream.class), eq("roster.xlsx"), eq("replace"));
    }

    @Test
    @DisplayName("POST /import：正式导入把统计拼成中文提示并回传明细")
    void importBuildsChineseSummary() throws Exception
    {
        stubLoginUser();
        when(attendeeService.importAttendees(any(InputStream.class), eq("roster.xlsx"), eq("append"), eq(OPERATOR)))
                .thenReturn(Map.of("inserted", 3, "updated", 1, "skipped", 2, "total", 4));
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1 });

        mockMvc.perform(multipart("/admin/attendee/import").file(file).param("mode", "append"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("导入完成：新增 3，更新 1，跳过 2（有效名单共 4 人）"))
                .andExpect(jsonPath("$.data.inserted").value(3));
    }

    @Test
    @DisplayName("POST /import：空文件直接提示请选择文件，不调用 service")
    void importRejectsEmptyFile() throws Exception
    {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/admin/attendee/import").file(empty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请选择文件"));

        verify(attendeeService, never()).importAttendees(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /template：下载导入模板，返回 Excel 流")
    void templateReturnsExcel() throws Exception
    {
        mockMvc.perform(post("/admin/attendee/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX));
    }

    @Test
    @DisplayName("GET /dashboard：看板统计委托")
    void dashboardDelegates() throws Exception
    {
        when(attendeeService.dashboardStat()).thenReturn(Map.of("total", 120));

        mockMvc.perform(get("/admin/attendee/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(120));
    }

    private KconAttendee attendee(Long id, String name, String phone)
    {
        KconAttendee attendee = new KconAttendee();
        attendee.setId(id);
        attendee.setName(name);
        attendee.setPhone(phone);
        return attendee;
    }

    private void stubLoginUser()
    {
        SysUser user = new SysUser();
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(9L, null, user, Set.of()));
    }
}
