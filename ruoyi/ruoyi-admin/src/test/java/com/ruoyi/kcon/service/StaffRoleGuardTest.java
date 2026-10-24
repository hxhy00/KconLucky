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
 * 现场工作人员岗位守卫单元测试：角色解析、建号岗位校验、工作人员判定。
 */
@ExtendWith(MockitoExtension.class)
class StaffRoleGuardTest
{
    private static final Long CHECKIN_ROLE_ID = 11L;

    private static final Long DRAW_ROLE_ID = 12L;

    private static final Long VERIFY_ROLE_ID = 13L;

    @Mock
    private ISysRoleService roleService;

    @InjectMocks
    private StaffRoleGuard guard;

    @Test
    @DisplayName("岗位列表为 null：提示请至少选择一个岗位")
    void rejectsNullRoleKeys()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请至少选择一个岗位");
    }

    @Test
    @DisplayName("岗位列表为空：提示请至少选择一个岗位")
    void rejectsEmptyRoleKeys()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(Collections.emptyList()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请至少选择一个岗位");
    }

    @Test
    @DisplayName("岗位角色不存在：提示检查 kcon_menu.sql")
    void rejectsMissingRoleWithSqlHint()
    {
        when(roleService.selectRoleAll()).thenReturn(List.of(
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "0"),
                role(DRAW_ROLE_ID, StaffRoleGuard.ROLE_DRAW, "抽奖台", "0")));

        assertThatThrownBy(() -> guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_VERIFY)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("岗位「兑奖台」不存在，请检查 kcon_menu.sql 是否已执行");
    }

    @Test
    @DisplayName("岗位角色已停用：提示先在角色管理中启用")
    void rejectsDisabledRole()
    {
        when(roleService.selectRoleAll()).thenReturn(List.of(
                role(CHECKIN_ROLE_ID, StaffRoleGuard.ROLE_CHECKIN, "签到台", "1")));

        assertThatThrownBy(() -> guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_CHECKIN)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("岗位「签到台」已停用，请先在角色管理中启用");
    }

    @Test
    @DisplayName("正常解析：角色 ID 按请求顺序返回")
    void resolvesRoleIdsInRequestOrder()
    {
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

        Long[] ids = guard.resolveRoleIds(List.of(StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_CHECKIN));

        assertThat(ids).containsExactly(DRAW_ROLE_ID, CHECKIN_ROLE_ID);
    }

    @Test
    @DisplayName("非岗位白名单的 role_key：直接拒绝，且不查角色表")
    void rejectsKeyOutsideWhitelistBeforeTouchingMapper()
    {
        assertThatThrownBy(() -> guard.resolveRoleIds(List.of("admin")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的岗位：admin");

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("重复岗位去重后只解析一次，返回单个角色 ID")
    void dedupesRepeatedRoleKeys()
    {
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

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
    @DisplayName("超管账号（userId=1）恒不是工作人员，且不查角色")
    void adminIsNeverStaff()
    {
        assertThat(guard.isStaff(1L)).isFalse();

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("userId 为空：不是工作人员")
    void nullUserIdIsNotStaff()
    {
        assertThat(guard.isStaff(null)).isFalse();

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("未绑定任何角色：不是工作人员")
    void userWithoutRolesIsNotStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(Collections.emptyList());

        assertThat(guard.isStaff(5L)).isFalse();
    }

    @Test
    @DisplayName("绑定任一岗位角色：判定为工作人员")
    void userWithAnyStaffRoleIsStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(99L, DRAW_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

        assertThat(guard.isStaff(5L)).isTrue();
    }

    @Test
    @DisplayName("角色查询抛业务异常：按非工作人员处理，不向上抛")
    void roleLookupFailureFallsBackToNotStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenThrow(new ServiceException("角色配置损坏"));

        assertThat(guard.isStaff(5L)).isFalse();
    }

    @Test
    @DisplayName("assertStaff：非工作人员直接拒绝")
    void assertStaffRejectsNonStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(99L));
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

        assertThatThrownBy(() -> guard.assertStaff(5L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("只能操作现场工作人员账号");
    }

    @Test
    @DisplayName("assertStaff：工作人员通过")
    void assertStaffAcceptsStaff()
    {
        when(roleService.selectRoleListByUserId(5L)).thenReturn(List.of(VERIFY_ROLE_ID));
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

        assertThatCode(() -> guard.assertStaff(5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("selectableRoles：按签到台、抽奖台、兑奖台顺序返回 key/name/id")
    void selectableRolesKeepsOrderAndFields()
    {
        when(roleService.selectRoleAll()).thenReturn(allStaffRoles());

        List<Map<String, Object>> roles = guard.selectableRoles();

        assertThat(roles).hasSize(3);
        assertThat(roles).extracting(m -> m.get("roleKey")).containsExactly(
                StaffRoleGuard.ROLE_CHECKIN, StaffRoleGuard.ROLE_DRAW, StaffRoleGuard.ROLE_VERIFY);
        assertThat(roles).extracting(m -> m.get("roleName")).containsExactly("签到台", "抽奖台", "兑奖台");
        assertThat(roles).extracting(m -> m.get("roleId"))
                .containsExactly(CHECKIN_ROLE_ID, DRAW_ROLE_ID, VERIFY_ROLE_ID);
        assertThat(roles.get(0).keySet()).containsExactly("roleKey", "roleName", "roleId");
    }

    private List<SysRole> allStaffRoles()
    {
        return List.of(
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
