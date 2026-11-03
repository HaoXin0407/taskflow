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
 * 审批节点实体，对应表 ap_approval_node。
 */
@Data
@TableName("ap_approval_node")
public class ApApprovalNode implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long approvalId;

    /** 节点序号，从 1 开始 */
    private Integer nodeIndex;

    private String nodeName;

    private Long approverId;

    /** 0 待审批 1 通过 2 驳回 */
    private Integer status;

    private String comment;

    private LocalDateTime approveTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String approverName;
}