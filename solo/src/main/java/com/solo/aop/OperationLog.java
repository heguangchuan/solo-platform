package com.solo.aop;

import com.solo.common.BusinessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 标注在 controller 写操作方法上，由 OperationLogAspect 异步记录到 sys_log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 模块名，如 "参数管理"
     */
    String module();

    /**
     * 业务类型
     */
    BusinessType type();

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录入参
     */
    boolean recordParam() default true;
}
