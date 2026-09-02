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

/**
 * OpenAPI 3.0 + knife4j 配置
 *
 * <p>对应注解：{@link io.swagger.v3.oas.annotations.tags.Tag}（Controller 类）/ {@link io.swagger.v3.oas.annotations.Operation}（方法）。
 * knife4j 自动读 Spring 注解（{@code @RestController} / {@code @GetMapping} 等）生成 OpenAPI 3.0 规范，
 * 额外补 {@code @Tag} / {@code @Operation} / {@code @Parameter} / {@code @Schema} 提供中文描述给前端看。</p>
 *
 * <h3>knife4j 分组</h3>
 * <p>按后端包路径 {@code com.solo.X.controller} 一对一映射成 knife4j 顶部 7 个分组，
 * UI 左侧导航树跟项目域一致。{@code @Tag(name = "X管理")} 在每个组内作为子节点。</p>
 */
@Configuration
public class Knife4jConfig {

    /** 全局 OpenAPI 元信息 + JWT Bearer 鉴权 */
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

    /** AI 能力：素材 / 对话 / 创作 / 模型配置 / 提示词模板 / 任务 */
    @Bean
    public GroupedOpenApi aiGroup() {
        return GroupedOpenApi.builder()
                .group("AI能力")
                .packagesToScan("com.solo.ai.controller")
                .build();
    }

    /** 大屏：首页 / 地图配置 */
    @Bean
    public GroupedOpenApi dashboardGroup() {
        return GroupedOpenApi.builder()
                .group("大屏")
                .packagesToScan("com.solo.dashboard.controller")
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
}
