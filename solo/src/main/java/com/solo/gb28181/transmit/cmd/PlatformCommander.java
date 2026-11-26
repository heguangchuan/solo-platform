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

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformCommander {

    private final PlatformSipHeaderProvider headerProvider;
    private final PlatformRegisterState registerState;
    private final SipSender sipSender;
    private final Gb28181Properties properties;
    private final ThreadPoolExecutor gb28181BizExecutor;

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

    public void broadcastResultCmd(GbParentPlatform p, int sn, String channelId, boolean ok) {
        String localIp = headerProvider.localIp();
        try {
            Request request = headerProvider.buildBroadcastResponse(p, sn, channelId, ok);
            sipSender.send(localIp, request);
            log.info("[级联喊话] 已回复广播应答 上级:{} Result:{} 通道:{}", p.getServerGbId(), ok ? "OK" : "ERROR", channelId);
        } catch (Exception e) {
            log.error("[级联喊话] 回复广播应答失败 上级:{} 错误:{}", p.getServerGbId(), e.getMessage(), e);
        }
    }

    public void broadcastByeCmd(GbParentPlatform p, String callId, String localFromTag, String upstreamToTag) {
        String localIp = headerProvider.localIp();
        try {
            Request request = headerProvider.createByeAsCaller(p, callId, localFromTag, upstreamToTag);
            sipSender.send(localIp, request);
            log.info("[级联喊话] 已给上级发 BYE 上级:{} callId:{}", p.getServerGbId(), callId);
        } catch (Exception e) {
            log.warn("[级联喊话] 给上级发 BYE 异常(忽略) 上级:{} callId:{} 错误:{}",
                    p.getServerGbId(), callId, e.getMessage());
        }
    }

    // 每片等上级 200 OK 再发下一片；超时按配置间隔继续，4xx/5xx 就停。
    // 每片都是新 CallId/fromTag 的独立 MESSAGE。
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

    // 平台自己的 catalogGroup 只认 1/2/4/8，其他值用全局默认。
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
                // 对端明确拒收就别接着推后面的片了
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


    public void sendDeviceInfoResponse(GbParentPlatform platform, String sn, GbDevice device) {
        String xml = PlatformDeviceInfoXml.buildResponse(platform, device.getDeviceId(), sn, device);
        sendSingleMessageResponse(platform, xml, "设备信息", sn);
    }

    public void sendDeviceInfoError(GbParentPlatform platform, String deviceId, String sn, String reason) {
        String xml = PlatformDeviceInfoXml.buildErrorResponse(platform, deviceId, sn, reason);
        sendSingleMessageResponse(platform, xml, "设备信息-ERROR", sn);
    }


    public void sendDeviceStatusResponse(GbParentPlatform platform, String sn, GbDevice device) {
        String xml = PlatformDeviceStatusXml.buildResponse(platform, device.getDeviceId(), sn, device);
        sendSingleMessageResponse(platform, xml, "设备状态", sn);
    }

    public void sendDeviceStatusResponse(GbParentPlatform platform, String sn,
                                          String deviceId, String channelStatus) {
        String xml = PlatformDeviceStatusXml.buildResponse(platform, deviceId, sn, channelStatus);
        sendSingleMessageResponse(platform, xml, "设备状态-通道", sn);
    }

    public void sendDeviceStatusError(GbParentPlatform platform, String deviceId, String sn, String reason) {
        String xml = PlatformDeviceStatusXml.buildErrorResponse(platform, deviceId, sn, reason);
        sendSingleMessageResponse(platform, xml, "设备状态-ERROR", sn);
    }


    // 空列表也要回 SumNum=0 + 空 RecordList，别让上级一直等。
    public void sendRecordInfoResponse(GbParentPlatform platform, String sn, String deviceId,
                                       List<RecordItemResponse> items) {
        String xml = PlatformRecordInfoXml.buildResponse(platform, deviceId, sn, items);
        sendSingleMessageResponse(platform, xml, "录像查询", sn);
    }

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


    // NOTIFY 不等 200，丢了就靠上级重新 SUBSCRIBE。
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

    public static int nextSn() {
        return ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }
}
