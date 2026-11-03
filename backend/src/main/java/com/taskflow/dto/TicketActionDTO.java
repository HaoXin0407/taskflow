package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工单流转（状态机动作）参数。
 */
@Data
public class TicketActionDTO {

    @NotBlank(message = "请指定要执行的动作")
    private String action;

    /** 备注 / 处理说明 */
    private String remark;

    /** 转派场景下指定新的处理人ID */
    private Long assigneeId;

    /** 幂等键：同一 key 重复提交只生效一次，防重复点击 */
    private String requestId;
}