package com.taskflow.common;

import lombok.Getter;

/**
 * 自定义业务异常。
 * <p>Service 层校验不通过时直接抛出，由 GlobalExceptionHandler 统一捕获并转成 Result。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String msg) {
        super(msg);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    /** 条件成立时抛异常，替代大量 if-throw */
    public static void throwIf(boolean condition, String msg) {
        if (condition) {
            throw new BusinessException(msg);
        }
    }

    public static void throwIf(boolean condition, ResultCode resultCode) {
        if (condition) {
            throw new BusinessException(resultCode);
        }
    }
}