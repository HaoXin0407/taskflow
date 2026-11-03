package com.taskflow.enums;

import lombok.Getter;

/**
 * 工单流转动作枚举。
 *
 * <p>每个动作绑定一个「流转后的目标状态」，Service 层只需 action 即可推导出新状态，
 * 彻底避免调用方手动指定 targetStatus 造成的数据不一致。</p>
 */
@Getter
public enum TicketAction {

    /** 受理：待受理 → 处理中 */
    ACCEPT("ACCEPT", TicketStatus.PROCESSING, "受理"),

    /** 转派：状态不变（处理中 → 处理中），仅更换处理人 */
    TRANSFER("TRANSFER", TicketStatus.PROCESSING, "转派"),

    /** 提交处理结果：处理中 → 待确认 */
    SUBMIT("SUBMIT", TicketStatus.CONFIRMING, "提交"),

    /** 确认验收：待确认 → 已完成 */
    CONFIRM("CONFIRM", TicketStatus.FINISHED, "确认验收"),

    /** 驳回：待确认/处理中 → 处理中（打回重做） */
    REJECT("REJECT", TicketStatus.PROCESSING, "驳回"),

    /** 关闭：已完成 → 已关闭（归档） */
    CLOSE("CLOSE", TicketStatus.CLOSED, "关闭归档"),

    /** 重开：已关闭/已驳回 → 处理中 */
    REOPEN("REOPEN", TicketStatus.PROCESSING, "重新打开");

    /** 动作码 */
    private final String code;

    /** 执行该动作后到达的状态 */
    private final TicketStatus targetStatus;

    /** 动作中文名 */
    private final String desc;

    TicketAction(String code, TicketStatus targetStatus, String desc) {
        this.code = code;
        this.targetStatus = targetStatus;
        this.desc = desc;
    }

    /** 根据动作码获取枚举 */
    public static TicketAction of(String code) {
        for (TicketAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知的工单动作：" + code);
    }

    /** 判断该动作是否会改变工单状态（转派不会） */
    public boolean changesStatus() {
        return this != TRANSFER;
    }
}