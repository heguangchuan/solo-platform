package com.solo.gb28181.transmit.cmd;

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
}
