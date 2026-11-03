package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 修改工单参数。
 */
@Data
public class TicketDTO {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "请输入工单标题")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @NotBlank(message = "请输入工单内容")
    @Size(max = 5000, message = "内容过长")
    private String content;

    /** 类型 BUG/FEATURE/COMPLAINT/OTHER */
    private String type;

    /** 优先级 1紧急 2高 3中 4低 */
    private Integer priority;

    /**
     * 指定处理人（可选）。
     *
     * <p><b>只有管理岗（管理员 / 主管）才能指定</b>：
     * 普通员工创建工单时只能"提交后由他人受理"，
     * 不能自己指定谁来处理 —— 否则可以绕过工单流转流程直接甩给别人。</p>
     *
     * <p>后端会在 {@code TicketService.create} 中做权限校验，
     * 非管理岗传了这个字段也会被忽略并记日志。</p>
     */
    private Long assigneeId;
}