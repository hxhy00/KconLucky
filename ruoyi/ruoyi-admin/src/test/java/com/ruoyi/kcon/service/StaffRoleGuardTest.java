package com.ruoyi.kcon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysRoleService;

/**
 * 账号身份守卫单元测试：角色解析、身份白名单、岗位判定与账号管理范围判定。
 */
@ExtendWith(MockitoExtension.class)
class StaffRoleGuardTest
{
    private static final Long ADMIN_ROLE_ID = 1L;

    private static final Long CHECKIN_ROLE_ID = 11L;

    private static final Long DRAW_ROLE_ID = 12L;

    private static final Long VERIFY_ROLE_ID = 13L;

    @Mock
    private ISysRoleService roleService;

    @InjectMocks
    private StaffRoleGuard guard;

    @Test
    @DisplayName("身份列表为 null：提示请至少选择一个身份")
    void rejectsNullRoleKeys()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请至少选择一个身份");
    }

    @Test
    @DisplayName("身份列表为空：提示请至少选择一个身份")
    void rejectsEmptyRoleKeys()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(Collections.emptyList()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请至少选择一个身份");
    }

    @Test
    @DisplayName("身份角色不存在：提示检查初始化脚本")
    void rejectsMissingRoleWithSqlHint()
    {
        when(roleService.selectRoleAll()).thenReturn(List.of(
                role(ADMIN_ROLE_ID, StaffRoleGuard.ROLE_ADMIN, "管理员", "0"),
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "0"),
                role(DRAW_ROLE_ID, StaffRoleGuard.ROLE_DRAW, "抽奖台", "0")));

        assertThatThrownBy(() -> guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_VERIFY)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("身份「兑奖台」不存在，请检查初始化脚本是否已执行");
    }

    @Test
    @DisplayName("身份角色已停用：提示先在角色管理中启用")
    void rejectsDisabledRole()
    {
        when(roleService.selectRoleAll()).thenReturn(List.of(
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "1")));

        assertThatThrownBy(() -> guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("身份「签到台」已停用，请先在角色管理中启用");
    }

    @Test
    @DisplayName("正常解析：角色 ID 按请求顺序返回")
    void resolvesRoleIdsInRequestOrder()
    {
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        Long[] ids = guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_CHECKIN));

        assertThat(ids).containsExactly(DRAW_ROLE_ID, CHECKIN_ROLE_ID);
    }

    @Test
    @DisplayName("管理员在白名单内：可解析为管理员角色 ID")
    void resolvesAdminRole()
    {
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        Long[] ids = guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_ADMIN));

        assertThat(ids).containsExactly(ADMIN_ROLE_ID);
    }

    @Test
    @DisplayName("白名单之外的 role_key：直接拒绝，且不查角色表")
    void rejectsKeyOutsideWhitelistBeforeTouchingMapper()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(List.of("common")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的身份：common");

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("重复身份去重后只解析一次，返回单个角色 ID")
    void dedupesRepeatedRoleKeys()
    {
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        Long[] ids = guard.resolveRoleIds(List.of(
                StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_CHECKIN));

        assertThat(ids).containsExactly(DRAW_ROLE_ID, CHECKIN_ROLE_ID);
    }

    @Test
    @DisplayName("同一 role_key 存在多条：拒绝并提示先合并角色")
    void rejectsDuplicateRoleKey()
    {
        when(roleService.selectRoleAll()).thenReturn(List.of(
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "0"),
                role(21L, StaffRoleGuard.ROLE_CHECKIN, "签到台（旧）", "0")));

        assertThatThrownBy(() -> guard.resolveRolePublic(StaffRoleGuard.ROLE_CHECKIN))
                .isInstanceOf(ServiceException.class)
                .hasMessage("存在多个「签到台」角色，请先在角色管理中合并后再试");
    }

    @Test
    @DisplayName("超管账号（userId=1）恒不是岗位账号，且不查角色")
    void adminIsNeverStaff()
    {
        assertThat(guard.isStaff(1L)).isFalse();

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("userId 为空：不是岗位账号")
    void nullUserIdIsNotStaff()
    {
        assertThat(guard.isStaff(null)).isFalse();

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("未绑定任何角色：不是岗位账号")
    void userWithoutRolesIsNotStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(Collections.emptyList());

        assertThat(guard.isStaff(5L)).isFalse();
    }

    @Test
    @DisplayName("绑定任一岗位角色：判定为岗位账号")
    void userWithAnyStaffRoleIsStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(99L, DRAW_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        assertThat(guard.isStaff(5L)).isTrue();
    }

    @Test
    @DisplayName("角色查询抛业务异常：按非岗位账号处理，不向上抛")
    void roleLookupFailureFallsBackToNotStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenThrow(new ServiceException("角色配置损坏"));

        assertThat(guard.isStaff(5L)).isFalse();
    }

    @Test
    @DisplayName("只绑定管理员身份：属于账号管理范围，但不属于现场岗位")
    void adminOnlyAccountIsManagedButNotStaff()
    {
        when(roleService.selectRoleListByUserId(7L)).thenReturn(List.of(ADMIN_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        assertThat(guard.isManagedAccount(7L)).isTrue();
        assertThat(guard.isStaff(7L)).isFalse();
        assertThat(guard.hasAdminRole(7L)).isTrue();
    }

    @Test
    @DisplayName("内置超管无需查角色即属于账号管理范围")
    void builtinAdminIsManagedWithoutRoleQuery()
    {
        assertThat(guard.isManagedAccount(1L)).isTrue();
        assertThat(guard.hasAdminRole(1L)).isTrue();

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("assertManagedAccount：范围外账号直接拒绝")
    void assertManagedAccountRejectsOutsider()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(99L));
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        assertThatThrownBy(() -> guard.assertManagedAccount(5L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("只能操作管理员或现场工作人员账号");
    }

    @Test
    @DisplayName("assertManagedAccount：管理员与岗位账号都通过")
    void assertManagedAccountAcceptsManagedAccounts()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(VERIFY_ROLE_ID));
        when(roleService.selectRoleListByUserId(6L)).thenReturn(List.of(ADMIN_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        assertThatCode(() -> guard.assertManagedAccount(5L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertManagedAccount(6L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("managedRolesOf：按管理员→签到台→抽奖台→兑奖台顺序返回已绑定身份")
    void managedRolesOfKeepsDisplayOrder()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(VERIFY_ROLE_ID, ADMIN_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        List<SysRole> roles = guard.managedRolesOf(5L);

        assertThat(roles).extracting(SysRole::getRoleKey).containsExactly(
                StaffRoleGuard.ROLE_ADMIN, StaffRoleGuard.ROLE_VERIFY);
    }

    @Test
    @DisplayName("selectableRoles：按管理员、签到台、抽奖台、兑奖台顺序返回 key/name/id")
    void selectableRolesKeepsOrderAndFields()
    {
        when(roleService.selectRoleAll()).thenReturn(allManagedRoles());

        List<Map<String, Object>> roles = guard.selectableRoles();

        assertThat(roles).hasSize(4);
        assertThat(roles).extracting(m -> m.get("roleKey")).containsExactly(
                StaffRoleGuard.ROLE_ADMIN, StaffRoleGuard.ROLE_CHECKIN,
                StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_VERIFY);
        assertThat(roles).extracting(m -> m.get("roleName"))
                .containsExactly("管理员", "签到台", "抽奖台", "兑奖台");
        assertThat(roles).extracting(m -> m.get("roleId"))
                .containsExactly(ADMIN_ROLE_ID, CHECKIN_ROLE_ID, DRAW_ROLE_ID, VERIFY_ROLE_ID);
        assertThat(roles.get(0).keySet()).containsExactly("roleKey", "roleName", "roleId");
    }

    private List<SysRole> allManagedRoles()
    {
        return List.of(
                role(ADMIN_ROLE_ID, StaffRoleGuard.ROLE_ADMIN, "管理员", "0"),
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "0"),
                role(DRAW_ROLE_ID, StaffRoleGuard.ROLE_DRAW, "抽奖台", "0"),
                role(VERIFY_ROLE_ID, StaffRoleGuard.ROLE_VERIFY, "兑奖台", "0"));
    }

    private SysRole role(Long roleId, String roleKey, String roleName, String status)
    {
        SysRole role = new SysRole(roleId);
        role.setRoleKey(roleKey);
        role.setRoleName(roleName);
        role.setStatus(status);
        return role;
    }
}
