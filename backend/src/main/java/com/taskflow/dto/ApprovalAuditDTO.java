package com.taskflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批处理参数。
 */
@Data
public class ApprovalAuditDTO {

    @NotNull(message = "请指定审批单ID")
    private Long approvalId;

    /** 是否通过：true 通过，false 驳回 */
    @NotNull(message = "请选择审批结果")
    private Boolean pass;

    /** 审批意见 */
    private String comment;
}