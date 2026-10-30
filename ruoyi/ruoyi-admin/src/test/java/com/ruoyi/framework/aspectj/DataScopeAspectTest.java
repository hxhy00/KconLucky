package com.ruoyi.framework.aspectj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

/**
 * 数据权限切面测试。
 *
 * 重点回归：KCon「管理员身份」（role_key = admin）授予非内置超管账号时，
 * 应与内置超管一样不做数据权限过滤。否则角色权限集合为空会导致
 * 「不查询任何数据」，管理员身份账号会报「身份角色不存在」。
 */
class DataScopeAspectTest
{
    private final DataScopeAspect aspect = new DataScopeAspect();

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("内置超管不做数据权限过滤")
    void builtInAdminSkipsDataScope() throws Throwable
    {
        login(user(1L, "admin", "3"));

        SysRole entity = applyFilter();

        assertThat(entity.getParams().get(DataScopeAspect.DATA_SCOPE)).isEqualTo("");
    }

    @Test
    @DisplayName("非内置管理员身份账号不做数据权限过滤")
    void adminRoleSkipsDataScope() throws Throwable
    {
        login(user(101L, "admin", "3"));

        SysRole entity = applyFilter();

        // 修复前这里会被拼成 dept_id = 0，导致角色/账号列表查不到数据
        assertThat(entity.getParams().get(DataScopeAspect.DATA_SCOPE)).isEqualTo("");
    }

    @Test
    @DisplayName("普通岗位账号仍按数据权限拼接条件")
    void staffRoleStillAppliesDataScope() throws Throwable
    {
        login(user(102L, "kcon_checkin_staff", "3"));

        SysRole entity = applyFilter();

        assertThat((String) entity.getParams().get(DataScopeAspect.DATA_SCOPE))
                .contains("d.dept_id = 100");
    }

    private SysRole applyFilter() throws Throwable
    {
        // 切面会读取 PermissionContextHolder（依赖当前请求），单测里补一个请求上下文
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        DataScope annotation = mock(DataScope.class);
        when(annotation.permission()).thenReturn("");
        when(annotation.deptAlias()).thenReturn("d");
        when(annotation.userAlias()).thenReturn("u");

        SysRole entity = new SysRole();
        JoinPoint point = mock(JoinPoint.class);
        when(point.getArgs()).thenReturn(new Object[] { entity });

        aspect.doBefore(point, annotation);
        return entity;
    }

    private void login(SysUser user)
    {
        LoginUser loginUser = new LoginUser(user, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private SysUser user(Long userId, String roleKey, String dataScope)
    {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setDataScope(dataScope);

        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setDeptId(100L);
        user.setRoles(List.of(role));
        return user;
    }
}
