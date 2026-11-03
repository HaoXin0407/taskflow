package com.taskflow.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务状态码枚举。
 * <p>分段规范：</p>
 * <ul>
 *     <li>200          成功</li>
 *     <li>400 / 401 / 403 / 404 / 500  通用 HTTP 语义</li>
 *     <li>1000 ~ 1999  认证与权限</li>
 *     <li>2000 ~ 2999  用户模块</li>
 *     <li>3000 ~ 3999  工单模块</li>
 *     <li>4000 ~ 4999  审批模块</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "系统内部错误"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求的资源不存在"),

    LOGIN_FAILED(1001, "账号或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已被禁用，请联系管理员"),
    TOKEN_INVALID(1003, "令牌无效，请重新登录"),
    TOKEN_EXPIRED(1004, "令牌已过期，请重新登录"),
    USERNAME_EXISTS(1005, "用户名已存在"),
    OLD_PASSWORD_ERROR(1006, "原密码错误"),

    USER_NOT_FOUND(2001, "用户不存在"),

    TICKET_NOT_FOUND(3001, "工单不存在"),
    TICKET_STATUS_ERROR(3002, "当前状态不允许该操作"),
    TICKET_ASSIGN_ERROR(3003, "只能受理指派给自己的工单"),

    APPROVAL_NOT_FOUND(4001, "审批单不存在"),
    APPROVAL_STATUS_ERROR(4002, "审批单已结束，无法重复处理"),
    APPROVAL_NO_PERMISSION(4003, "您不是当前节点的审批人"),
    APPROVAL_EMPTY_NODE(4004, "该审批单未配置审批节点");

    private final Integer code;
    private final String msg;
}