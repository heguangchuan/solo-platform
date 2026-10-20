package com.solo.gb28181.subscribe;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.transmit.cmd.PlatformNotifyXml;
import com.solo.gb28181.transmit.cmd.SipRequestHeaderProvider;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 上级平台目录订阅管理（被动端）
 * <p>管理哪些上级订阅了哪台设备的目录，通道变化时主动 NOTIFY 推送。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformCatalogSubscribeManager {

    private static final String LOG_TAG = "[级联目录订阅]";

    private final SipSender sipSender;
    private final SipRequestHeaderProvider headerProvider;
    private final GbDeviceChannelService channelService;
    private final GbParentPlatformService parentPlatformService;

    /** key = platformId + "_" + deviceId */
    private final ConcurrentHashMap<String, SubscribeState> subscribes = new ConcurrentHashMap<>();

    public void onSubscribe(String platformId, String deviceId, SIPRequest request, int expires) {
        String key = key(platformId, deviceId);
        SubscribeState state = subscribes.computeIfAbsent(key, k -> new SubscribeState());
        state.platformId = platformId;
        state.deviceId = deviceId;
        state.expireTime = Instant.now().plusSeconds(expires);
        state.callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
        state.fromTag = ((FromHeader) request.getHeader(FromHeader.NAME)).getTag();
        state.toTag = ((ToHeader) request.getHeader(ToHeader.NAME)).getTag();
        state.remoteIp = SipUtils.getRemoteAddress(request);
        state.remotePort = SipUtils.getRemotePort(request);
        state.transport = SipUtils.getTransport(request);

        log.info("{} 订阅 platform={} deviceId={} expires={}s", LOG_TAG, platformId, deviceId, expires);
    }

    public void onUnsubscribe(String platformId, String deviceId, SIPRequest request) {
        String key = key(platformId, deviceId);
        if (subscribes.remove(key) != null) {
            log.info("{} 退订 platform={} deviceId={}", LOG_TAG, platformId, deviceId);
        }
    }

    /**
     * 通道变化时通知所有订阅的上级
     */
    public void notifyChannelChange(String deviceId) {
        List<SubscribeState> targets = subscribes.values().stream()
                .filter(s -> deviceId.equals(s.deviceId))
                .toList();
        if (targets.isEmpty()) return;

        List<GbDeviceChannel> channels = channelService.listByDevice(deviceId);
        if (channels == null) channels = List.of();

        for (SubscribeState state : targets) {
            sendNotify(state, channels);
        }
    }

    private void sendNotify(SubscribeState state, List<GbDeviceChannel> channels) {
        try {
            GbParentPlatform platform = parentPlatformService.lambdaQuery()
                    .eq(GbParentPlatform::getServerGbId, state.platformId)
                    .one();
            if (platform == null) return;

            String sn = String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 999_999));
            String xml = PlatformNotifyXml.buildCatalogList(
                    platform, state.deviceId, state.deviceId, channels, sn);

            String charset = platform.getCharacterSet() == null || platform.getCharacterSet().isBlank()
                    ? "GB2312" : platform.getCharacterSet();

            Request notify = headerProvider.buildCatalogNotifyToPlatform(
                    state.platformId,
                    state.remoteIp + ":" + state.remotePort,
                    state.transport,
                    state.deviceId,
                    null,
                    xml,
                    charset);

            sipSender.send(null, notify);
            log.info("{} NOTIFY 推送 platform={} deviceId={} channels={}",
                    LOG_TAG, state.platformId, state.deviceId, channels.size());
        } catch (Exception e) {
            log.error("{} NOTIFY 推送异常 platform={} deviceId={}",
                    LOG_TAG, state.platformId, state.deviceId, e);
        }
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void cleanupExpired() {
        Instant now = Instant.now();
        subscribes.entrySet().removeIf(entry -> {
            if (entry.getValue().expireTime.isBefore(now)) {
                log.info("{} 订阅过期自动清理 platform={} deviceId={}",
                        LOG_TAG, entry.getValue().platformId, entry.getValue().deviceId);
                return true;
            }
            return false;
        });
    }

    private String key(String platformId, String deviceId) {
        return platformId + "_" + deviceId;
    }

    private static class SubscribeState {
        String platformId;
        String deviceId;
        Instant expireTime;
        String callId;
        String fromTag;
        String toTag;
        String remoteIp;
        int remotePort;
        String transport;
    }
}
