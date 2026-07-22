package com.solo.config;

import com.solo.common.utils.XssUtils;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class XssStringArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestParam.class) && parameter.getParameterType() == String.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        String name = getParameterName(parameter, requestParam);
        if (name == null) {
            return null;
        }
        String value = webRequest.getParameter(name);
        if ((value == null || value.isEmpty()) && requestParam != null
                && !ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())) {
            value = requestParam.defaultValue();
        }
        if (XssUtils.shouldSkipField(name)) {
            return value;
        }
        return XssUtils.sanitize(value);
    }

    private String getParameterName(MethodParameter parameter, RequestParam requestParam) {
        if (requestParam != null && !requestParam.name().isBlank()) {
            return requestParam.name();
        }
        if (requestParam != null && !requestParam.value().isBlank()) {
            return requestParam.value();
        }
        return parameter.getParameterName();
    }
}
