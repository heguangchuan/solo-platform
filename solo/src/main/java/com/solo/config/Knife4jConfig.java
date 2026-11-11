package com.solo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("登录后从响应里取 accessToken，填在下面输入框：Bearer {token}");

        Components components = new Components()
                .addSecuritySchemes("bearer-jwt", bearerScheme);

        SecurityRequirement globalSecurity = new SecurityRequirement().addList("bearer-jwt");

        return new OpenAPI()
                .info(new Info()
                        .title("Solo Platform API")
                        .description("Solo Platform 后端 REST API 文档。\n" +
                                "访问 `/doc.html` 看 UI；`/v3/api-docs` 看 OpenAPI 3.0 原始 JSON（可导入 Postman / Apifox）。")
                        .version("1.0.0")
                        .contact(new Contact().name("Solo Platform"))
                        .license(new License().name("Proprietary")))
                .components(components)
                .addSecurityItem(globalSecurity);
    }

    /** 系统管理：用户 / 角色 / 权限 / 字典 / 配置 / 文件 / 日志 / 消息 / 在线 / 认证 / 任务 / 服务器 */
    @Bean
    public GroupedOpenApi systemGroup() {
        return GroupedOpenApi.builder()
                .group("系统管理")
                .packagesToScan("com.solo.system.controller")
                .build();
    }

    /** 国标：设备 / 通道 / 上下级平台 / 录像下载 / 点播 / 回放 / 云台 / 分组 */
    @Bean
    public GroupedOpenApi gb28181Group() {
        return GroupedOpenApi.builder()
                .group("国标")
                .packagesToScan("com.solo.gb28181.controller")
                .build();
    }

    /** ONVIF：设备 / 实时点播 */
    @Bean
    public GroupedOpenApi onvifGroup() {
        return GroupedOpenApi.builder()
                .group("ONVIF")
                .packagesToScan("com.solo.onvif.controller")
                .build();
    }

    /** 媒体节点：节点管理 / 拉流代理 / 推流 / 推流方鉴权 */
    @Bean
    public GroupedOpenApi mediaGroup() {
        return GroupedOpenApi.builder()
                .group("媒体节点")
                .packagesToScan("com.solo.media.controller")
                .build();
    }

    /** 调试（开发期诊断）：限流 / 防重提交 / XSS 过滤 */
    @Bean
    public GroupedOpenApi testsGroup() {
        return GroupedOpenApi.builder()
                .group("调试")
                .packagesToScan("com.solo.tests.controller")
                .build();
    }

    /** 开放平台：第三方调用的 /open-api/** 和后台应用管理 /api/openapi/** */
    @Bean
    public GroupedOpenApi openApiGroup() {
        return GroupedOpenApi.builder()
                .group("开放平台")
                .packagesToScan("com.solo.openapi.controller", "com.solo.openapi.admin")
                .build();
    }
}
