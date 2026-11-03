package com.taskflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据看板 VO。
 */
@Data
public class DashboardVO {

    /** 核心指标 */
    private Summary summary;

    /** 各状态工单数量 [{name:'待受理', value:3}] */
    private List<Map<String, Object>> statusCount;

    /** 各类型工单数量 */
    private List<Map<String, Object>> typeCount;

    /** 近 30 天趋势 */
    private List<Map<String, Object>> dailyTrend;

    /** 处理人工作量 TOP8 */
    private List<Map<String, Object>> assigneeRank;

    /** 审批单状态分布 */
    private List<Map<String, Object>> approvalCount;

    /**
     * 核心指标汇总。
     */
    @Data
    public static class Summary {
        /** 工单总数 */
        private Long total = 0L;
        /** 进行中 */
        private Long processing = 0L;
        /** 已完成 */
        private Long finished = 0L;
        /** 已关闭 + 已驳回 */
        private Long closed = 0L;
        /** 超时数 */
        private Long timeout = 0L;
        /** 紧急工单数 */
        private Long urgent = 0L;
        /** 完成率（%） */
        private Double finishRate = 0.0;
        /** 用户总数 */
        private Long userCount = 0L;
        /** 我相关的工单数 */
        private Long myTicketCount = 0L;
        /** 待我审批数 */
        private Long myTodoApproval = 0L;
        /** 未读通知数 */
        private Long unreadNotice = 0L;
    }
}