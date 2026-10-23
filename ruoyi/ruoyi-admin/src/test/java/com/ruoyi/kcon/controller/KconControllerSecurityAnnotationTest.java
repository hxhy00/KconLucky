package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Anonymous;

/**
 * 权限注解回归测试：扫描 kcon 包下所有 @RestController，防止以后新增接口漏挂 @PreAuthorize。
 *
 * <p>约定：
 * <ul>
 *   <li>除 {@link CheckinAppController}、{@link DrawScreenController} 两个明确匿名类外，
 *       每个带 Mapping 注解的方法都必须有 @PreAuthorize；</li>
 *   <li>匿名类必须类级标注 @Anonymous，且其方法不得挂需要登录的权限注解。</li>
 * </ul>
 */
class KconControllerSecurityAnnotationTest
{
    private static final String CONTROLLER_PACKAGE_PATH = "classpath*:com/ruoyi/kcon/controller/*.class";

    private static final Set<String> EXPECTED_ANONYMOUS_CONTROLLERS = Set.of(
            "CheckinAppController", "DrawScreenController");

    private static final Set<Class<? extends Annotation>> MAPPING_ANNOTATIONS = Set.of(
            GetMapping.class, PostMapping.class, PutMapping.class, DeleteMapping.class,
            PatchMapping.class, RequestMapping.class);

    @Test
    @DisplayName("包扫描能找到全部 kcon 控制器")
    void scansAllKconControllers() throws Exception
    {
        Set<Class<?>> controllers = scanControllers();

        assertThat(controllers).hasSizeGreaterThanOrEqualTo(15);
        assertThat(controllers).extracting(Class::getSimpleName).contains(
                "AttendeeController", "BenefitGrantController", "ChannelMappingController",
                "CheckinAppController", "DrawAdminController", "DrawScreenController",
                "DrawSettingController", "ExportController", "MigrateController", "PrizeController",
                "PrizeGrantController", "SessionController", "StaffController", "VerifyController",
                "VerifyLogController");
    }

    @Test
    @DisplayName("只有 CheckinAppController / DrawScreenController 是匿名控制器")
    void onlyCheckinAndScreenAreAnonymous() throws Exception
    {
        Set<String> annotated = new TreeSet<>();
        for (Class<?> controller : scanControllers())
        {
            if (controller.isAnnotationPresent(Anonymous.class))
            {
                annotated.add(controller.getSimpleName());
            }
        }

        assertThat(annotated).containsExactlyInAnyOrderElementsOf(EXPECTED_ANONYMOUS_CONTROLLERS);
    }

    @Test
    @DisplayName("非匿名控制器的每个 Mapping 方法都必须有 @PreAuthorize")
    void everyMappedMethodOnSecuredControllerHasPreAuthorize() throws Exception
    {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scanControllers())
        {
            if (isAnonymous(controller))
            {
                continue;
            }
            for (Method method : controller.getDeclaredMethods())
            {
                if (isMapped(method) && !method.isAnnotationPresent(PreAuthorize.class))
                {
                    violations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(violations)
                .describedAs("以下接口缺少 @PreAuthorize，任何人都能直接访问")
                .isEmpty();
    }

    @Test
    @DisplayName("匿名控制器的方法不得挂 @PreAuthorize（免登录接口不能混入权限校验）")
    void anonymousControllerMethodsHaveNoPreAuthorize() throws Exception
    {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : scanControllers())
        {
            if (!isAnonymous(controller))
            {
                continue;
            }
            if (controller.isAnnotationPresent(PreAuthorize.class))
            {
                violations.add(controller.getSimpleName() + "（类级 @PreAuthorize）");
            }
            for (Method method : controller.getDeclaredMethods())
            {
                if (method.isAnnotationPresent(PreAuthorize.class))
                {
                    violations.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(violations).describedAs("匿名接口不应挂 @PreAuthorize").isEmpty();
    }

    private boolean isAnonymous(Class<?> controller)
    {
        return controller.isAnnotationPresent(Anonymous.class)
                || EXPECTED_ANONYMOUS_CONTROLLERS.contains(controller.getSimpleName());
    }

    private boolean isMapped(Method method)
    {
        for (Annotation annotation : method.getAnnotations())
        {
            if (MAPPING_ANNOTATIONS.contains(annotation.annotationType()))
            {
                return true;
            }
        }
        return false;
    }

    private Set<Class<?>> scanControllers() throws IOException, ClassNotFoundException
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(CONTROLLER_PACKAGE_PATH);
        Set<Class<?>> controllers = new LinkedHashSet<>();
        for (Resource resource : resources)
        {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".class") || filename.contains("$"))
            {
                continue;
            }
            String className = "com.ruoyi.kcon.controller."
                    + filename.substring(0, filename.length() - ".class".length());
            Class<?> type = Class.forName(className);
            if (type.isAnnotationPresent(RestController.class))
            {
                controllers.add(type);
            }
        }
        return controllers;
    }
}
