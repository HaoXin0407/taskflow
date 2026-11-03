package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知实体，对应表 sys_notice。
 */
@Data
@TableName("sys_notice")
public class SysNotice implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** SYSTEM / TICKET / APPROVAL */
    private String type;

    /** 1 已读 0 未读 */
    private Integer isRead;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}