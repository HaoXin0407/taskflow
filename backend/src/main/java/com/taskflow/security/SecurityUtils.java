package com.taskflow.security;

import com.taskflow.common.BusinessException;
import com.taskflow.common.Constants;
import com.taskflow.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 安全上下文工具类。
 * <p>在 Service / Controller 中静态获取当前登录用户，避免到处传 userId 参数。</p>
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录抛 401。
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    /**
     * 获取当前登录用户，未登录返回 null（不抛异常）。
     */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static Long getUserIdOrNull() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? null : loginUser.getUserId();
    }

    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    public static String getNickname() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? "系统" : loginUser.getNickname();
    }

    /**
     * 获取当前用户权限标识集合。
     */
    public static Set<String> getPermissions() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null || loginUser.getPermissions() == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(loginUser.getPermissions());
    }

    /**
     * 是否为超级管理员。
     */
    public static boolean isAdmin() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.ROLE_ADMIN);
    }

    /**
     * 是否拥有指定权限标识（超管直接放行）。
     */
    public static boolean hasPermission(String permCode) {
        if (isAdmin()) {
            return true;
        }
        return getPermissions().contains(permCode);
    }
}