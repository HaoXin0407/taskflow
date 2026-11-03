package com.taskflow.enums;

import lombok.Getter;

/**
 * 工单类型枚举。
 */
@Getter
public enum TicketType {

    BUG("BUG", "系统缺陷"),
    FEATURE("FEATURE", "功能需求"),
    COMPLAINT("COMPLAINT", "投诉建议"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    TicketType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String descOf(String code) {
        for (TicketType t : values()) {
            if (t.code.equals(code)) {
                return t.desc;
            }
        }
        return "未知";
    }
}