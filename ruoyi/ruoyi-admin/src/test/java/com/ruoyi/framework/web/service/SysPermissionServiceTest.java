package com.ruoyi.framework.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 权限聚合测试。
 *
 * 重点回归：内置超管角色（role_id=1）被授予非内置账号时的权限口径。
 * RuoYi 原实现按「角色是否内置」过滤，会把该角色整体跳过，
 * 结果是「身份=管理员」的新账号权限集为空，登录后每个页面都提示「当前操作没有权限」。
 */
@ExtendWith(MockitoExtension.class)
class SysPermissionServiceTest
{
    @Mock
    private ISysRoleService roleService;

    @Mock
    private ISysMenuService menuService;

    @InjectMocks
    private SysPermissionService permissionService;

    @Test
    @DisplayName("非内置账号绑定管理员角色时获得全量权限")
    void adminRoleGrantedToOtherUserGetsAllPermission()
    {
        SysUser user = user(101L);
        user.setRoles(List.of(role(1L, "admin")));

        assertThat(permissionService.getMenuPermission(user)).containsExactly(Constants.ALL_PERMISSION);
        // 不走菜单聚合：否则会被「内置角色跳过」逻辑吞掉
        verifyNoInteractions(menuService);
    }

    @Test
    @DisplayName("岗位角色按菜单授权聚合权限")
    void staffRoleAggregatesMenuPermissions()
    {
        SysUser user = user(102L);
        user.setRoles(List.of(role(3L, "kcon_checkin_staff")));
        when(menuService.selectMenuPermsByRoleId(3L)).thenReturn(Set.of("kcon:attendee:list", "kcon:benefit:grant"));

        assertThat(permissionService.getMenuPermission(user))
                .containsExactlyInAnyOrder("kcon:attendee:list", "kcon:benefit:grant");
    }

    @Test
    @DisplayName("未绑定角色的账号回落到按用户查询的菜单权限")
    void userWithoutRolesFallsBackToUserMenus()
    {
        SysUser user = user(103L);
        user.setRoles(new ArrayList<>());
        when(menuService.selectMenuPermsByUserId(103L)).thenReturn(Set.of("kcon:attendee:list"));

        assertThat(permissionService.getMenuPermission(user)).containsExactly("kcon:attendee:list");
    }

    private SysUser user(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        return user;
    }

    private SysRole role(Long roleId, String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        role.setStatus("0");
        return role;
    }
}
