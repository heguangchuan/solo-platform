package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        String localIp = headerProvider.localIp(p);
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
        String localIp = headerProvider.localIp(p);
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
        String localIp = headerProvider.localIp(p);
        String transport = p.getTransport() == null ? "UDP" : p.getTransport();
        String charset = p.getCharacterSet() == null ? "GB2312" : p.getCharacterSet();
        int sn = (int) ((Math.random() * 9 + 1) * 100000);
        String xml = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n" +
                "<Notify>\r\n" +
                "<CmdType>Keepalive</CmdType>\r\n" +
                "<SN>" + sn + "</SN>\r\n" +
                "<DeviceID>" + p.getDeviceGbId() + "</DeviceID>\r\n" +
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
     * <p>不复用请求 fromTag / dialog——每片走全新 fromTag/CallId，SIP 层合规且免去等 200 联动。
     * 分片间 sleep {@code catalogPageIntervalMs} 避免瞬时刷屏。整个过程跑在专属业务线程池，不阻塞调用者。</p>
     *
     * @param platform  上级平台配置
     * @param sn        请求 SN，透传回去
     * @param groups    共享的分组明细（顺序=输出顺序）
     * @param channels  共享的通道明细（顺序=输出顺序；分组会排在通道之前）
     */
    public void sendCatalogResponse(GbParentPlatform platform, String sn,
                                    List<GbGroup> groups, List<GbDeviceChannel> channels) {
        gb28181BizExecutor.execute(() -> doSendCatalogResponse(platform, sn, groups, channels));
    }

    private void doSendCatalogResponse(GbParentPlatform platform, String sn,
                                       List<GbGroup> groups, List<GbDeviceChannel> channels) {
        // group.id (雪花主键) -> group.groupId (20 位国标码)：只包含"在共享列表内"的分组，
        // 仅供通道 Item 的 ParentID 反查（ch.group_id 仍是雪花 id）。分组的 ParentID 现在直接
        // 存 g.getParentId()（国标码），不再走这个 map。
        Map<Long, String> groupIdCode = new HashMap<>(groups.size() * 2);
        for (GbGroup g : groups) {
            groupIdCode.put(g.getId(), g.getGroupId());
        }

        int pageSize = Math.max(1, properties.getCascade().getCatalogPageSize());
        long intervalMs = Math.max(0, properties.getCascade().getCatalogPageIntervalMs());
        int total = groups.size() + channels.size();
        if (total == 0) {
            // 无共享数据仍要回一片空 Response，避免上级卡等
            sendOnePage(platform, sn, 0, List.of(), List.of(), groupIdCode, 1, 1);
            return;
        }

        int totalPages = (total + pageSize - 1) / pageSize;
        // 把 groups + channels 顺序切片：先分组、后通道
        int cursor = 0;
        int pageIdx = 0;
        int gi = 0, ci = 0;
        while (cursor < total) {
            int end = Math.min(cursor + pageSize, total);
            List<GbGroup> pageGroups = new ArrayList<>();
            List<GbDeviceChannel> pageChannels = new ArrayList<>();
            for (int k = cursor; k < end; k++) {
                if (gi < groups.size()) {
                    pageGroups.add(groups.get(gi++));
                } else {
                    pageChannels.add(channels.get(ci++));
                }
            }
            pageIdx++;
            sendOnePage(platform, sn, total, pageGroups, pageChannels, groupIdCode, pageIdx, totalPages);
            cursor = end;

            if (cursor < total && intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[级联目录] 分片间等待被打断，剩余分片放弃发送 -> {}", platform.getServerGbId());
                    return;
                }
            }
        }
    }

    private void sendOnePage(GbParentPlatform platform, String sn, int sumNum,
                             List<GbGroup> pageGroups, List<GbDeviceChannel> pageChannels,
                             Map<Long, String> groupIdCode, int pageIdx, int totalPages) {
        String localIp = headerProvider.localIp(platform);
        String transport = platform.getTransport() == null ? "UDP" : platform.getTransport();
        try {
            String xml = PlatformCatalogXml.buildResponse(platform, sn, sumNum,
                    pageGroups, pageChannels, groupIdCode);
            CallIdHeader callId = sipSender.newCallId(localIp, transport);
            Request request = headerProvider.createMessageRequest(platform, xml, callId);
            sipSender.send(localIp, request);
            log.info("[级联目录] 响应 Catalog 查询 platform={} SN={} sumNum={} page={}/{} items={}",
                    platform.getServerGbId(), sn, sumNum, pageIdx, totalPages,
                    pageGroups.size() + pageChannels.size());
        } catch (Exception e) {
            log.warn("[级联目录] 分片发送失败 platform={} SN={} page={}/{}: {}",
                    platform.getServerGbId(), sn, pageIdx, totalPages, e.getMessage());
        }
    }
}
