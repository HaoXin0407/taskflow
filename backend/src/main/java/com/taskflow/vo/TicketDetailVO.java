package com.taskflow.vo;

import com.taskflow.entity.WfTicket;
import com.taskflow.entity.WfTicketComment;
import com.taskflow.entity.WfTicketFlow;
import lombok.Data;

/**
 * 工单详情 VO：主表 + 流转轨迹 + 评论列表。
 */
@Data
public class TicketDetailVO {

    /** 工单主体 */
    private WfTicket ticket;

    /** 流转轨迹（时间线展示） */
    private java.util.List<WfTicketFlow> flows;

    /** 评论列表 */
    private java.util.List<WfTicketComment> comments;
}