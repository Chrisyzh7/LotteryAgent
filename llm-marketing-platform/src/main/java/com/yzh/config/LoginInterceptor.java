package com.yzh.config;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.yzh.service.impl.UserServiceImpl;
import com.yzh.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器
 * 拦截请求，解析 Token，如果通过则放入 UserContext，失败则返回 401
 */
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 跨域预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从 Header 提取
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return buildUnAuthResponse(response, "未登录或请求头中未携带有效 Token");
        }

        String token = authorization.substring(7);

        try {
            // 校验签名是否合法
            boolean verify = JWTUtil.verify(token, JWTSignerUtil.hs256(UserServiceImpl.JWT_SECRET.getBytes()));
            if (!verify) {
                return buildUnAuthResponse(response, "Token签名无效，请重新登录");
            }

            // 解析 Payload 取 userId
            JWT jwt = JWTUtil.parseToken(token);
            String userId = (String) jwt.getPayload("userId");
            String username = (String) jwt.getPayload("username");
            if (userId == null) {
                return buildUnAuthResponse(response, "Token解析异常：无法获取用户信息");
            }

            // 【关键】放入 ThreadLocal 上下文中，后面的 Controller / Service 直接通过 UserContext.getUserId() 就可以拿到
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
            
            return true;

        } catch (Exception e) {
            log.error("[LoginInterceptor] Token校验失败: {}", e.getMessage());
            return buildUnAuthResponse(response, "Token校验发生异常，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 【关键】防止内存泄漏及线程池复用串数据
        UserContext.remove();
    }

    private boolean buildUnAuthResponse(HttpServletResponse response, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401); // 401 Unauthorized
        String json = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        response.getWriter().write(json);
        return false;
    }
}
