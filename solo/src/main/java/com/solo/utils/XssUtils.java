package com.solo.utils;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.Locale;
import java.util.Set;

public final class XssUtils {

    private static final PolicyFactory TEXT_ONLY_POLICY = new HtmlPolicyBuilder().toFactory();
    private static final Set<String> SKIP_FIELD_NAMES = Set.of(
            "password",
            "oldpassword",
            "newpassword",
            "token",
            "refreshtoken",
            "authorization",
            // 结构化/原文文本字段：避免 JSON、Prompt、用户消息等被 HTML 净化破坏
            "modelparams",
            "attachments",
            "variables",
            "example",
            "content",
            "prompt",
            "enhancedprompt"
    );

    private XssUtils() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return TEXT_ONLY_POLICY.sanitize(value);
    }

    public static boolean shouldSkipField(String fieldName) {
        return fieldName != null && SKIP_FIELD_NAMES.contains(fieldName.toLowerCase(Locale.ROOT));
    }
}
