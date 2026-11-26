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

    @Bean
    public GroupedOpenApi systemGroup() {
        return GroupedOpenApi.builder()
                .group("系统管理")
                .packagesToScan("com.solo.system.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi gb28181Group() {
        return GroupedOpenApi.builder()
                .group("国标")
                .packagesToScan("com.solo.gb28181.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi onvifGroup() {
        return GroupedOpenApi.builder()
                .group("ONVIF")
                .packagesToScan("com.solo.onvif.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi mediaGroup() {
        return GroupedOpenApi.builder()
                .group("媒体节点")
                .packagesToScan("com.solo.media.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi testsGroup() {
        return GroupedOpenApi.builder()
                .group("调试")
                .packagesToScan("com.solo.tests.controller")
                .build();
    }

    @Bean
    public GroupedOpenApi openApiGroup() {
        return GroupedOpenApi.builder()
                .group("开放平台")
                .packagesToScan("com.solo.openapi.controller", "com.solo.openapi.admin")
                .build();
    }
}
