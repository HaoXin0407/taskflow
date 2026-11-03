package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限（菜单）实体，对应表 sys_permission。
 */
@Data
@TableName("sys_permission")
public class SysPermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级ID，0 为顶级 */
    private Long parentId;

    /** 权限标识，如 system:user:list */
    private String permCode;

    private String permName;

    /** M 目录 / C 菜单 / F 按钮 */
    private String permType;

    /** 前端路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    private String icon;

    private Integer sort;

    /** 1 显示 0 隐藏 */
    private Integer visible;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}