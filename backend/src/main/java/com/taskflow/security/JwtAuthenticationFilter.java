package com.taskflow.security;

import com.taskflow.service.AuthService;
import com.taskflow.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 整个安全体系的核心。
 *
 * <p>执行时机：每次请求进入后端前都会被执行一次（OncePerRequestFilter 保证只执行一次）。</p>
 * <p>流程：</p>
 * <ol>
 *     <li>从请求头 {@code Authorization} 中取出 Bearer 令牌</li>
 *     <li>用 JwtUtils 校验签名与有效期</li>
 *     <li>校验通过 → 解析出 userId，重新加载最新的用户权限信息</li>
 *     <li>构造 {@link UsernamePasswordAuthenticationToken} 放入 SecurityContextHolder</li>
 *     <li>后续 {@code @PreAuthorize} 就能拿到权限做鉴权</li>
 * </ol>
 *
 * <p>注意第3 步：每次请求都查库拿权限，是为了保证「管理员在后台改了角色，
 * 被改用户下一次请求立即生效」，这是很多初级项目会漏掉的细节。</p>
 *
 * <p><b>关于循环依赖</b>：本类被 {@code SecurityConfig} 注入，同时又依赖 {@link AuthService}，
 * 构成 SecurityConfig → Filter → AuthService → PasswordEncoder → SecurityConfig 的环。
 * 解决办法是把 PasswordEncoder 拆到独立的 {@code PasswordConfig}（已在那边处理）。
 * 这里的 {@code @Lazy} 是额外保险 —— 让 Spring 注入代理对象而非真实实例，
 * 即使将来依赖链再变化也不会死锁。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    private final @Lazy AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 已有认证信息则不再重复认证
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtUtils.getUserId(token);
            if (userId != null) {
                LoginUser loginUser = authService.loadLoginUserById(userId);
                if (loginUser != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("用户认证成功: {}", loginUser.getUsername());
                } else {
                    log.debug("令牌有效但用户不存在或已禁用: userId={}", userId);
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用场景下必须清理，防止下一个请求拿到上一个用户的上下文
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 从请求头或参数中提取令牌。
     */
    private String resolveToken(HttpServletRequest request) {
        String headerValue = request.getHeader(jwtUtils.getHeader());
        if (StringUtils.hasText(headerValue) && headerValue.startsWith(jwtUtils.getPrefix())) {
            return headerValue;
        }
        // 兼容 WebSocket / 文件下载等无法自定义 Header 的场景
        String param = request.getParameter("token");
        if (StringUtils.hasText(param)) {
            return param;
        }
        return null;
    }
}