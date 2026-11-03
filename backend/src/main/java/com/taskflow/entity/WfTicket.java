package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单实体，对应表 wf_ticket。
 */
@Data
@TableName("wf_ticket")
public class WfTicket implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号 */
    private String ticketNo;

    private String title;

    private String content;

    /** 类型 BUG/FEATURE/COMPLAINT/OTHER */
    private String type;

    /** 优先级 1紧急 2高 3中 4低 */
    private Integer priority;

    /** 状态，见 TicketStatus 枚举 */
    private Integer status;

    /** 创建人 */
    private Long creatorId;

    /** 当前处理人 */
    private Long assigneeId;

    private String dept;

    /** 是否超时 1 是 0 否 */
    private Integer isTimeout;

    /** SLA 截止时间 */
    private LocalDateTime deadline;

    private LocalDateTime finishAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 非数据库字段：创建人昵称（联表查出的冗余数据） */
    @TableField(exist = false)
    private String creatorName;

    /** 非数据库字段：处理人昵称 */
    @TableField(exist = false)
    private String assigneeName;

    /** 非数据库字段：状态中文名 */
    @TableField(exist = false)
    private String statusDesc;

    /** 非数据库字段：类型中文名 */
    @TableField(exist = false)
    private String typeDesc;

    /** 非数据库字段：优先级中文名 */
    @TableField(exist = false)
    private String priorityDesc;

    /** 非数据库字段：评论数 */
    @TableField(exist = false)
    private Integer commentCount;

    /** 非数据库字段：审批单金额（审批详情用） */
    @TableField(exist = false)
    private BigDecimal amount;
}