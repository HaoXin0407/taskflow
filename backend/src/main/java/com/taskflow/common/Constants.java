package com.taskflow.common;

/**
 * 全局常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 用户详情在 SecurityContext / Session 中的 key */
    public static final String LOGIN_USER = "loginUser";

    /** 超级管理员角色标识，拥有所有权限 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 数据权限：全部 */
    public static final String DATA_SCOPE_ALL = "ALL";

    /** 逻辑未删除 */
    public static final Integer NOT_DELETED = 0;

    /** 逻辑已删除 */
    public static final Integer DELETED = 1;
}