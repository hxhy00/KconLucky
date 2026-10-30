package com.ruoyi.framework.web.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.manager.AsyncManager;

/**
 * 登录前置校验测试。
 *
 * 重点回归：手机号格式非法、密码长度越界、账号密码为空时，
 * 应与密码错误返回同一句「用户不存在/密码错误」，而不是被兜底成「系统繁忙」。
 */
class SysLoginServiceTest
{
    private static final String UNIFIED_MESSAGE = "用户不存在/密码错误";

    private final SysLoginService loginService = new SysLoginService();

    @AfterEach
    void clearRequest()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("账号为空返回统一登录失败文案")
    void blankUsernameUsesUnifiedMessage()
    {
        assertUnified("", "Kcon2026Aa.");
    }

    @Test
    @DisplayName("密码为空返回统一登录失败文案")
    void blankPasswordUsesUnifiedMessage()
    {
        assertUnified("13000000000", "");
    }

    @Test
    @DisplayName("密码长度越界返回统一登录失败文案")
    void invalidPasswordLengthUsesUnifiedMessage()
    {
        assertUnified("13000000000", "1234");
        assertUnified("13000000000", "123456789012345678901");
    }

    @Test
    @DisplayName("非手机号账号返回统一登录失败文案")
    void nonPhoneUsernameUsesUnifiedMessage()
    {
        assertUnified("12345", "Kcon2026Aa.");
        assertUnified("KconLucky", "Kcon2026Aa.");
    }

    /**
     * 登录失败会异步写登录日志；单测里把 AsyncManager 与消息源一并 mock，
     * 只验证前置校验抛出的异常与文案，不启动真实线程池。
     */
    private void assertUnified(String username, String password)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
                MockedStatic<AsyncManager> asyncManager = mockStatic(AsyncManager.class))
        {
            MessageSource messageSource = mock(MessageSource.class);
            when(messageSource.getMessage(anyString(), any(), any())).thenReturn(UNIFIED_MESSAGE);
            springUtils.when(() -> SpringUtils.getBean(MessageSource.class)).thenReturn(messageSource);
            asyncManager.when(AsyncManager::me).thenReturn(mock(AsyncManager.class));

            assertThatThrownBy(() -> loginService.loginPreCheck(username, password))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage(UNIFIED_MESSAGE);
        }
    }
}
