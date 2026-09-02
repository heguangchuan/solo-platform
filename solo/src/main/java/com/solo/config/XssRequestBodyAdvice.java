package com.solo.config;

import com.solo.common.utils.XssUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * XSS 请求体消毒（@RequestBody 入参）
 *
 * <p>对所有 {@link RestController} / {@link Controller} 的 {@code @RequestBody} JSON 入参递归
 * 清洗：{@code String} 字段过 {@link XssUtils#sanitize}；Map / Collection / 数组 / 嵌套对象
 * 递归清洗；{@code com.solo.request} / {@code com.solo.controller} 包外的对象不递归（避免
 * 误改 MyBatis 实体等内部对象）。</p>
 *
 * <p>黑名单字段（{@code password} / {@code rawPassword} / {@code oldPassword} 等）通过
 * {@link XssUtils#shouldSkipField} 跳过，不消毒。</p>
 */
@ControllerAdvice(annotations = {RestController.class, Controller.class})
public class XssRequestBodyAdvice extends RequestBodyAdviceAdapter {

    /**
     * 是否启用本次消毒
     * <p>始终返回 {@code true}——所有 {@code @RequestBody} 入参都走消毒；如需关闭，
     * 在 {@code application.yaml} 切 {@code xss.enabled=false} 即可（见 {@code XssProperties}）。</p>
     *
     * @param methodParameter 当前请求的方法参数
     * @param targetType 目标类型（集合 / 数组 / 单对象）
     * @param converterType 消息转换器类型
     * @return 始终 {@code true}
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 反序列化后清洗入参
     * <p>入口；递归调用 {@link #sanitizeObject} 处理对象图，循环引用由 {@code visited} 集合防爆栈。</p>
     *
     * @param body 已反序列化的入参对象
     * @param inputMessage HTTP 输入消息
     * @param parameter 方法参数
     * @param targetType 目标类型
     * @param converterType 消息转换器类型
     * @return 清洗后的对象（原地修改）
     */
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        sanitizeObject(body, null, new IdentityHashMap<>());
        return body;
    }

    private void sanitizeObject(Object value, String fieldName, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || XssUtils.shouldSkipField(fieldName)) {
            return;
        }
        if (visited.containsKey(value)) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            visited.put(value, Boolean.TRUE);
            sanitizeMap(map, visited);
            return;
        }
        if (value instanceof Collection<?> collection) {
            visited.put(value, Boolean.TRUE);
            sanitizeCollection(collection, fieldName, visited);
            return;
        }
        if (value.getClass().isArray()) {
            visited.put(value, Boolean.TRUE);
            sanitizeArray(value, fieldName, visited);
            return;
        }
        if (!isProjectObject(value)) {
            return;
        }

        visited.put(value, Boolean.TRUE);
        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                sanitizeField(value, field, visited);
            }
            type = type.getSuperclass();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sanitizeMap(Map map, IdentityHashMap<Object, Boolean> visited) {
        for (Object key : map.keySet()) {
            Object mapValue = map.get(key);
            String fieldName = key instanceof String s ? s : null;
            if (mapValue instanceof String stringValue) {
                if (!XssUtils.shouldSkipField(fieldName)) {
                    map.put(key, XssUtils.sanitize(stringValue));
                }
            } else {
                sanitizeObject(mapValue, fieldName, visited);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sanitizeCollection(Collection collection, String fieldName, IdentityHashMap<Object, Boolean> visited) {
        if (collection instanceof java.util.List list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof String stringValue) {
                    list.set(i, XssUtils.sanitize(stringValue));
                } else {
                    sanitizeObject(item, fieldName, visited);
                }
            }
            return;
        }
        for (Object item : collection) {
            sanitizeObject(item, fieldName, visited);
        }
    }

    private void sanitizeArray(Object array, String fieldName, IdentityHashMap<Object, Boolean> visited) {
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            if (item instanceof String stringValue) {
                Array.set(array, i, XssUtils.sanitize(stringValue));
            } else {
                sanitizeObject(item, fieldName, visited);
            }
        }
    }

    private void sanitizeField(Object target, Field field, IdentityHashMap<Object, Boolean> visited) {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return;
        }
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(target);
            if (fieldValue instanceof String stringValue) {
                if (!XssUtils.shouldSkipField(field.getName())) {
                    field.set(target, XssUtils.sanitize(stringValue));
                }
                return;
            }
            sanitizeObject(fieldValue, field.getName(), visited);
        } catch (IllegalAccessException ignored) {
        }
    }

    private boolean isProjectObject(Object value) {
        Package valuePackage = value.getClass().getPackage();
        if (valuePackage == null) {
            return false;
        }
        String packageName = valuePackage.getName();
        return packageName.startsWith("com.solo.request") || packageName.startsWith("com.solo.controller");
    }
}
