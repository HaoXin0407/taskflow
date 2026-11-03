package com.taskflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 工单分页查询参数。
 */
@Data
public class TicketQueryDTO {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 按状态筛选 */
    private Integer status;

    /** 按类型筛选 */
    private String type;

    /** 按优先级筛选 */
    private Integer priority;

    /** 关键字（标题 / 内容 / 编号） */
    private String keyword;

    /** 指定创建人 */
    private Long creatorId;

    /** 指定处理人 */
    private Long assigneeId;

    /** 是否只查与我相关的（我创建的 + 指派给我的） */
    private Boolean mine;
}