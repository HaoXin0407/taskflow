package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单流转记录实体，对应表 wf_ticket_flow。
 * <p>这张表记录状态机每一次变更，是「全链路可追溯」的实现基础。</p>
 */
@Data
@TableName("wf_ticket_flow")
public class WfTicketFlow implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    /** 变更前状态，创建时为 null */
    private Integer fromStatus;

    private Integer toStatus;

    /** 动作码 CREATE/ACCEPT/SUBMIT/... */
    private String action;

    private String remark;

    private Long operatorId;

    /** 操作人昵称（冗余字段，避免展示时连表） */
    private String operatorName;

    /** 非数据库字段：动作中文名 */
    @TableField(exist = false)
    private String actionDesc;

    /** 非数据库字段：目标状态中文名 */
    @TableField(exist = false)
    private String statusDesc;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}