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

/**
 * SUBSCRIBE 请求处理器（ADR-029）。
 * <p>本平台作为下级时，收到上级对 {@code Event: catalog} / {@code Event: mobilePosition} 的 SUBSCRIBE
 * 后回 200 OK（含 {@code Subscription-State: active;expires=N} + {@code Event} 头 + MANSCDP+xml body）
 * 并把订阅关系登记到 {@link SubscribeHolder}，供后续变更 NOTIFY 推送使用（ADR-030 跟进）。</p>
 *
 * <h3>CmdType 分支</h3>
 * <ul>
 *   <li>{@code Catalog}：登记订阅，回 OK。当前 NOTIFY 推送未实现，但订阅关系已入表，将来 catalog
 *       变更即可遍历订阅方推 NOTIFY。</li>
 *   <li>{@code MobilePosition}：200+接受，订阅入表。当前 NOTIFY 推送未实现，先留订阅关系。</li>
 *   <li>{@code Alarm}：200+接受，订阅入表。当前 NOTIFY 推送未实现，订阅关系入表为后续 ADR-030 推送打底
 *       （Sprint 5 报警数据不落库，仅日志，订阅方仅登记）。</li>
 *   <li>其他：200 + 默认 30s Expires，不打 ERROR，避免 S5 启动前的告警噪音。</li>
 * </ul>
 *
 * <h3>退订（Expires=0）</h3>
 * 从订阅表移除，回 200 + {@code Subscription-State: terminated}。
 *
 * <h3>不命中处理</h3>
 * <ul>
 *   <li>黑名单（{@code gb_platform.enabled=0}）：静默丢弃，与 {@code RegisterRequestProcessor} 一致。</li>
 *   <li>报文解析失败：400 Bad Request（语法层错误要告诉对方）。</li>
 *   <li>fromUser 未匹配到上级平台：静默丢弃——避免 NVR 周期性 SUBSCRIBE 刷屏。</li>
 * </ul>
 *
 * <h3>不实现</h3>
 * <ul>
 *   <li>NOTIFY 推送（需要 catalog 变更事件总线）→ ADR-030</li>
 *   <li>订阅持久化（GB 协议默许重启重发）</li>
 * </ul>
 *
 * @see SubscribeHolder
 * @see SubscribeInfo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscribeRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private static final String LOG_TAG = "[级联订阅]";

    /** GB/T 28181 建议目录订阅有效期。 */
    private static final int DEFAULT_EXPIRES_SECONDS = 600;

    /** 未知 CmdType 默认 Expires（30s 短期订阅，避免无谓占用订阅表）。 */
    private static final int DEFAULT_UNKNOWN_EXPIRES_SECONDS = 30;

    /** MANSCDP+xml 响应 body 中事件名（与 CmdType 一致，全小写驼峰）。 */
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

        // 黑名单拦截——与 REGISTER/MESSAGE 入口一致
        if (gbPlatformService.isBlocked(fromUser)) {
            log.warn("{} 命中禁用名单，静默丢弃 SUBSCRIBE: from={}", LOG_TAG, fromUser);
            return;
        }

        // 解析 + 业务处理全程包 try/catch——JAIN-SIP 多个 header factory 在边界值下会抛
        // InvalidArgumentException（典型：createExpiresHeader(int) 不接受负数），不能让单条
        // 坏报文把整条 SIP listener 线程拖死。失败时回 500 + 业务日志，让对端按自身重传机制处理。
        try {
            handleInternal(request, fromUser);
        } catch (javax.sip.InvalidArgumentException iae) {
            log.warn("{} SUBSCRIBE 参数越界 from={} cmd={} : {}",
                    LOG_TAG, fromUser, iae.getMessage());
            safeResponse(request, Response.SERVER_INTERNAL_ERROR,
                    "invalid subscribe parameter");
        } catch (Exception e) {
            log.error("{} 处理 SUBSCRIBE 异常 from={}", LOG_TAG, fromUser, e);
            safeResponse(request, Response.SERVER_INTERNAL_ERROR, "internal error");
        }
    }

    /**
     * 解析 body 并按 CmdType 分发。失败回 SIP 错误响应，失败本身只 log 不抛。
     */
    private void handleInternal(SIPRequest request, String fromUser) throws Exception {
        // body 解析
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

        // 上级平台查询——与 MessageRequestProcessor.process 范式一致
        GbParentPlatform platform = gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, fromUser)
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
        if (platform == null) {
            // 未注册来源 SUBSCRIBE：与 MessageRequestProcessor "Catalog 未注册" 静默丢弃策略一致，
            // 避免 NVR 周期性 SUBSCRIBE 刷屏。
            return;
        }

        int expires = resolveExpires(request, root);

        // 分支
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

    /**
     * 兜底回 SIP 错误响应——只 log 不抛，避免坏报文把监听线程拖死。
     */
    private void safeResponse(SIPRequest request, int statusCode, String reason) {
        try {
            responseAck(request, statusCode, reason);
        } catch (Exception e) {
            log.warn("{} 回 {} 失败 reason={} : {}", LOG_TAG, statusCode, reason, e.getMessage());
        }
    }

    /**
     * 处理已识别的 SUBSCRIBE（Catalog / MobilePosition / Alarm）：回 200 OK + 登记/续订/退订。
     */
    private void processSubscribe(SIPRequest request, GbParentPlatform platform,
                                  String cmd, String eventId, String sn, String deviceId,
                                  int expires) throws Exception {
        String platformId = platform.getServerGbId();

        if (expires <= 0) {
            // 退订
            SubscribeInfo removed = subscribeHolder.remove(platformId, cmd);
            log.info("{} 退订 platform={} cmd={} deviceId={} SN={}（{}前订阅）",
                    LOG_TAG, platformId, cmd, deviceId, sn, removed == null ? "无" : "有");
            sipSender.send(request.getLocalAddress().getHostAddress(),
                    buildTerminated(request, eventId));
            return;
        }

        // 订阅/续订：入表
        // 级联订阅：DeviceID == 本级平台 ID → 归一化为空（订阅整个平台目录，推送按 share 过滤）
        if (CMD_CATALOG.equalsIgnoreCase(cmd) && deviceId != null && deviceId.equals(properties.getId())) {
            deviceId = "";
        }
        SubscribeInfo info = new SubscribeInfo(
                platformId, cmd, deviceId, sn, expires, LocalDateTime.now());
        subscribeHolder.put(info);

        log.info("{} 接受订阅 platform={} cmd={} deviceId={} SN={} expires={}s 当前订阅总数={}",
                LOG_TAG, platformId, cmd, deviceId, sn, expires, subscribeHolder.size());

        String body = buildResponseBody(cmd, sn, deviceId);
        sipSender.send(request.getLocalAddress().getHostAddress(),
                buildSubscribeOk(request, eventId, expires, body));
    }

    /**
     * 未知 CmdType 的兜底：200 + 默认 Expires + Subscription-State: active，不入订阅表，不打 ERROR。
     */
    private void sendDefaultOk(SIPRequest request, String eventId, int expires) throws Exception {
        sipSender.send(request.getLocalAddress().getHostAddress(),
                buildSubscribeOk(request, eventId, expires, null));
    }

    /**
     * 解析 SUBSCRIBE 的 Expires：优先 SIP {@code Expires} 头，否则取 body {@code <Expires>} 元素，
     * 否则默认 600s。返回 0 表示退订。负数 / 越界值钳到 [0, MAX_SECONDS] 区间，规避 JAIN-SIP
     * {@code createExpiresHeader(int)} 在 {@code expires < 0} 时抛 {@code InvalidArgumentException}。
     */
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

    /** Expires 上限：GB/T 28181 建议目录订阅最长 24h，再长没意义。 */
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

    /**
     * 把 Event 头取值规整成"小写驼峰"，与 response 头兼容（catalog / mobilePosition）。
     * 设备 / 上级有时带完整 URI 形式（{@code <urn:gb28181:sip:event:catalog>}），
     * 这里只取冒号后最后一段，避免响应头变成巨长字符串。
     */
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
        // 首字母小写
        if (!s.isEmpty() && Character.isUpperCase(s.charAt(0))) {
            s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    /**
     * 构造响应 body（MANSCDP+xml）：
     * <pre>
     * &lt;Response&gt;
     *   &lt;CmdType&gt;Catalog&lt;/CmdType&gt;
     *   &lt;SN&gt;...&lt;/SN&gt;
     *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
     *   &lt;Result&gt;OK&lt;/Result&gt;
     * &lt;/Response&gt;
     * </pre>
     */
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
