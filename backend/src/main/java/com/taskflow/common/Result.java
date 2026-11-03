package com.taskflow.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果包装类。
 * <p>全项目所有 Controller 一律返回 Result，前端只需判断 code === 200。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 业务状态码：200 成功，其余为各种失败原因 */
    private Integer code;

    /** 提示信息，前端可直接 ElMessage 展示 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 服务端时间戳，前端可做时钟同步 */
    private Long timestamp = System.currentTimeMillis();

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ------------------- 成功 -------------------

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "操作成功", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    // ------------------- 失败 -------------------

    public static <T> Result<T> error(String msg) {
        return new Result<>(ResultCode.ERROR.getCode(), msg, null);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }
}