package com.taskflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体，对应表 sys_oper_log。
 * <p>该表的每一条记录都由 {@code OperLogAspect} 自动写入，业务代码不需关心。</p>
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块标题 */
    private String title;

    /** 业务类型 INSERT/UPDATE/DELETE/OTHER */
    private String businessType;

    /** 方法名 */
    private String method;

    private String requestMethod;

    private Long operId;

    private String operName;

    private String operUrl;

    private String operIp;

    private String operParam;

    private String jsonResult;

    /** 1 成功 0 失败 */
    private Integer status;

    private String errorMsg;

    /** 耗时 ms */
    private Long costTime;

    private LocalDateTime operTime;
}