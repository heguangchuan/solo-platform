package com.solo.aop;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /**
     * 需要的权限编码
     */
    String[] value();

    /**
     * 权限验证模式
     * ALL：需要拥有所有指定的权限
     * ANY：需要拥有任意一个指定权限即可
     */
    RequireMode mode() default RequireMode.ANY;

    enum RequireMode {
        ALL,
        ANY
    }
}
