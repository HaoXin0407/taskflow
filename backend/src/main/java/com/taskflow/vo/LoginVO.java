package com.taskflow.vo;

import com.taskflow.entity.SysPermission;
import lombok.Data;

import java.util.Set;

/**
 * 登录成功返回给前端的完整信息。
 */
@Data
public class LoginVO {

    /** JWT 令牌 */
    private String token;

    /** 令牌前缀，拼接后即 Authorization 的值 */
    private String tokenPrefix;

    /** 用户ID */
    private Long userId;

    private String username;

    private String nickname;

    private String dept;

    private String avatar;

    /** 角色标识集合 */
    private Set<String> roles;

    /** 权限标识集合，前端用于按钮级权限控制 v-has-perm */
    private Set<String> permissions;

    /** 可见菜单（目录 + 菜单），前端据此动态注册路由 */
    private java.util.List<SysPermission> menus;

    /** 未读通知数 */
    private Integer unreadCount;
}