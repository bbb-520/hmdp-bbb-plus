package com.example.demo.config;

import com.example.demo.utils.LoginInterceptor;
import com.example.demo.utils.RefreshFockenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };
    public void addInterceptors(InterceptorRegistry registration){

        registration.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                )
                .excludePathPatterns(SWAGGER_PATHS)
                .order(1);
        registration.addInterceptor(new RefreshFockenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }

}
