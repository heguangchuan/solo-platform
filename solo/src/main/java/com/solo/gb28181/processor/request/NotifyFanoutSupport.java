package com.solo.gb28181.processor.request;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.service.GbParentPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * <p>各 event 包的 {@code *NotifyListener} 共用：按订阅方查上级平台、订阅匹配。</p>
 */
@Component
@RequiredArgsConstructor
public class NotifyFanoutSupport {

    private final GbParentPlatformService gbParentPlatformService;

    /** 订阅方平台配置——可能已被禁用/删除，按 enabled=1 过滤 */
    public GbParentPlatform platformOf(SubscribeInfo sub) {
        if (sub == null || sub.getPlatformId() == null) {
            return null;
        }
        return gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, sub.getPlatformId())
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
    }

    /** 订阅匹配：空 deviceId = 订阅全部；非空 = 精确匹配 */
    public static boolean matches(SubscribeInfo sub, String deviceId) {
        if (sub == null || sub.getDeviceId() == null || sub.getDeviceId().isEmpty()) {
            return true;
        }
        return sub.getDeviceId().equals(deviceId);
    }
}
