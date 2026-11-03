package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单评论实体，对应表 wf_ticket_comment。
 */
@Data
@TableName("wf_ticket_comment")
public class WfTicketComment implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String content;

    private Long userId;

    private String userName;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}