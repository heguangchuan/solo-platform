package com.solo.openapi.support;

import com.solo.openapi.auth.RequireScope;
import com.solo.openapi.constant.OpenApiConstants;
import com.solo.openapi.response.OpenApiScopeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 开放 API scope 目录。启动时扫描 {@code /open-api/**} 上的 {@link RequireScope}，
 * 以注解为唯一事实来源生成管理端可选项，不落库、不提供手工维护入口。
 * 同时做 fail-closed 校验：任一开放接口未标注 scope 时直接启动失败，避免新接口漏标注后裸奔。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiScopeCatalog implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;

    private volatile List<OpenApiScopeResponse> scopes = List.of();

    @Override
    public void afterSingletonsInstantiated() {
        refresh(handlerMappingProvider.getObject());
    }

    public List<OpenApiScopeResponse> getScopes() {
        return scopes;
    }

    void refresh(RequestMappingHandlerMapping handlerMapping) {
        Map<String, ScopeBuilder> builders = new TreeMap<>();
        List<String> missing = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            List<String> paths = resolvePaths(mappingInfo);
            if (paths.isEmpty()) {
                return;
            }
            RequireScope methodScope = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequireScope.class);
            RequireScope annotation = methodScope != null ? methodScope
                    : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireScope.class);
            if (annotation == null || annotation.value().length == 0) {
                missing.add(describe(handlerMethod, paths));
                return;
            }

            String method = resolveMethod(mappingInfo);
            String summary = resolveSummary(handlerMethod);
            String tag = resolveTag(handlerMethod);
            for (String scope : annotation.value()) {
                ScopeBuilder builder = builders.computeIfAbsent(scope, code ->
                        new ScopeBuilder(code, displayName(annotation.name(), code), tag));
                for (String path : paths) {
                    builder.endpoints.add(new OpenApiScopeResponse.Endpoint(method, path, summary));
                }
            }
        });

        if (!missing.isEmpty()) {
            throw new IllegalStateException("开放 API 接口必须标注 @RequireScope，缺失: "
                    + String.join("; ", missing));
        }

        List<OpenApiScopeResponse> loaded = builders.values().stream()
                .map(ScopeBuilder::build)
                .toList();
        this.scopes = loaded;
        log.info("[开放API] scope 目录加载完成: {} 个 scope / {} 个接口",
                loaded.size(), loaded.stream().mapToInt(scope -> scope.endpoints().size()).sum());
    }

    private static List<String> resolvePaths(RequestMappingInfo mappingInfo) {
        return mappingInfo.getPatternValues().stream()
                .filter(path -> path.startsWith(OpenApiConstants.PATH_PREFIX))
                .sorted()
                .toList();
    }

    private static String resolveMethod(RequestMappingInfo mappingInfo) {
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            return "ANY";
        }
        return methods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static String resolveSummary(HandlerMethod handlerMethod) {
        Operation operation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), Operation.class);
        return operation == null ? "" : operation.summary();
    }

    private static String resolveTag(HandlerMethod handlerMethod) {
        Tag tag = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), Tag.class);
        return tag == null ? handlerMethod.getBeanType().getSimpleName() : tag.name();
    }

    private static String displayName(String name, String code) {
        return name == null || name.isBlank() ? code : name;
    }

    private static String describe(HandlerMethod handlerMethod, List<String> paths) {
        return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName()
                + " " + String.join(",", paths);
    }

    private static final class ScopeBuilder {

        private final String code;
        private final String name;
        private final String tag;
        private final List<OpenApiScopeResponse.Endpoint> endpoints = new ArrayList<>();

        private ScopeBuilder(String code, String name, String tag) {
            this.code = code;
            this.name = name;
            this.tag = tag;
        }

        private OpenApiScopeResponse build() {
            List<OpenApiScopeResponse.Endpoint> sorted = endpoints.stream()
                    .distinct()
                    .sorted(Comparator.comparing(OpenApiScopeResponse.Endpoint::path)
                            .thenComparing(OpenApiScopeResponse.Endpoint::method))
                    .toList();
            return new OpenApiScopeResponse(code, name, tag, sorted);
        }
    }
}
