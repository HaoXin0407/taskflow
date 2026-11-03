package com.taskflow.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 登录用户上下文对象。
 * <p>同时实现 Spring Security 的 {@link UserDetails}，
 * 这样自定义的 {@link JwtAuthenticationFilter} 构造 Authentication 时可以直接传入本对象。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails {

    /** 用户ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 昵称，前端展示用 */
    private String nickname;

    /** 部门 */
    private String dept;

    /** 权限标识集合，如 system:user:list */
    private Set<String> permissions;

    /** 角色标识集合，如 ADMIN */
    private Set<String> roles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        if (permissions != null) {
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }
        if (roles != null) {
            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        // 密码校验已在登录时完成，这里返回 null 表示不需要再提供密码
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}