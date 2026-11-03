package com.taskflow.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 工单状态枚举 —— 项目的核心，状态机规则集中定义在这里。
 *
 * <p>设计要点（面试可讲）：把「当前状态 → 允许哪些动作」的映射集中声明，
 * 而不是散落在 Service 的 if-else 中。好处是新增状态只需改一处，非法流转天然被拦截。</p>
 *
 * <p><b>注意</b>：这里刻意使用动作码字符串字面量，而不是直接引用 {@link TicketAction} 枚举常量。
 * 因为 TicketAction 反过来又依赖 TicketStatus 定义目标状态，若互相引用常量，
 * JVM 在类初始化时会出现循环依赖，可能拿到 null，导致状态机判断失效。</p>
 */
@Getter
public enum TicketStatus {

    /** 待受理：创建后的初始状态，等待管理员或主管受理 */
    PENDING(0, "待受理", false, "ACCEPT", "CLOSE"),

    /** 处理中：已受理，处理人正在解决 */
    PROCESSING(1, "处理中", false, "TRANSFER", "SUBMIT", "REJECT"),

    /** 待确认：处理人已提交结果，等待提单人验收 */
    CONFIRMING(2, "待确认", false, "CONFIRM", "REJECT", "TRANSFER"),

    /** 已完成：提单人已确认，待归档 */
    FINISHED(3, "已完成", false, "CLOSE", "REOPEN"),

    /** 已关闭：终态，归档 */
    CLOSED(4, "已关闭", true),

    /** 已驳回：终态，可重开 */
    REJECTED(5, "已驳回", false, "REOPEN");

    /** 状态码，存数据库 */
    private final Integer code;

    /** 状态中文名 */
    private final String desc;

    /** 是否终态：终态不允许任何流转（REJECTED 允许重开，故非终态） */
    private final boolean finalState;

    /** 该状态下允许执行的动作码集合 */
    private final Set<String> allowedActions;

    TicketStatus(Integer code, String desc, boolean finalState, String... actions) {
        this.code = code;
        this.desc = desc;
        this.finalState = finalState;
        this.allowedActions = actions.length == 0
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(Arrays.asList(actions)));
    }

    /** 根据状态码获取枚举，找不到返回 null */
    public static TicketStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 根据状态码获取中文名 */
    public static String descOf(Integer code) {
        TicketStatus status = of(code);
        return status == null ? "未知" : status.desc;
    }

    /** 判断当前状态是否允许执行某动作码 */
    public boolean canDo(String actionCode) {
        return !finalState && allowedActions.contains(actionCode);
    }

    /** 判断是否为终态 */
    public boolean isFinalState() {
        return finalState;
    }
}