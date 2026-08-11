package com.seckill.config;

import com.seckill.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 * 用于配置全局拦截器，如JWT认证拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")  // 拦截所有路径
                .excludePathPatterns(  // 白名单：不需要认证的路径
                        "/user/login",          // 登录接口
                        "/user/register",       // 注册接口
                        "/user/captcha",        // 验证码接口
                        "/seckill/path",        // 动态URL获取接口
                        // "/goods/list",          // 商品列表页
                        "/goods/*",             // 商品详情页
                        "/order/result",        // 秒杀结果页
                        "/",                    // 首页
                        "/static/**",           // 静态资源
                        "/public/**",           // 公共资源
                        "/actuator/**",         // 健康检查
                        "/error"                // 错误页面
                );
    }
}