package com.solo.openapi.auth;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * IP 白名单校验，支持单 IP 和 CIDR（IPv4/IPv6）。白名单为空表示不校验。
 */
@Component
public class OpenApiIpChecker {

    public boolean allowed(String whitelist, String clientIp) {
        if (!StringUtils.hasText(whitelist)) {
            return true;
        }
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        return Arrays.stream(whitelist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(entry -> matches(entry, clientIp));
    }

    private boolean matches(String entry, String clientIp) {
        try {
            if (entry.contains("/")) {
                return matchesCidr(entry, clientIp);
            }
            return InetAddress.getByName(entry).equals(InetAddress.getByName(clientIp));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean matchesCidr(String cidr, String clientIp) throws UnknownHostException {
        String[] parts = cidr.split("/");
        byte[] network = InetAddress.getByName(parts[0]).getAddress();
        byte[] target = InetAddress.getByName(clientIp).getAddress();
        if (network.length != target.length) {
            return false;
        }
        int prefix = Integer.parseInt(parts[1]);
        int fullBytes = prefix / 8;
        int remainBits = prefix % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != target[i]) {
                return false;
            }
        }
        if (remainBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainBits);
        return (network[fullBytes] & mask) == (target[fullBytes] & mask);
    }
}
