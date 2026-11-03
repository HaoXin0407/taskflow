package com.taskflow.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工单状态机单元测试。
 *
 * <p><b>为什么这个测试很重要：</b></p>
 * <p>状态机是整个系统的核心，6 个状态 × 7 个动作有几十种组合，
 * 人工点页面不可能全部覆盖。有了测试，任何一次修改导致非法流转被放行，
 * 测试会立刻失败。</p>
 *
 * <p>面试时这就是 Q「你怎么保证状态机是对的」的标准答案。</p>
 */
@DisplayName("工单状态机测试")
class TicketStatusMachineTest {

    @Test
    @DisplayName("状态码能正确映射到枚举")
    void statusCodeShouldMapToEnum() {
        assertEquals(TicketStatus.PENDING, TicketStatus.of(0));
        assertEquals(TicketStatus.PROCESSING, TicketStatus.of(1));
        assertEquals(TicketStatus.CONFIRMING, TicketStatus.of(2));
        assertEquals(TicketStatus.FINISHED, TicketStatus.of(3));
        assertEquals(TicketStatus.CLOSED, TicketStatus.of(4));
        assertEquals(TicketStatus.REJECTED, TicketStatus.of(5));
        assertNull(TicketStatus.of(99), "不存在的状态码应返回 null");
        assertNull(TicketStatus.of(null), "null 应返回 null");
    }

    @Test
    @DisplayName("正常流转路径：待受理→处理中→待确认→已完成→已关闭")
    void happyPathShouldWork() {
        // 待受理可以受理
        assertTrue(TicketStatus.PENDING.canDo(TicketAction.ACCEPT.getCode()));
        // 处理中可以提交
        assertTrue(TicketStatus.PROCESSING.canDo(TicketAction.SUBMIT.getCode()));
        // 待确认可以确认
        assertTrue(TicketStatus.CONFIRMING.canDo(TicketAction.CONFIRM.getCode()));
        // 已完成可以关闭
        assertTrue(TicketStatus.FINISHED.canDo(TicketAction.CLOSE.getCode()));
    }

    @Test
    @DisplayName("非法流转必须被拦截：待受理不能直接提交")
    void illegalTransitionShouldBeBlocked() {
        assertFalse(TicketStatus.PENDING.canDo(TicketAction.SUBMIT.getCode()),
                "待受理状态不应该允许直接提交");
        assertFalse(TicketStatus.PENDING.canDo(TicketAction.CONFIRM.getCode()),
                "待受理状态不应该允许确认");
        assertFalse(TicketStatus.PENDING.canDo(TicketAction.REOPEN.getCode()),
                "待受理状态不应该允许重开");
    }

    @Test
    @DisplayName("已关闭是终态，不允许任何动作")
    void closedShouldBeFinalState() {
        assertTrue(TicketStatus.CLOSED.isFinalState());
        for (TicketAction action : TicketAction.values()) {
            assertFalse(TicketStatus.CLOSED.canDo(action.getCode()),
                    "已关闭状态不应允许任何动作，实际放行了：" + action.getDesc());
        }
    }

    @Test
    @DisplayName("已驳回可以重开")
    void rejectedCanReopen() {
        assertFalse(TicketStatus.REJECTED.isFinalState());
        assertTrue(TicketStatus.REJECTED.canDo(TicketAction.REOPEN.getCode()));
    }

    @ParameterizedTest(name = "{0} 状态下执行 {1} 动作，期望结果为 {2}")
    @CsvSource({
            "PENDING,    ACCEPT,   true",
            "PENDING,    CLOSE,    true",
            "PENDING,    SUBMIT,   false",
            "PROCESSING, SUBMIT,   true",
            "PROCESSING, TRANSFER, true",
            "PROCESSING, REJECT,   true",
            "PROCESSING, CONFIRM,  false",
            "PROCESSING, ACCEPT,   false",
            "CONFIRMING, CONFIRM,  true",
            "CONFIRMING, REJECT,   true",
            "FINISHED,   CLOSE,    true",
            "FINISHED,   REOPEN,   true",
            "FINISHED,   SUBMIT,   false",
            "CLOSED,     REOPEN,   false",
            "REJECTED,   REOPEN,   true",
            "REJECTED,   SUBMIT,   false"
    })
    @DisplayName("状态与动作的合法矩阵")
    void statusActionMatrix(String statusName, String actionName, boolean expected) {
        TicketStatus status = TicketStatus.valueOf(statusName);
        TicketAction action = TicketAction.valueOf(actionName);

        assertEquals(expected, status.canDo(action.getCode()),
                "状态「" + status.getDesc() + "」执行「" + action.getDesc() + "」，期望 " + expected);
    }

    @Test
    @DisplayName("每个动作的目标状态必须正确")
    void actionTargetStatusShouldBeCorrect() {
        assertEquals(TicketStatus.PROCESSING, TicketAction.ACCEPT.getTargetStatus());
        assertEquals(TicketStatus.PROCESSING, TicketAction.SUBMIT.getTargetStatus());
        assertEquals(TicketStatus.CONFIRMING, TicketAction.SUBMIT.getTargetStatus());
        assertEquals(TicketStatus.FINISHED, TicketAction.CONFIRM.getTargetStatus());
        assertEquals(TicketStatus.CLOSED, TicketAction.CLOSE.getTargetStatus());
        assertEquals(TicketStatus.PROCESSING, TicketAction.REOPEN.getTargetStatus());
    }

    @Test
    @DisplayName("转派动作不改变状态")
    void transferShouldNotChangeStatus() {
        assertFalse(TicketAction.TRANSFER.changesStatus(),
                "转派只换处理人，不改变工单状态");
        assertTrue(TicketAction.ACCEPT.changesStatus());
    }

    @Test
    @DisplayName("未知动作码应抛出异常")
    void unknownActionShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> TicketAction.of("NOT_EXIST"));
    }

    @Test
    @DisplayName("中文名映射正确")
    void descriptionShouldBeCorrect() {
        assertEquals("待受理", TicketStatus.descOf(0));
        assertEquals("已完成", TicketStatus.descOf(3));
        assertEquals("未知", TicketStatus.descOf(99));
    }
}