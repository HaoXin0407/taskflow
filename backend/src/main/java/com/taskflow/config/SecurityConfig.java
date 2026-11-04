package com.taskflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.common.Result;
import com.taskflow.common.ResultCode;
import com.taskflow.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置类。
 *
 * <p>本项目的安全策略（面试时可直接说）：</p>
 * <ul>
 *     <li>无状态：SessionCreationPolicy.STATELESS，不创建 HttpSession</li>
 *     <li>白名单：登录、注册、错误页、接口文档放行</li>
 *     <li>其余接口一律需要 JWT 认证</li>
 *     <li>方法级鉴权：开启 @EnableMethodSecurity，支持 @PreAuthorize("hasAuthority('xxx')")</li>
 *     <li>过滤器在 UsernamePasswordAuthenticationFilter 之前插入</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableMethodSecurity          // 开启方法级权限控制
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 免认证白名单 */
    private static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            "/error",
            // Knife4j / OpenAPI 文档
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/favicon.ico"
            // ⚠️ 调试接口 /debug/** 故意不放进免认证白名单。
            //    它会输出所有用户的角色与权限清单，匿名可访问等于信息泄露。
            //    现在要求登录后才能调用 —— 本地排查权限问题依然够用
            //    （先登录拿到 token，再带 token 调用）。
            //    生产环境如需彻底关闭，可给 DebugController 加 @Profile("dev")。
    };

    /**
     * 注意：PasswordEncoder 已移至 {@link PasswordConfig}。
     * <p>原因见该类的注释：如果定义在这里，AuthService → passwordEncoder → SecurityConfig
     * → JwtAuthenticationFilter → AuthService 会形成循环依赖，Spring Boot 2.6+ 默认禁止。</p>
     */

    /**
     * 认证管理器：供 AuthenticationManager 手动执行登录时使用。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离，无 Cookie 会话，关闭 CSRF（否则 POST 全部被拦）
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 放行所有 OPTIONS 预检请求
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 其余接口需要认证（登录后由 JwtAuthenticationFilter 注入身份）
                        .anyRequest().authenticated())
                // 把 JWT 过滤器插到用户名密码过滤器之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 认证失败 / 权限不足 时统一返回 JSON，而不是重定向到登录页
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, ResultCode.UNAUTHORIZED.getCode(), "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeJson(response, ResultCode.FORBIDDEN.getCode(), "没有访问权限")));

        return http.build();
    }

    /**
     * 跨域配置：允许前端 Vite 开发服务器访问。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 向响应流写出 JSON 错误结果。
     */
    private void writeJson(jakarta.servlet.http.HttpServletResponse response,
                           Integer code,
                           String msg) throws java.io.IOException {
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.error(code, msg);
        response.getWriter().write(objectMapper.writeValueAsString(result));
        response.getWriter().flush();
    }
}