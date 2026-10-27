package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.kcon.service.StaffRoleGuard;
import com.ruoyi.system.service.ISysUserService;

/**
 * 账号管理端测试：身份下拉、列表补身份名、建号/编辑校验、管理员授权边界、重置密码/启停/删除的前置守卫与令牌吊销。
 */
@ExtendWith(MockitoExtension.class)
class StaffControllerTest
{
    private static final String OPERATOR = "kcon_admin";

    private static final Long LOGIN_USER_ID = 5L;

    private static final Long ADMIN_ROLE_ID = 1L;

    private static final Long CHECKIN_ROLE_ID = 11L;

    private static final Long DRAW_ROLE_ID = 12L;

    private static final Long VERIFY_ROLE_ID = 13L;

    @Mock
    private ISysUserService userService;

    @Mock
    private StaffRoleGuard staffRoleGuard;

    @Mock
    private RedisCache redisCache;

    @Mock
    private TokenService tokenService;

    private StaffController controller;

    private MockMvc mockMvc;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        controller = new StaffController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "staffRoleGuard", staffRoleGuard);
        ReflectionTestUtils.setField(controller, "redisCache", redisCache);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
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
    @DisplayName("GET /roles：身份下拉委托 StaffRoleGuard（含管理员）")
    void rolesDelegatesToGuard() throws Exception
    {
        when(staffRoleGuard.selectableRoles()).thenReturn(List.of(
                roleOption("admin", "管理员", ADMIN_ROLE_ID),
                roleOption("kcon_checkin_staff", "签到台", CHECKIN_ROLE_ID),
                roleOption("kcon_draw_staff", "抽奖台", DRAW_ROLE_ID),
                roleOption("kcon_verify_staff", "兑奖台", VERIFY_ROLE_ID)));

        mockMvc.perform(get("/admin/staff/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].roleKey").value("admin"));
    }

    @Test
    @DisplayName("GET /list：按四个身份查询，并补齐身份名与身份 key")
    void listFillsRoleNamesAndKeys() throws Exception
    {
        SysUser first = new SysUser();
        first.setUserId(9L);
        first.setNickName("张三");
        SysUser second = new SysUser();
        second.setUserId(10L);
        second.setNickName("李四");

        when(staffRoleGuard.resolveRolePublic("admin")).thenReturn(role(ADMIN_ROLE_ID, "admin", "管理员"));
        when(staffRoleGuard.resolveRolePublic("kcon_checkin_staff")).thenReturn(role(CHECKIN_ROLE_ID, "kcon_checkin_staff", "签到台"));
        when(staffRoleGuard.resolveRolePublic("kcon_draw_staff")).thenReturn(role(DRAW_ROLE_ID, "kcon_draw_staff", "抽奖台"));
        when(staffRoleGuard.resolveRolePublic("kcon_verify_staff")).thenReturn(role(VERIFY_ROLE_ID, "kcon_verify_staff", "兑奖台"));
        when(staffRoleGuard.managedRolesOf(9L)).thenReturn(List.of(
                role(ADMIN_ROLE_ID, "admin", "管理员"),
                role(VERIFY_ROLE_ID, "kcon_verify_staff", "兑奖台")));
        when(staffRoleGuard.managedRolesOf(10L)).thenReturn(List.of());
        when(userService.selectAllocatedRoleInList(any(SysUser.class))).thenReturn(List.of(first, second));

        mockMvc.perform(get("/admin/staff/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows[0].roleNames").value("管理员、兑奖台"))
                .andExpect(jsonPath("$.rows[0].roleKeys[0]").value("admin"))
                .andExpect(jsonPath("$.rows[1].roleNames").isEmpty());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).selectAllocatedRoleInList(captor.capture());
        assertThat(captor.getValue().getRoleIds())
                .containsExactly(ADMIN_ROLE_ID, CHECKIN_ROLE_ID, DRAW_ROLE_ID, VERIFY_ROLE_ID);
    }

    @Test
    @DisplayName("POST：建号按手机号生成内部账号、强制身份并忽略部门/岗位/邮箱等前端字段")
    void addCreatesStaffWithServerSideFields() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        securityUtils.when(() -> SecurityUtils.encryptPassword("abc12345")).thenReturn("ENC_HASH");
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN))).thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(true);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
        when(userService.insertUser(any(SysUser.class))).thenReturn(1);

        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"张三\",\"phonenumber\":\"13800138000\",\"password\":\"abc12345\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"],\"deptId\":99,\"postIds\":[7],"
                                + "\"email\":\"a@b.com\",\"status\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).insertUser(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getUserName()).isEqualTo("u_00138000");
        assertThat(saved.getNickName()).isEqualTo("张三");
        assertThat(saved.getPhonenumber()).isEqualTo("13800138000");
        assertThat(saved.getPassword()).isEqualTo("ENC_HASH");
        assertThat(saved.getStatus()).isEqualTo("0");
        assertThat(saved.getCreateBy()).isEqualTo(OPERATOR);
        assertThat(saved.getDeptId()).isNull();
        assertThat(saved.getPostIds()).isNull();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getRoleIds()).containsExactly(CHECKIN_ROLE_ID);
    }

    @Test
    @DisplayName("POST：内置超管可以任命管理员")
    void addAcceptsAdminIdentityForSuperAdmin() throws Exception
    {
        // 内置超管 userId = 1：assertCanGrantAdmin 必须放行
        stubLoginUser(1L);
        securityUtils.when(() -> SecurityUtils.isAdmin(1L)).thenReturn(true);
        securityUtils.when(() -> SecurityUtils.encryptPassword("abc12345")).thenReturn("ENC_HASH");
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_ADMIN))).thenReturn(new Long[] { ADMIN_ROLE_ID });
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(true);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
        when(userService.insertUser(any(SysUser.class))).thenReturn(1);

        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"副管理员\",\"phonenumber\":\"13800138001\","
                                + "\"password\":\"abc12345\",\"roleKeys\":[\"admin\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).insertUser(captor.capture());
        assertThat(captor.getValue().getRoleIds()).containsExactly(ADMIN_ROLE_ID);
    }

    @Test
    @DisplayName("POST：非内置超管不能任命管理员")
    void addRejectsAdminIdentityForNonSuperAdmin()
    {
        stubLoginUser(LOGIN_USER_ID);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_ADMIN))).thenReturn(new Long[] { ADMIN_ROLE_ID });

        assertThatThrownBy(() -> controller.add(staffRequest("越权", "13800138002",
                "abc12345", StaffRoleGuard.ROLE_ADMIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("只有超级管理员可以授予管理员身份");

        verify(userService, never()).insertUser(any());
    }

    @Test
    @DisplayName("POST：姓名为空时拒绝")
    void addRejectsBlankNickName()
    {
        assertThatThrownBy(() -> controller.add(staffRequest("", "13800138000",
                "abc12345", StaffRoleGuard.ROLE_CHECKIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请填写姓名");
    }

    @Test
    @DisplayName("POST：姓名超长时拒绝")
    void addRejectsTooLongNickName()
    {
        assertThatThrownBy(() -> controller.add(staffRequest("名".repeat(31), "13800138000",
                "abc12345", StaffRoleGuard.ROLE_CHECKIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("姓名不能超过 30 个字符");
    }

    @Test
    @DisplayName("POST：手机号格式错误时拒绝")
    void addRejectsMalformedPhone()
    {
        assertThatThrownBy(() -> controller.add(staffRequest("张三", "12345",
                "abc12345", StaffRoleGuard.ROLE_CHECKIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("手机号格式不正确");
    }

    @Test
    @DisplayName("POST：密码不含数字时抛业务异常")
    void addRejectsWeakPassword()
    {
        SysUser user = staffRequest("张三", "13800138000", "abcdefgh", StaffRoleGuard.ROLE_CHECKIN);

        assertThatThrownBy(() -> controller.add(user))
                .isInstanceOf(ServiceException.class)
                .hasMessage("密码需同时包含字母和数字");
    }

    @Test
    @DisplayName("POST：手机号已被占用时拒绝建号")
    void addRejectsDuplicatedPhone() throws Exception
    {
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN))).thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(false);

        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickName\":\"张三\",\"phonenumber\":\"13800138000\",\"password\":\"abc12345\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该手机号已被其它账号使用"));

        verify(userService, never()).insertUser(any());
    }

    @Test
    @DisplayName("PUT：编辑姓名/手机号/身份后吊销目标账号旧令牌")
    void editUpdatesProfileAndRolesAndRevokesSession() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(9L);
        target.setUserName("u_00138000");
        target.setPhonenumber("13800138000");
        when(userService.selectUserById(9L)).thenReturn(target);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_VERIFY)))
                .thenReturn(new Long[] { VERIFY_ROLE_ID });
        when(staffRoleGuard.hasAdminRole(9L)).thenReturn(false);
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(true);
        when(userService.updateUser(any(SysUser.class))).thenReturn(1);

        LoginUser oldSession = new LoginUser(9L, null, target, Set.of());
        oldSession.setToken("staff-token");
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*")).thenReturn(List.of("login_tokens:ccc"));
        when(redisCache.getCacheObject("login_tokens:ccc")).thenReturn(oldSession);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"nickName\":\"张三（兑奖）\",\"phonenumber\":\"13900139000\","
                                + "\"roleKeys\":[\"kcon_verify_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUser(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getNickName()).isEqualTo("张三（兑奖）");
        assertThat(saved.getPhonenumber()).isEqualTo("13900139000");
        assertThat(saved.getRoleIds()).containsExactly(VERIFY_ROLE_ID);
        assertThat(saved.getUserName()).isEqualTo("u_00138000");
        assertThat(saved.getUpdateBy()).isEqualTo(OPERATOR);

        verify(staffRoleGuard).assertManagedAccount(9L);
        verify(tokenService).delLoginUser("staff-token");
    }

    @Test
    @DisplayName("PUT：编辑自己不吊销令牌（否则刚保存就被踢回登录页）")
    void editSelfKeepsSession() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(LOGIN_USER_ID);
        target.setPhonenumber("13800138000");
        when(userService.selectUserById(LOGIN_USER_ID)).thenReturn(target);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(staffRoleGuard.hasAdminRole(LOGIN_USER_ID)).thenReturn(false);
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(true);
        when(userService.updateUser(any(SysUser.class))).thenReturn(1);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":5,\"nickName\":\"张三\",\"phonenumber\":\"13800138000\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(redisCache, never()).keys(anyString());
    }

    @Test
    @DisplayName("PUT：缺账号 ID 时抛业务异常")
    void editRejectsMissingUserId()
    {
        SysUser request = new SysUser();
        request.setNickName("张三");
        request.setPhonenumber("13800138000");

        assertThatThrownBy(() -> controller.edit(request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("缺少账号 ID");
    }

    @Test
    @DisplayName("PUT：目标账号不存在时返回业务错误")
    void editRejectsUnknownAccount() throws Exception
    {
        when(userService.selectUserById(9L)).thenReturn(null);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"nickName\":\"张三\",\"phonenumber\":\"13800138000\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("账号不存在"));

        verify(userService, never()).updateUser(any());
    }

    @Test
    @DisplayName("PUT：不能取消自己的管理员身份")
    void editRejectsCancellingOwnAdminRole() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(LOGIN_USER_ID);
        when(userService.selectUserById(LOGIN_USER_ID)).thenReturn(target);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(staffRoleGuard.hasAdminRole(LOGIN_USER_ID)).thenReturn(true);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":5,\"nickName\":\"张三\",\"phonenumber\":\"13800138000\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能取消自己的管理员身份"));

        verify(userService, never()).updateUser(any());
    }

    @Test
    @DisplayName("PUT：不能取消内置超管的管理员身份")
    void editRejectsCancellingBuiltinAdminRole() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(1L);
        when(userService.selectUserById(1L)).thenReturn(target);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(staffRoleGuard.hasAdminRole(1L)).thenReturn(true);
        securityUtils.when(() -> SecurityUtils.isAdmin(1L)).thenReturn(true);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"nickName\":\"管理员\",\"phonenumber\":\"13000000000\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能取消内置超级管理员的管理员身份"));

        verify(userService, never()).updateUser(any());
    }

    @Test
    @DisplayName("PUT：手机号已被其他账号占用时拒绝编辑")
    void editRejectsDuplicatedPhone() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(9L);
        when(userService.selectUserById(9L)).thenReturn(target);
        when(staffRoleGuard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .thenReturn(new Long[] { CHECKIN_ROLE_ID });
        when(staffRoleGuard.hasAdminRole(9L)).thenReturn(false);
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(false);

        mockMvc.perform(put("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"nickName\":\"张三\",\"phonenumber\":\"13800138000\","
                                + "\"roleKeys\":[\"kcon_checkin_staff\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该手机号已被其它账号使用"));

        verify(userService, never()).updateUser(any());
    }

    @Test
    @DisplayName("PUT /resetPwd：校验账号管理范围、重置密码并吊销旧令牌")
    void resetPwdChecksManagedAccountAndRevokesSessions() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        SysUser target = new SysUser();
        target.setUserId(9L);
        target.setUserName("u_00138000");
        when(userService.selectUserById(9L)).thenReturn(target);
        securityUtils.when(() -> SecurityUtils.encryptPassword("abc12345")).thenReturn("ENC_HASH");
        LoginUser oldSession = new LoginUser(9L, null, target, Set.of());
        oldSession.setToken("old-token");
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*")).thenReturn(List.of("login_tokens:aaa"));
        when(redisCache.getCacheObject("login_tokens:aaa")).thenReturn(oldSession);

        mockMvc.perform(put("/admin/staff/resetPwd")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"password\":\"abc12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("密码已重置，该账号需要重新登录"));

        verify(staffRoleGuard).assertManagedAccount(9L);
        verify(userService).resetUserPwd(9L, "ENC_HASH");
        verify(tokenService).delLoginUser("old-token");
    }

    @Test
    @DisplayName("PUT /resetPwd：缺账号 ID 时抛业务异常")
    void resetPwdRejectsMissingUserId()
    {
        SysUser request = new SysUser();
        request.setPassword("abc12345");

        assertThatThrownBy(() -> controller.resetPwd(request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("缺少账号 ID");

        verify(staffRoleGuard, never()).assertManagedAccount(any());
    }

    @Test
    @DisplayName("PUT /changeStatus：不能停用当前登录账号")
    void changeStatusRejectsDisablingSelf() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);

        mockMvc.perform(put("/admin/staff/changeStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":5,\"status\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能停用当前登录的账号"));

        verify(staffRoleGuard, never()).assertManagedAccount(any());
        verify(userService, never()).updateUserStatus(any());
    }

    @Test
    @DisplayName("PUT /changeStatus：不能停用内置超管")
    void changeStatusRejectsBuiltinAdmin() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        securityUtils.when(() -> SecurityUtils.isAdmin(1L)).thenReturn(true);

        mockMvc.perform(put("/admin/staff/changeStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"status\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能停用内置超级管理员账号"));

        verify(userService, never()).updateUserStatus(any());
    }

    @Test
    @DisplayName("PUT /changeStatus：状态值非法时拒绝")
    void changeStatusRejectsInvalidStatus() throws Exception
    {
        mockMvc.perform(put("/admin/staff/changeStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"status\":\"2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("状态只能是 0（正常）或 1（停用）"));
    }

    @Test
    @DisplayName("PUT /changeStatus：停用他人时校验账号范围并吊销令牌")
    void changeStatusDisablesStaffAndRevokesSession() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        when(userService.updateUserStatus(any(SysUser.class))).thenReturn(1);
        SysUser staff = new SysUser();
        staff.setUserId(9L);
        LoginUser oldSession = new LoginUser(9L, null, staff, Set.of());
        oldSession.setToken("staff-token");
        when(redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*")).thenReturn(List.of("login_tokens:bbb"));
        when(redisCache.getCacheObject("login_tokens:bbb")).thenReturn(oldSession);

        mockMvc.perform(put("/admin/staff/changeStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"status\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(staffRoleGuard).assertManagedAccount(9L);
        verify(tokenService).delLoginUser("staff-token");
    }

    @Test
    @DisplayName("PUT /changeStatus：启用时不吊销令牌")
    void changeStatusEnableKeepsSessions() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        when(userService.updateUserStatus(any(SysUser.class))).thenReturn(1);

        mockMvc.perform(put("/admin/staff/changeStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"status\":\"0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(staffRoleGuard).assertManagedAccount(9L);
        verify(redisCache, never()).keys(anyString());
    }

    @Test
    @DisplayName("DELETE：不能删除当前登录账号")
    void removeRejectsDeletingSelf() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);

        mockMvc.perform(delete("/admin/staff/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能删除当前登录的账号"));

        verify(userService, never()).deleteUserByIds(any(Long[].class));
    }

    @Test
    @DisplayName("DELETE：不能删除内置超管")
    void removeRejectsBuiltinAdmin() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        securityUtils.when(() -> SecurityUtils.isAdmin(1L)).thenReturn(true);

        mockMvc.perform(delete("/admin/staff/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("不能删除内置超级管理员账号"));

        verify(userService, never()).deleteUserByIds(any(Long[].class));
    }

    @Test
    @DisplayName("DELETE：批量删除前逐个校验账号范围，并吊销各账号令牌")
    void removeChecksEveryUserAndRevokesSessions() throws Exception
    {
        stubLoginUser(LOGIN_USER_ID);
        when(userService.deleteUserByIds(any(Long[].class))).thenReturn(2);

        mockMvc.perform(delete("/admin/staff/9,10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(staffRoleGuard).assertManagedAccount(9L);
        verify(staffRoleGuard).assertManagedAccount(10L);
        verify(userService).deleteUserByIds(any(Long[].class));
    }

    @Test
    @DisplayName("DELETE：目标不在账号管理范围时拒绝删除")
    void removeRejectsNonManagedAccount()
    {
        stubLoginUser(LOGIN_USER_ID);
        doThrow(new ServiceException("只能操作管理员或现场工作人员账号"))
                .when(staffRoleGuard).assertManagedAccount(9L);

        assertThatThrownBy(() -> controller.remove(new Long[] { 9L }))
                .isInstanceOf(ServiceException.class)
                .hasMessage("只能操作管理员或现场工作人员账号");

        verify(userService, never()).deleteUserByIds(any(Long[].class));
    }

    private SysRole role(Long roleId, String roleKey, String roleName)
    {
        SysRole role = new SysRole(roleId);
        role.setRoleKey(roleKey);
        role.setRoleName(roleName);
        role.setStatus("0");
        return role;
    }

    private Map<String, Object> roleOption(String roleKey, String roleName, Long roleId)
    {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("roleKey", roleKey);
        option.put("roleName", roleName);
        option.put("roleId", roleId);
        return option;
    }

    private SysUser staffRequest(String nickName, String phone, String password, String roleKey)
    {
        SysUser user = new SysUser();
        user.setNickName(nickName);
        user.setPhonenumber(phone);
        user.setPassword(password);
        user.setRoleKeys(List.of(roleKey));
        return user;
    }

    private void stubLoginUser(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(OPERATOR);
        securityUtils.when(SecurityUtils::getLoginUser)
                .thenReturn(new LoginUser(userId, null, user, Set.of()));
    }
}
