package com.solo.openapi.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 白名单校验，支持单 IP 和 CIDR（IPv4/IPv6）。白名单为空表示不校验。
 * <p>非法条目在这里跳过并告警，不会抛异常——一条配错的白名单不能把整个应用打成 500，
 * 保存应用时由 {@link #invalidEntries(String)} 先拦下。
 */
@Slf4j
@Component
public class OpenApiIpChecker {

    public boolean allowed(String whitelist, String clientIp) {
        if (!StringUtils.hasText(whitelist)) {
            return true;
        }
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        for (String entry : whitelist.split(",")) {
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            try {
                if (IpRange.parse(entry).contains(clientIp)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                log.warn("[开放API] 忽略非法白名单条目: {}", e.getMessage());
            }
        }
        return false;
    }

    /** 返回格式有问题的条目及原因，空列表表示全部合法 */
    public List<String> invalidEntries(String whitelist) {
        if (!StringUtils.hasText(whitelist)) {
            return List.of();
        }
        List<String> invalid = new ArrayList<>();
        for (String entry : whitelist.split(",")) {
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            try {
                IpRange.parse(entry);
            } catch (IllegalArgumentException e) {
                invalid.add(e.getMessage());
            }
        }
        return invalid;
    }
}
