package com.taskflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p><b>为什么要单独拆一个类？</b>（这是本项目踩过的真实坑）</p>
 *
 * <p>原本 {@code PasswordEncoder} 定义在 {@link SecurityConfig} 里，运行时出现循环依赖：</p>
 * <pre>
 *   jwtAuthenticationFilter  → 依赖 →  authService
 *   authService             → 依赖 →  passwordEncoder（在 SecurityConfig 里）
 *   securityConfig          → 依赖 →  jwtAuthenticationFilter
 *                ↑__________________________________|
 * </pre>
 *
 * <p>Spring Boot 2.6+ 默认禁止循环依赖，直接启动失败。</p>
 *
 * <p>解法：把与「认证算法」相关的 Bean 抽到独立配置类，打断
 * SecurityConfig ←→ 业务 Bean 的反向依赖。</p>
 *
 * <p><b>面试可讲</b>：这是一种「配置类职责拆分」的实践 ——
 * 配置类的职责越单一，被其他 Bean 依赖的副作用就越少。</p>
 */
@Configuration
public class PasswordConfig {

    /**
     * 密码编码器：BCrypt 自带随机盐，同一密码每次加密结果都不同，比 MD5 安全。
     *
     * <p>BCrypt 特点：</p>
     * <ul>
     *     <li>随机盐内置，同密码密文不同，防彩虹表</li>
     *     <li>计算成本可调（默认 10轮），刻意做慢以抵抗暴力破解</li>
     *     <li>密文自带算法+成本+盐信息，升级算法时也能兼容旧密码</li>
     * </ul>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}