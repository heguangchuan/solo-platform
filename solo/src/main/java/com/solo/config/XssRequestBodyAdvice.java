package com.solo.config;

import com.solo.utils.XssUtils;
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

@ControllerAdvice(annotations = {RestController.class, Controller.class})
public class XssRequestBodyAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

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
