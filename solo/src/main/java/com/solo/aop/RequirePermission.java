package com.solo.aop;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String[] value();

    // ALL 全都要有，ANY 有一个就行。
    RequireMode mode() default RequireMode.ANY;

    enum RequireMode {
        ALL,
        ANY
    }
}
