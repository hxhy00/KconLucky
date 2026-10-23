package com.ruoyi.kcon.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysUserService;

/**
 * 敏感操作守卫单元测试：校验对象、防爆破计数与锁定语义。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveOperationGuardTest
{
    private static final Long USER_ID = 7L;

    private static final String ACTION = "数据清场";

    /** 登录令牌里的旧散列（快照） */
    private static final String LOGIN_SNAPSHOT_HASH = "$2a$10$loginSnapshotHash";

    /** 数据库里的最新散列 */
    private static final String LATEST_HASH = "$2a$10$latestHashInDatabase";

    @Mock
    private ISysUserService sysUserService;

    @InjectMocks
    private SensitiveOperationGuard guard;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        SysUser loginSnapshot = new SysUser();
        loginSnapshot.setUserId(USER_ID);
        loginSnapshot.setUserName("kcon_admin");
        loginSnapshot.setPassword(LOGIN_SNAPSHOT_HASH);
        LoginUser loginUser = new LoginUser(USER_ID, null, loginSnapshot, Set.of());

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getLoginUser).thenReturn(loginUser);
    }

    @AfterEach
    void tearDown()
    {
        securityUtils.close();
    }

    @Test
    @DisplayName("空密码：直接提示需要输入管理员密码，不查库")
    void rejectsEmptyPasswordWithoutQueryingUser()
    {
        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请输入数据清场所需的管理员密码");

        verifyNoInteractions(sysUserService);
    }

    @Test
    @DisplayName("密码正确：通过校验，且比对的是数据库里的最新散列而非登录快照")
    void acceptsCorrectPasswordUsingLatestHashFromDatabase()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword(LATEST_HASH));
        securityUtils.when(() -> SecurityUtils.matchesPassword("P@ssw0rd123", LATEST_HASH)).thenReturn(true);

        assertThatCode(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .doesNotThrowAnyException();

        verify(sysUserService).selectUserById(USER_ID);
        // 用旧快列（LOGIN_SNAPSHOT_HASH）比对则不会命中本条 stub，测试会失败
        securityUtils.verify(() -> SecurityUtils.matchesPassword("P@ssw0rd123", LATEST_HASH));
    }

    @Test
    @DisplayName("密码错误：提示剩余可尝试次数")
    void reportsRemainingAttemptsOnWrongPassword()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword(LATEST_HASH));
        securityUtils.when(() -> SecurityUtils.matchesPassword(eq("wrong"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("wrong", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("管理员密码不正确，还可尝试 4 次");
    }

    @Test
    @DisplayName("连续输错 5 次后锁定；锁定期内即使密码正确也拒绝")
    void locksAccountAfterFiveConsecutiveFailuresAndRejectsCorrectPassword()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword(LATEST_HASH));
        securityUtils.when(() -> SecurityUtils.matchesPassword(eq("wrong"), anyString())).thenReturn(false);

        for (int i = 1; i < 5; i++)
        {
            final int remaining = 5 - i;
            assertThatThrownBy(() -> guard.verifyCurrentUserPassword("wrong", ACTION))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("管理员密码不正确，还可尝试 " + remaining + " 次");
        }

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("wrong", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("密码连续输错 5 次，已锁定 30 分钟，请稍后再试");

        // 锁定期内即使密码正确也直接拒绝，且不再做 BCrypt 比对
        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("密码连续输错 5 次，已锁定 30 分钟，请稍后再试");

        securityUtils.verify(() -> SecurityUtils.matchesPassword("P@ssw0rd123", LATEST_HASH), times(0));
        verify(sysUserService, times(5)).selectUserById(USER_ID);
    }

    @Test
    @DisplayName("成功一次后失败计数清零：再输错仍从 4 次余量开始")
    void resetsFailureCounterAfterSuccess()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword(LATEST_HASH));
        securityUtils.when(() -> SecurityUtils.matchesPassword(anyString(), anyString()))
                .thenReturn(false, true, false);

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("wrong", ACTION))
                .hasMessage("管理员密码不正确，还可尝试 4 次");

        assertThatCode(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("wrong", ACTION))
                .hasMessage("管理员密码不正确，还可尝试 4 次");
    }

    @Test
    @DisplayName("查不到账号：提示无法读取账号信息")
    void rejectsWhenUserNotFound()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无法读取当前账号信息，请重新登录后再试");
    }

    @Test
    @DisplayName("账号密码散列为空：同样提示无法读取账号信息")
    void rejectsWhenStoredPasswordIsEmpty()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword(""));

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("无法读取当前账号信息，请重新登录后再试");
    }

    @Test
    @DisplayName("散列比对抛异常：按失败处理并返回剩余次数，不泄露内部细节")
    void treatsRegexFailureAsWrongPasswordWithoutLeakingDetails()
    {
        when(sysUserService.selectUserById(USER_ID)).thenReturn(userWithPassword("not-a-bcrypt-hash"));
        securityUtils.when(() -> SecurityUtils.matchesPassword(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("bcrypt 内部散列格式错误"));

        assertThatThrownBy(() -> guard.verifyCurrentUserPassword("P@ssw0rd123", ACTION))
                .isInstanceOf(ServiceException.class)
                .hasMessage("管理员密码不正确，还可尝试 4 次")
                .hasMessageNotContaining("bcrypt")
                .hasMessageNotContaining("内部散列格式错误");
    }

    private SysUser userWithPassword(String password)
    {
        SysUser user = new SysUser();
        user.setUserId(USER_ID);
        user.setPassword(password);
        return user;
    }
}
