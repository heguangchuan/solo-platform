package com.solo.openapi.auth;

import com.solo.openapi.config.OpenApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 IP 解析。
 * <p>代理头由客户端随意填写，只有直连地址命中 {@code openapi.trusted-proxies} 时才采信，
 * 否则一律以 TCP 源地址为准；直连部署下伪造 {@code X-Forwarded-For} 不再能绕过白名单。
 * <p>XFF 链路从右往左取第一个非可信代理地址：最左侧是客户端自己写的，取它等于把白名单交给攻击者。
 */
@Slf4j
@Component
public class OpenApiClientIpResolver {

    private final List<IpRange> trustedProxies;

    public OpenApiClientIpResolver(OpenApiProperties properties) {
        List<IpRange> parsed = new ArrayList<>();
        for (String entry : split(properties.getTrustedProxies())) {
            try {
                parsed.add(IpRange.parse(entry));
            } catch (IllegalArgumentException e) {
                log.warn("[开放API] 忽略非法可信代理配置: {}", e.getMessage());
            }
        }
        this.trustedProxies = List.copyOf(parsed);
        if (trustedProxies.isEmpty()) {
            log.info("[开放API] 未配置可信代理，客户端 IP 取 TCP 源地址，不解析代理头");
        }
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!isTrusted(remote)) {
            return remote;
        }
        String chain = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(chain)) {
            chain = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(chain)) {
            return remote;
        }
        String[] hops = chain.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalize(hops[i]);
            if (IpRange.tryParse(hop) == null || isTrusted(hop)) {
                continue;
            }
            return hop;
        }
        return remote;
    }

    private boolean isTrusted(String ip) {
        return trustedProxies.stream().anyMatch(range -> range.contains(ip));
    }

    /** 统一成规范写法；解析不了就原样返回，白名单按不匹配处理 */
    private static String normalize(String ip) {
        if (ip == null) {
            return "unknown";
        }
        byte[] address = IpRange.tryParse(ip);
        return address == null ? ip.trim() : IpRange.format(address);
    }

    private static List<String> split(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                entries.add(part.trim());
            }
        }
        return entries;
    }
}
