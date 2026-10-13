package com.ruoyi.framework.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.interceptor.RepeatSubmitInterceptor;

/**
 * 通用配置
 * 
 * @author ruoyi
 */
@Configuration
public class ResourcesConfig implements WebMvcConfigurer
{
    @Autowired
    private RepeatSubmitInterceptor repeatSubmitInterceptor;

    /**
     * 允许跨域的来源白名单（逗号分隔）。为空 = 不开放跨域，
     * 三端均走 nginx 同源 /prod-api/，不需要 CORS。
     */
    @Value("${cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        /** 本地文件上传路径 */
        registry.addResourceHandler(Constants.RESOURCE_PREFIX + "/**")
                .addResourceLocations("file:" + RuoYiConfig.getProfile() + "/");

        /** swagger配置 */
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                .setCacheControl(CacheControl.maxAge(5, TimeUnit.HOURS).cachePublic());
    }

    /**
     * 自定义拦截规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/**");
    }

    /**
     * 跨域配置
     *
     * 三端前端（管理端 / 大屏 / 签到 H5）均通过 nginx 的同源 /prod-api/ 反代访问后端，
     * 开发期 vite proxy 也是同源转发，**正常链路不需要 CORS**。
     * 因此默认不开放任何跨域来源，避免大屏这类免登录写接口被第三方网页跨站调用。
     *
     * 如确有跨域调用方（如公网部署后管理端与后端分域名），
     * 在 application.yml 配 cors.allowed-origins 为逗号分隔的来源白名单即可。
     */
    @Bean
    public CorsFilter corsFilter()
    {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = parseAllowedOrigins();
        if (!origins.isEmpty())
        {
            config.setAllowedOriginPatterns(origins);
            config.addAllowedHeader("*");
            config.addAllowedMethod("*");
            config.setMaxAge(1800L);
        }

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 解析 cors.allowed-origins 配置，格式：逗号分隔的来源，如 http://a.com,http://b.com
     */
    private List<String> parseAllowedOrigins()
    {
        String configured = StringUtils.trimToEmpty(corsAllowedOrigins);
        if (StringUtils.isEmpty(configured))
        {
            return Collections.emptyList();
        }
        List<String> origins = new ArrayList<>();
        for (String origin : configured.split(","))
        {
            String trimmed = StringUtils.trimToEmpty(origin);
            if (StringUtils.isNotEmpty(trimmed))
            {
                origins.add(trimmed);
            }
        }
        return origins;
    }
}