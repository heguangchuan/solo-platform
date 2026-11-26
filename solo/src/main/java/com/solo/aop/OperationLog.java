package com.solo.aop;

import com.solo.common.enums.BusinessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    String module();

    BusinessType type();

    String description() default "";

    boolean recordParam() default true;
}
