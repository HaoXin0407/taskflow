package com.taskflow.enums;

import lombok.Getter;

/**
 * 审批单状态枚举。
 */
@Getter
public enum ApprovalStatus {

    /** 审批中 */
    PENDING(0, "审批中"),
    /** 已通过 */
    APPROVED(1, "已通过"),
    /** 已驳回 */
    REJECTED(2, "已驳回"),
    /** 已撤销 */
    CANCELED(3, "已撤销");

    private final Integer code;
    private final String desc;

    ApprovalStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ApprovalStatus of(Integer code) {
        if (code == null) {
            return PENDING;
        }
        for (ApprovalStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return PENDING;
    }

    public static String descOf(Integer code) {
        return of(code).getDesc();
    }
}