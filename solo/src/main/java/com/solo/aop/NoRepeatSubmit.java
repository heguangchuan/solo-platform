package com.solo.aop;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoRepeatSubmit {


    long expire() default 3;

    TimeUnit timeUnit() default TimeUnit.SECONDS;


    LockType lockType() default LockType.USER;

    String message() default "请求过于频繁，请稍后再试";

    boolean unlockWhenFinish() default true;


    boolean includeParams() default false;


    enum LockType {

        USER,

        IP,

        GLOBAL
    }
}
