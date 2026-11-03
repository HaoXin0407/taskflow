package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交审批单参数。
 */
@Data
public class ApprovalSubmitDTO {

    @NotBlank(message = "请输入审批标题")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    /** 业务类型 LEAVE/OVERTIME/PURCHASE/REOPEN */
    @NotBlank(message = "请选择业务类型")
    private String bizType;

    /** 关联业务ID，如工单ID */
    private Long bizId;

    @NotBlank(message = "请填写申请事由")
    @Size(max = 500, message = "事由不能超过 500 个字符")
    private String reason;

    /** 金额（报销/采购类） */
    private BigDecimal amount;

    /**
     * 审批人ID列表，按顺序生成审批节点。
     * 如 [2, 1] 表示先由2 号（主管）审批，再由 1 号（管理员）审批。
     */
    @NotNull(message = "请至少选择一位审批人")
    private List<Long> approverIds;
}