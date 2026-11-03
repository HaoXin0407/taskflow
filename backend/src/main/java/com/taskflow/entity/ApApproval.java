package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批单实体，对应表 ap_approval。
 */
@Data
@TableName("ap_approval")
public class ApApproval implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String approvalNo;

    private String title;

    /** 业务类型 LEAVE/OVERTIME/PURCHASE/REOPEN */
    private String bizType;

    /** 关联业务ID，如工单ID */
    private Long bizId;

    private String reason;

    /** 金额（报销/采购类） */
    private BigDecimal amount;

    private Long applicantId;

    /** 当前节点序号 */
    private Integer currentNode;

    /** 总节点数 */
    private Integer totalNode;

    /** 0 审批中 1 通过 2 驳回 3 撤销 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // --------- 非数据库字段 ---------

    @TableField(exist = false)
    private String applicantName;

    @TableField(exist = false)
    private String statusDesc;

    @TableField(exist = false)
    private String bizTypeDesc;

    /** 当前待审批人ID，方便前端判断"轮到我审批了" */
    @TableField(exist = false)
    private Long currentApproverId;

    /** 我是否有权审批这条单据 */
    @TableField(exist = false)
    private Boolean canAudit;
}