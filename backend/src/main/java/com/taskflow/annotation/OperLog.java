package com.taskflow.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解。
 *
 * <p>用法：贴在 Controller 方法上，{@code OperLogAspect} 会自动拦截并记录一条日志。</p>
 *
 * <pre>
 * &#64;OperLog(title = "工单管理", businessType = BusinessType.INSERT)
 * public Result&lt;Void&gt; create(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块标题，如「工单管理」 */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /**
     * 业务类型枚举。
     */
    enum BusinessType {
        /** 其它 */
        OTHER,
        /** 新增 */
        INSERT,
        /** 修改 */
        UPDATE,
        /** 删除 */
        DELETE,
        /** 查询 */
        QUERY,
        /** 审批 */
        AUDIT
    }
}