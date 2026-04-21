package com.yzh.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMVC 配置类：负责注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                // 拦截所有对话及后续受业务保护的请求
                .addPathPatterns("/api/chat/**", "/api/merchant/**", "/api/lottery/preheat", "/api/lottery/publish")
                // 放行登录注册
                .excludePathPatterns("/api/user/login", "/api/user/register")
                // 放行静态资源
                .excludePathPatterns("/**/*.html", "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.jpg");
    }
}
