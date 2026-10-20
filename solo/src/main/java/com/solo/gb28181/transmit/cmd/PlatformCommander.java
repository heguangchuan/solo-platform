package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.event.position.DevicePosition;
import com.solo.gb28181.response.RecordItemResponse;
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
import java.util.concurrent.ThreadLocalRandom;
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
            log.info("[级联注册] 发送 {} 有效期:{}s → 上级平台:{} 地址:{}:{}",
                    expires > 0 ? "REGISTER" : "UNREGISTER", expires,
                    p.getServerGbId(), p.getServerIp(), p.getServerPort());
        } catch (Exception e) {
            log.error("[级联注册] 发送 REGISTER 失败 上级平台:{} 错误:{}", p.getServerGbId(), e.getMessage(), e);
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
            log.info("[级联注册] 带认证重发 {} 有效期:{}s → 上级平台:{}",
                    expires > 0 ? "REGISTER" : "UNREGISTER", expires, p.getServerGbId());
        } catch (Exception e) {
            log.error("[级联注册] 带认证重发 REGISTER 失败 上级平台:{} 错误:{}", p.getServerGbId(), e.getMessage(), e);
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
            log.error("[级联心跳] 发送失败 上级平台:{} 错误:{}", p.getServerGbId(), e.getMessage(), e);
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
        log.warn("[级联目录] 上级平台:{} catalogGroup:{} 越界 原因:只允许 1/2/4/8 默认:{}",
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
            log.info("[级联目录] 响应 Catalog 查询 上级平台:{} SN:{} sumNum:{} 分片:{}/{} 条目:{}",
                    platform.getServerGbId(), sn, sumNum, pageIdx, totalPages, pageItems.size());

            // 下一片只在还有剩余时才有意义
            int nextIdx = pageIdx + 1;
            boolean hasNext = nextIdx <= totalPages;

            Consumer<ResponseEvent> onOk = e -> {
                log.debug("[级联目录] ACK 到达 上级平台:{} SN:{} 分片:{}",
                        platform.getServerGbId(), sn, pageIdx);
                if (hasNext) {
                    gb28181BizExecutor.execute(() -> sendPage(platform, sn, sumNum, allItems, nextIdx, totalPages, pageSize));
                }
            };
            Consumer<ResponseEvent> onError = e -> {
                int status = e.getResponse() != null ? e.getResponse().getStatusCode() : -1;
                log.warn("[级联目录] ACK 失败 上级平台:{} SN:{} 分片:{}/{} 状态码:{} 原因:停止后续分片",
                        platform.getServerGbId(), sn, pageIdx, totalPages, status);
                // 4xx/5xx 视为对端已拒收，继续推大概率会重复被拒，整条链停下
            };
            Runnable onTimeout = () -> {
                long fallbackMs = Math.max(0, properties.getCascade().getCatalogPageIntervalMs());
                log.warn("[级联目录] ACK 超时 上级平台:{} SN:{} 分片:{}/{} 延迟:{}ms 原因:fallback 继续",
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
            log.warn("[级联目录] 分片发送失败 上级平台:{} SN:{} 分片:{}/{} 错误:{}",
                    platform.getServerGbId(), sn, pageIdx, totalPages, e.getMessage(), e);
        }
    }

    // ==================== DeviceInfo 业务应答（ADR-031） ====================

    /**
     * 响应上级 {@code Query/DeviceInfo}：本平台登记的设备，回完整厂商/型号/固件。
     * <p>异步走业务线程池；fire-and-forget（不订阅响应，上级是否回 ACK 不重要）。</p>
     */
    public void sendDeviceInfoResponse(GbParentPlatform platform, String sn, GbDevice device) {
        String xml = PlatformDeviceInfoXml.buildResponse(platform, device.getDeviceId(), sn, device);
        sendSingleMessageResponse(platform, xml, "设备信息", sn);
    }

    /**
     * 响应上级 {@code Query/DeviceInfo}：设备未登记，回 {@code Result=ERROR}。
     */
    public void sendDeviceInfoError(GbParentPlatform platform, String deviceId, String sn, String reason) {
        String xml = PlatformDeviceInfoXml.buildErrorResponse(platform, deviceId, sn, reason);
        sendSingleMessageResponse(platform, xml, "设备信息-ERROR", sn);
    }

    // ==================== RecordInfo 业务应答（ADR-031） ====================

    /**
     * 响应上级 {@code Query/RecordInfo}：回录像段列表（设备响应经 SN 重映射后转给上级）。
     * <p>{@code items} 可为空或空集合——空集合会出 {@code SumNum=0 RecordList Num=0} 报文，
     * 用于"设备无录像 / 设备超时未响应"两种场景。</p>
     */
    public void sendRecordInfoResponse(GbParentPlatform platform, String sn, String deviceId,
                                       List<RecordItemResponse> items) {
        String xml = PlatformRecordInfoXml.buildResponse(platform, deviceId, sn, items);
        sendSingleMessageResponse(platform, xml, "录像查询", sn);
    }

    /**
     * 单条 MESSAGE 响应上级的私有 helper：每条独立事务（新 Call-Id / fromTag），走业务线程池。
     * <p>与 {@link #sendCatalogResponse} 的"分片每片一条事务"是同一思路，只是这里不分片。</p>
     */
    private void sendSingleMessageResponse(GbParentPlatform platform, String xml, String label, String sn) {
        gb28181BizExecutor.execute(() -> {
            String localIp = headerProvider.localIp();
            String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
            try {
                CallIdHeader callId = sipSender.newCallId(localIp, transport);
                Request request = headerProvider.createMessageRequest(platform, xml, callId);
                sipSender.send(localIp, request);
                log.info("[级联{}] 响应 上级平台:{} SN:{}", label, platform.getServerGbId(), sn);
            } catch (Exception e) {
                log.warn("[级联{}] 发送失败 上级平台:{} SN:{} 错误:{}",
                        label, platform.getServerGbId(), sn, e.getMessage(), e);
            }
        });
    }

    // ==================== NOTIFY 推送（ADR-030） ====================

    /**
     * 推送 Catalog NOTIFY（设备上下线 / 通道变更）给单个上级平台。
     * <p>fire-and-forget——NOTIFY 是事件流，不等上级回 200；失败仅日志，由订阅方的
     * 重 SUBSCRIBE 兜底（按 SIP 标准做法，GB/T 28181 也未要求 NOTIFY 可靠送达）。</p>
     *
     * @param platform 上级平台
     * @param device   事件源设备（仅用于 DeviceID/Item 字段）
     * @param sn       业务 SN（通常取订阅时收到的 SN 让上级对账）
     */
    public void sendNotifyCatalogItem(GbParentPlatform platform, GbDevice device, String sn) {
        gb28181BizExecutor.execute(() -> {
            String localIp = headerProvider.localIp();
            String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
            try {
                String xml = PlatformNotifyXml.buildCatalogItem(platform,
                        headerProvider.deviceGbId(), device, sn);
                CallIdHeader callId = sipSender.newCallId(localIp, transport);
                Request request = headerProvider.createNotifyRequest(platform, "catalog", xml, callId);
                sipSender.send(localIp, request);
                log.info("[级联订阅] Catalog NOTIFY 推送 上级平台:{} 设备:{} 状态:{} SN:{}",
                        platform.getServerGbId(), device.getDeviceId(),
                        device.getStatus() != null && device.getStatus() == 1 ? "ON" : "OFF", sn);
            } catch (Exception e) {
                log.warn("[级联订阅] Catalog NOTIFY 失败 上级平台:{} 设备:{} 错误:{}",
                        platform.getServerGbId(), device.getDeviceId(), e.getMessage(), e);
            }
        });
    }

    /**
     * 推送多通道 Catalog NOTIFY 给单个上级平台——通道目录同步完成后批量上报
     */
    public void sendNotifyCatalogList(GbParentPlatform platform, String deviceId,
                                      List<GbDeviceChannel> channels, String sn) {
        // 空目录也推（SumNum=0），让上级知道该设备/平台的通道已清空
        List<GbDeviceChannel> target = channels == null ? List.of() : channels;
        gb28181BizExecutor.execute(() -> {
            String localIp = headerProvider.localIp();
            String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
            try {
                String xml = PlatformNotifyXml.buildCatalogList(platform,
                        headerProvider.deviceGbId(), deviceId, target, sn);
                CallIdHeader callId = sipSender.newCallId(localIp, transport);
                Request request = headerProvider.createNotifyRequest(platform, "catalog", xml, callId);
                sipSender.send(localIp, request);
                log.info("[级联订阅] Catalog NOTIFY 推送 上级平台:{} 设备:{} 通道:{} SN:{}",
                        platform.getServerGbId(), deviceId, target.size(), sn);
            } catch (Exception e) {
                log.warn("[级联订阅] Catalog NOTIFY 失败 上级平台:{} 设备:{} 错误:{}",
                        platform.getServerGbId(), deviceId, e.getMessage(), e);
            }
        });
    }

    /**
     * 推送 MobilePosition NOTIFY 给单个上级平台
     */
    public void sendNotifyMobilePosition(GbParentPlatform platform, DevicePosition pos, String sn) {
        gb28181BizExecutor.execute(() -> {
            String localIp = headerProvider.localIp();
            String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
            try {
                String xml = PlatformNotifyXml.buildMobilePosition(platform, pos, sn);
                CallIdHeader callId = sipSender.newCallId(localIp, transport);
                Request request = headerProvider.createNotifyRequest(platform, "mobilePosition", xml, callId);
                sipSender.send(localIp, request);
                log.debug("[级联订阅] MobilePosition NOTIFY 推送 上级平台:{} 设备:{} SN:{}",
                        platform.getServerGbId(), pos.getDeviceId(), sn);
            } catch (Exception e) {
                log.warn("[级联订阅] MobilePosition NOTIFY 失败 上级平台:{} 设备:{} 错误:{}",
                        platform.getServerGbId(), pos.getDeviceId(), e.getMessage(), e);
            }
        });
    }

    /**
     * 推送 Alarm NOTIFY 给单个上级平台
     */
    public void sendNotifyAlarm(GbParentPlatform platform, DeviceAlarm alarm, String sn) {
        gb28181BizExecutor.execute(() -> {
            String localIp = headerProvider.localIp();
            String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
            try {
                String xml = PlatformNotifyXml.buildAlarm(platform, alarm, sn);
                CallIdHeader callId = sipSender.newCallId(localIp, transport);
                Request request = headerProvider.createNotifyRequest(platform, "alarm", xml, callId);
                sipSender.send(localIp, request);
                log.info("[级联订阅] Alarm NOTIFY 推送 上级平台:{} 设备:{} 通道:{} 优先级:{} SN:{}",
                        platform.getServerGbId(), alarm.getDeviceId(), alarm.getChannelId(),
                        alarm.getAlarmPriority(), sn);
            } catch (Exception e) {
                log.warn("[级联订阅] Alarm NOTIFY 失败 上级平台:{} 设备:{} 错误:{}",
                        platform.getServerGbId(), alarm.getDeviceId(), e.getMessage(), e);
            }
        });
    }

    /** 业务 SN（10w-99w，与 SubscribeRequestProcessor / keepalive 同分布） */
    public static int nextSn() {
        return ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }
}
