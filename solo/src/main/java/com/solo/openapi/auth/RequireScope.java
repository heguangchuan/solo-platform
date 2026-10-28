package com.solo.openapi.auth;

import java.lang.annotation.*;

/**
 * 开放 API 接口所需 Scope。标注在 Controller 方法/类上，由 {@link OpenApiScopeInterceptor} 校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireScope {

    /** 需要的 scope 编码（满足其一即可） */
    String[] value();

    /** 管理端展示名称，留空时回退为 scope 编码 */
    String name() default "";
}
