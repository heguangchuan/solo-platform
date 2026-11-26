package com.solo.gb28181.processor.request;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.time.LocalDateTime;

import static com.solo.gb28181.processor.request.SubscribeResponseBuilder.buildSubscribeOk;
import static com.solo.gb28181.processor.request.SubscribeResponseBuilder.buildTerminated;

// 不认识的来源和黑名单直接丢，别让 NVR 周期 SUBSCRIBE 刷日志。
// expires=0 退订；不认识的 CmdType 回个 30s 的 200，不入订阅表。
// 订阅只放内存，重启后等对端重新 SUBSCRIBE。
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscribeRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private static final String LOG_TAG = "[级联订阅]";

    private static final int DEFAULT_EXPIRES_SECONDS = 600;

    private static final int DEFAULT_UNKNOWN_EXPIRES_SECONDS = 30;

    private static final String CMD_CATALOG = "Catalog";
    private static final String CMD_MOBILE_POSITION = "MobilePosition";
    private static final String CMD_ALARM = "Alarm";

    private final SubscribeHolder subscribeHolder;
    private final GbParentPlatformService gbParentPlatformService;
    private final GbPlatformService gbPlatformService;
    private final Gb28181Properties properties;

    @Override
    public String getMethod() {
        return Request.SUBSCRIBE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        String fromUser = SipUtils.getUserIdFromFromHeader(request);

        if (gbPlatformService.isBlocked(fromUser)) {
            log.warn("{} 命中禁用名单，静默丢弃 SUBSCRIBE: from={}", LOG_TAG, fromUser);
            return;
        }

        // JAIN-SIP 遇到负数 Expires 这类值会直接抛，别让一条坏报文拖死 listener 线程
        try {
            handleInternal(request, fromUser);
        } catch (javax.sip.InvalidArgumentException iae) {
            log.warn("{} SUBSCRIBE 参数越界 from={} : {}",
                    LOG_TAG, fromUser, iae.getMessage());
            safeResponse(request, Response.SERVER_INTERNAL_ERROR,
                    "invalid subscribe parameter");
        } catch (Exception e) {
            log.error("{} 处理 SUBSCRIBE 异常 from={}", LOG_TAG, fromUser, e);
            safeResponse(request, Response.SERVER_INTERNAL_ERROR, "internal error");
        }
    }

    private void handleInternal(SIPRequest request, String fromUser) throws Exception {
        Element root = getRootElement(request);
        if (root == null) {
            log.warn("{} 报文体为空或解析失败 from={}", LOG_TAG, fromUser);
            responseAck(request, Response.BAD_REQUEST, "empty or malformed xml");
            return;
        }

        String cmd = XmlUtils.childText(root, "CmdType");
        String sn = XmlUtils.childText(root, "SN");
        String deviceId = XmlUtils.childText(root, "DeviceID");
        if (cmd.isEmpty()) cmd = CMD_CATALOG; // GB/T 28181 必有，缺失则按 Catalog 处理（最常见）
        String eventId = normalizeEventId(getEventHeader(request));

        GbParentPlatform platform = gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, fromUser)
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
        if (platform == null) {
            return;
        }

        int expires = resolveExpires(request, root);

        if (CMD_CATALOG.equalsIgnoreCase(cmd)
                || CMD_MOBILE_POSITION.equalsIgnoreCase(cmd)
                || CMD_ALARM.equalsIgnoreCase(cmd)) {
            processSubscribe(request, platform, cmd, eventId, sn, deviceId, expires);
        } else {
            // 未知 CmdType：200 + 默认 30s，不入订阅表，避免污染
            log.info("{} 收到 SUBSCRIBE platform={} cmd={} event={} expires={}s（未知 CmdType，按 30s 默认响应）",
                    LOG_TAG, fromUser, cmd, eventId, DEFAULT_UNKNOWN_EXPIRES_SECONDS);
            sendDefaultOk(request, eventId, DEFAULT_UNKNOWN_EXPIRES_SECONDS);
        }
    }

    private void safeResponse(SIPRequest request, int statusCode, String reason) {
        try {
            responseAck(request, statusCode, reason);
        } catch (Exception e) {
            log.warn("{} 回 {} 失败 reason={} : {}", LOG_TAG, statusCode, reason, e.getMessage());
        }
    }

    private void processSubscribe(SIPRequest request, GbParentPlatform platform,
                                  String cmd, String eventId, String sn, String deviceId,
                                  int expires) throws Exception {
        String platformId = platform.getServerGbId();

        if (expires <= 0) {
            SubscribeInfo removed = subscribeHolder.remove(platformId, cmd);
            log.info("{} 退订 platform={} cmd={} deviceId={} SN={}（{}前订阅）",
                    LOG_TAG, platformId, cmd, deviceId, sn, removed == null ? "无" : "有");
            sipSender.send(request.getLocalAddress().getHostAddress(),
                    buildTerminated(request, eventId));
            return;
        }

        // Catalog 的 DeviceID 为空或就是本平台，表示订阅整个平台，统一存空串
        boolean platformCatalog = CMD_CATALOG.equalsIgnoreCase(cmd)
                && (deviceId == null || deviceId.isEmpty() || deviceId.equals(properties.getId()));
        String subscribedDeviceId = platformCatalog ? "" : deviceId;
        SubscribeInfo info = new SubscribeInfo(
                platformId, cmd, subscribedDeviceId, sn, expires, LocalDateTime.now());
        subscribeHolder.put(info);

        log.info("{} 接受订阅 platform={} cmd={} deviceId={}({}) SN={} expires={}s 当前订阅总数={}",
                LOG_TAG, platformId, cmd, deviceId,
                platformCatalog ? "我" : "", sn, expires, subscribeHolder.size());

        String body = buildResponseBody(cmd, sn, deviceId);
        sipSender.send(request.getLocalAddress().getHostAddress(),
                buildSubscribeOk(request, eventId, expires, body));
    }

    private void sendDefaultOk(SIPRequest request, String eventId, int expires) throws Exception {
        sipSender.send(request.getLocalAddress().getHostAddress(),
                buildSubscribeOk(request, eventId, expires, null));
    }

    // 先看 SIP Expires 头，再看 XML；负数按退订，最大钳到 24h，免得 JAIN-SIP 抛错。
    private static int resolveExpires(SIPRequest request, Element root) {
        int raw = readExpiresRaw(request, root);
        if (raw < 0) {
            return 0; // 负数视同退订；HeaderFactory 不接受负值，钳到 0 后走退订分支
        }
        if (raw > MAX_EXPIRES_SECONDS) {
            return MAX_EXPIRES_SECONDS; // 钳上限，避免上游发天文数字撑爆订阅表
        }
        return raw;
    }

    private static final int MAX_EXPIRES_SECONDS = 86400;

    private static int readExpiresRaw(SIPRequest request, Element root) {
        ExpiresHeader eh = request.getExpires();
        if (eh != null && eh.getExpires() >= 0) {
            return eh.getExpires();
        }
        String bodyExpires = XmlUtils.childText(root, "Expires");
        if (!bodyExpires.isEmpty()) {
            try {
                return Integer.parseInt(bodyExpires.trim());
            } catch (NumberFormatException ignored) {
                // 落默认
            }
        }
        return DEFAULT_EXPIRES_SECONDS;
    }

    private static String getEventHeader(SIPRequest request) {
        EventHeader eh = (EventHeader) request.getHeader(EventHeader.NAME);
        return eh == null ? "" : eh.getEventType();
    }

    // 有的对端发完整 urn，只取最后一个冒号后面的 catalog/mobilePosition。
    private static String normalizeEventId(String raw) {
        if (raw == null) {
            return CMD_CATALOG.toLowerCase();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return CMD_CATALOG.toLowerCase();
        }
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < s.length()) {
            s = s.substring(colon + 1);
        }
        if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) {
            s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    private static String buildResponseBody(String cmd, String sn, String deviceId) {
        return "<?xml version=\"1.0\" encoding=\"GB2312\"?>\r\n"
                + "<Response>\r\n"
                + "<CmdType>" + cmd + "</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\r\n"
                + "<Result>OK</Result>\r\n"
                + "</Response>\r\n";
    }
}
