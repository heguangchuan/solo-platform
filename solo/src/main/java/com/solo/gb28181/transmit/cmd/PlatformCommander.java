package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.sip.SipSender;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * 级联出站命令（本平台作为下级向上级平台发起）
 *
 * <p>REGISTER 走「按方法名分发」的 {@link com.solo.gb28181.processor.response.RegisterResponseProcessor}
 * 统一处理响应（fire-and-forget 发送即可）；Keepalive 走 {@link SipSender} 的响应订阅以便感知超时。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformCommander {

    private final PlatformSipHeaderProvider headerProvider;
    private final PlatformRegisterState registerState;
    private final SipSender sipSender;
    private final Gb28181Properties properties;
    /** 字段名故意与 Bean 名一致，Spring 按名装配 gb28181-biz 专属线程池（见 Gb28181ThreadPoolConfig） */
    private final ThreadPoolExecutor gb28181BizExecutor;

    /**
     * 首次注册（无认证）。新建 Call-Id / from-tag，记入注册态缓存供响应回查。
     *
     * @param expires 注册有效期秒；传 0 表示注销
     */
    public void register(GbParentPlatform p, int expires) {
        String localIp = headerProvider.localIp();
        String transport = p.getTransport() == null ? "UDP" : p.getTransport();
        try {
            CallIdHeader callId = sipSender.newCallId(localIp, transport);
            String fromTag = SipUtils.newFromTag();
            Request request = headerProvider.createRegisterRequest(p, callId, fromTag, expires, null);
            registerState.markRegistering(callId.getCallId(), p.getServerGbId(), expires > 0);
            sipSender.send(localIp, request);
            log.info("[级联注册] 发送 {} expires={} -> {}({}:{})",
                    expires > 0 ? "REGISTER" : "UNREGISTER", expires,
                    p.getServerGbId(), p.getServerIp(), p.getServerPort());
        } catch (Exception e) {
            log.error("[级联注册] 发送 REGISTER 失败 -> {}: {}", p.getServerGbId(), e.getMessage());
        }
    }

    /**
     * 收到 401 后带 Digest 应答重发（复用响应回显的 Call-Id / from-tag）。
     */
    public void registerWithAuth(GbParentPlatform p, int expires, WWWAuthenticateHeader www,
                                 CallIdHeader callId, String fromTag) {
        String localIp = headerProvider.localIp();
        try {
            Request request = headerProvider.createRegisterRequest(p, callId, fromTag, expires, www);
            registerState.markRegistering(callId.getCallId(), p.getServerGbId(), expires > 0);
            sipSender.send(localIp, request);
            log.info("[级联注册] 带认证重发 {} expires={} -> {}",
                    expires > 0 ? "REGISTER" : "UNREGISTER", expires, p.getServerGbId());
        } catch (Exception e) {
            log.error("[级联注册] 带认证重发 REGISTER 失败 -> {}: {}", p.getServerGbId(), e.getMessage());
        }
    }

    /**
     * 发送心跳（Keepalive Notify）。订阅响应：超时/错误走 onFail，成功走 onOk。
     */
    public void keepalive(GbParentPlatform p, Runnable onOk, Runnable onFail) {
        String localIp = headerProvider.localIp();
        String transport = p.getTransport() == null ? "UDP" : p.getTransport();
        String charset = p.getCharacterSet() == null ? "GB2312" : p.getCharacterSet();
        int sn = (int) ((Math.random() * 9 + 1) * 100000);
        String xml = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n" +
                "<Notify>\r\n" +
                "<CmdType>Keepalive</CmdType>\r\n" +
                "<SN>" + sn + "</SN>\r\n" +
                "<DeviceID>" + properties.getId() + "</DeviceID>\r\n" +
                "<Status>OK</Status>\r\n" +
                "</Notify>\r\n";
        try {
            CallIdHeader callId = sipSender.newCallId(localIp, transport);
            Request request = headerProvider.createMessageRequest(p, xml, callId);
            Consumer<ResponseEvent> ok = e -> { if (onOk != null) onOk.run(); };
            Consumer<ResponseEvent> err = e -> { if (onFail != null) onFail.run(); };
            Runnable timeout = () -> { if (onFail != null) onFail.run(); };
            sipSender.send(localIp, request, ok, err, timeout, 5_000L);
        } catch (Exception e) {
            log.error("[级联心跳] 发送失败 -> {}: {}", p.getServerGbId(), e.getMessage());
            if (onFail != null) onFail.run();
        }
    }

    /**
     * 响应上级 Catalog 查询：按 {@code catalogPageSize} 分片，每片一条独立事务的 MESSAGE。
     * <p><b>ACK 驱动节奏（ADR-027）</b>：每发一片等上级回 200 OK 之后才发下一片——上级快则快、慢则让。
     * 若 {@code catalogAckTimeoutMs} 内没收到 ACK，按 {@code catalogPageIntervalMs} 兜底继续
     * （防止对端死掉时 sender 线程被长时间占用）。整个过程跑在专属业务线程池，不阻塞调用者。</p>
     * <p>每片走全新 fromTag/CallId（与请求 CallId 无关），SIP 层合规，免去 dialog 状态维护。
     * 回调链路靠 {@link SipSubscribe} 按 Call-Id+CSeq 匹配。</p>
     *
     * @param platform 上级平台配置
     * @param sn       请求 SN，透传回去
     * @param items    共享的 catalog item 明细（顺序=输出顺序；分组 parental=1 排在通道 parental=0 之前）
     */
    public void sendCatalogResponse(GbParentPlatform platform, String sn,
                                    List<GbDeviceChannel> items) {
        gb28181BizExecutor.execute(() -> doSendCatalogResponse(platform, sn, items));
    }

    private void doSendCatalogResponse(GbParentPlatform platform, String sn,
                                       List<GbDeviceChannel> items) {
        int pageSize = resolveCatalogPageSize(platform);
        int total = items.size();
        int totalPages = (total + pageSize - 1) / pageSize;
        if (totalPages == 0) {
            // 无共享数据仍要回一片空 Response，避免上级卡等
            sendPage(platform, sn, 0, List.of(), 1, 1, pageSize);
            return;
        }
        sendPage(platform, sn, total, items, 1, totalPages, pageSize);
    }

    /**
     * 取该平台生效的 catalog 单包 Item 数。per-platform 配置（{@code catalogGroup}）优先，
     * 必须是 1/2/4/8；NULL 或越界回落到 {@link Gb28181Properties.Cascade#getCatalogPageSize()}。
     * <p>per-platform 越界时打 warn 不抛错——线上容错优先于拒绝服务。</p>
     */
    private int resolveCatalogPageSize(GbParentPlatform platform) {
        int globalDefault = Math.max(1, properties.getCascade().getCatalogPageSize());
        if (platform == null) {
            return globalDefault;
        }
        Integer v = platform.getCatalogGroup();
        if (v == null) {
            return globalDefault;
        }
        if (v == 1 || v == 2 || v == 4 || v == 8) {
            return v;
        }
        log.warn("[级联目录] 平台 {} catalogGroup={} 越界（只允许 1/2/4/8），回落全局默认={}",
                platform.getServerGbId(), v, globalDefault);
        return globalDefault;
    }

    /**
     * 发送第 {@code pageIdx} 片 Catalog Response，并按回调决定是否继续下一片。
     * 整个链路由 {@link #gb28181BizExecutor} 驱动，每次新一片都重新提交到池里——避免单线程被
     * 长时间占用，pool 大小天然限流。
     *
     * @param pageSize 该平台生效的单包 Item 数（per-platform 或全局默认，由 {@link #resolveCatalogPageSize} 算好）
     */
    private void sendPage(GbParentPlatform platform, String sn, int sumNum,
                          List<GbDeviceChannel> allItems, int pageIdx, int totalPages,
                          int pageSize) {
        int start = (pageIdx - 1) * pageSize;
        int end = Math.min(start + pageSize, allItems.size());
        List<GbDeviceChannel> pageItems = new ArrayList<>(allItems.subList(start, end));

        String localIp = headerProvider.localIp();
        String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
        long ackTimeoutMs = Math.max(0, properties.getCascade().getCatalogAckTimeoutMs());

        try {
            String xml = PlatformCatalogXml.buildResponse(platform, properties.getId(), sn, sumNum, pageItems);
            CallIdHeader callId = sipSender.newCallId(localIp, transport);
            Request request = headerProvider.createMessageRequest(platform, xml, callId);
            log.info("[级联目录] 响应 Catalog 查询 platform={} SN={} sumNum={} page={}/{} items={}",
                    platform.getServerGbId(), sn, sumNum, pageIdx, totalPages, pageItems.size());

            // 下一片只在还有剩余时才有意义
            int nextIdx = pageIdx + 1;
            boolean hasNext = nextIdx <= totalPages;

            Consumer<ResponseEvent> onOk = e -> {
                log.debug("[级联目录] ACK 到达 platform={} SN={} page={}",
                        platform.getServerGbId(), sn, pageIdx);
                if (hasNext) {
                    gb28181BizExecutor.execute(() -> sendPage(platform, sn, sumNum, allItems, nextIdx, totalPages, pageSize));
                }
            };
            Consumer<ResponseEvent> onError = e -> {
                int status = e.getResponse() != null ? e.getResponse().getStatusCode() : -1;
                log.warn("[级联目录] ACK 失败 platform={} SN={} page={}/{} status={}，停止后续分片",
                        platform.getServerGbId(), sn, pageIdx, totalPages, status);
                // 4xx/5xx 视为对端已拒收，继续推大概率会重复被拒，整条链停下
            };
            Runnable onTimeout = () -> {
                long fallbackMs = Math.max(0, properties.getCascade().getCatalogPageIntervalMs());
                log.warn("[级联目录] ACK 超时 platform={} SN={} page={}/{}，{}ms 后 fallback 继续",
                        platform.getServerGbId(), sn, pageIdx, totalPages, fallbackMs);
                if (hasNext) {
                    if (fallbackMs > 0) {
                        gb28181BizExecutor.execute(() -> {
                            try {
                                Thread.sleep(fallbackMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            sendPage(platform, sn, sumNum, allItems, nextIdx, totalPages, pageSize);
                        });
                    } else {
                        gb28181BizExecutor.execute(() -> sendPage(platform, sn, sumNum, allItems, nextIdx, totalPages, pageSize));
                    }
                }
            };

            sipSender.send(localIp, request, onOk, onError, onTimeout, ackTimeoutMs);
        } catch (Exception e) {
            log.warn("[级联目录] 分片发送失败 platform={} SN={} page={}/{}: {}",
                    platform.getServerGbId(), sn, pageIdx, totalPages, e.getMessage());
        }
    }
}
