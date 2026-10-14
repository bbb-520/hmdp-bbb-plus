package com.example.demo.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ① 检查 ThreadLocal 是否有用户
 * ② 没有 → 401 拦截
 * ③ 有 → 放行
 */
public class LoginInterceptor implements HandlerInterceptor {
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        if (UserHolder.getUser() == null) {
            //拦截
            response.setStatus(401);
            return false;
        }

        return true;
    }
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws  Exception{

        UserHolder.removeUser();
    }

}
