package com.thz.spectrum.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 * - 拦截需要权限的 /admin/** API（除 login）
 * - 从 Header Authorization: Bearer <token> 提取 Token
 * - 验证签名与过期时间；通过则解析用户信息注入 request 上下文并放行
 * - 失败（缺失/无效/过期）返回 401 Unauthorized
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Autowired private JwtUtil jwtUtil;

    /** 请求上下文中用户信息的属性名 */
    public static final String USER_ATTR = "loginUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        // 1. Token 缺失
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(response, "缺少 Token 或 Authorization 头格式错误（应为 Bearer <token>）");
        }

        String token = authHeader.substring(7).trim();

        // 2. 验签 + 过期校验
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException e) {
            return unauthorized(response, "Token 已过期");
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(response, "Token 无效或签名不合法");
        }

        // 3. 解析用户信息注入请求上下文（仅非敏感信息：用户名、角色）
        String username = claims.get("username", String.class);
        if (username == null || username.isBlank()) {
            return unauthorized(response, "Token 中缺少用户信息");
        }
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) { role = "member"; }

        // 4. 角色强制：/admin/** 写操作仅允许 admin（login 已在 WebConfig 排除）
        String path = request.getRequestURI();
        if (path.startsWith("/admin/") && !"admin".equals(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"权限不足：该操作仅限管理员\"}");
            return false;
        }

        request.setAttribute(USER_ATTR, username);
        request.setAttribute("role", role);
        return true;
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
