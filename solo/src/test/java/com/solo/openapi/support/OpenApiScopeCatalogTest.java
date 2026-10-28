package com.solo.openapi.support;

import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.response.OpenApiScopeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiScopeCatalogTest {

    @Test
    void collectsScopesAndEndpointsFromAnnotations() throws Exception {
        RequestMappingHandlerMapping mapping = newHandlerMapping();
        register(mapping, ScopedController.class, "start", "/open-api/v1/play/start");
        register(mapping, ScopedController.class, "stop", "/open-api/v1/play/stop");

        OpenApiScopeCatalog catalog = catalogFor(mapping);
        catalog.refresh(mapping);

        List<OpenApiScopeResponse> scopes = catalog.getScopes();
        assertThat(scopes).hasSize(1);
        OpenApiScopeResponse scope = scopes.getFirst();
        assertThat(scope.code()).isEqualTo("gb:play:write");
        assertThat(scope.name()).isEqualTo("实时点播控制");
        assertThat(scope.tag()).isEqualTo("测试点播");
        assertThat(scope.endpoints()).extracting(OpenApiScopeResponse.Endpoint::path)
                .containsExactly("/open-api/v1/play/start", "/open-api/v1/play/stop");
        assertThat(scope.endpoints()).extracting(OpenApiScopeResponse.Endpoint::method)
                .containsOnly("POST");
        assertThat(scope.endpoints()).extracting(OpenApiScopeResponse.Endpoint::summary)
                .containsExactly("开始点播", "停止点播");
    }

    @Test
    void ignoresNonOpenApiHandlers() throws Exception {
        RequestMappingHandlerMapping mapping = newHandlerMapping();
        register(mapping, AdminController.class, "admin", "/api/admin/test");

        OpenApiScopeCatalog catalog = catalogFor(mapping);
        catalog.refresh(mapping);

        assertThat(catalog.getScopes()).isEmpty();
    }

    @Test
    void failsFastWhenOpenApiHandlerHasNoScope() throws Exception {
        RequestMappingHandlerMapping mapping = newHandlerMapping();
        register(mapping, UnscopedController.class, "unscoped", "/open-api/v1/test");

        OpenApiScopeCatalog catalog = catalogFor(mapping);

        assertThatThrownBy(() -> catalog.refresh(mapping))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UnscopedController#unscoped")
                .hasMessageContaining("缺失");
    }

    private static RequestMappingHandlerMapping newHandlerMapping() {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(new StaticApplicationContext());
        mapping.afterPropertiesSet();
        return mapping;
    }

    private static void register(RequestMappingHandlerMapping mapping, Class<?> controller,
                                 String methodName, String path) throws ReflectiveOperationException {
        Method method = controller.getDeclaredMethod(methodName);
        RequestMappingInfo info = RequestMappingInfo.paths(path)
                .methods(RequestMethod.POST)
                .build();
        mapping.registerMapping(info, controller.getDeclaredConstructor().newInstance(), method);
    }

    private static OpenApiScopeCatalog catalogFor(RequestMappingHandlerMapping mapping) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("requestMappingHandlerMapping", mapping);
        return new OpenApiScopeCatalog(
                beanFactory.getBeanProvider(RequestMappingHandlerMapping.class, false));
    }

    @Tag(name = "测试点播")
    @RestController
    static class ScopedController {

        @Operation(summary = "开始点播")
        @RequireScope(value = "gb:play:write", name = "实时点播控制")
        @PostMapping("/open-api/v1/play/start")
        public void start() {
        }

        @Operation(summary = "停止点播")
        @RequireScope(value = "gb:play:write", name = "实时点播控制")
        @PostMapping("/open-api/v1/play/stop")
        public void stop() {
        }
    }

    @RestController
    static class AdminController {

        @PostMapping("/api/admin/test")
        public void admin() {
        }
    }

    @RestController
    static class UnscopedController {

        @PostMapping("/open-api/v1/test")
        public void unscoped() {
        }
    }
}
