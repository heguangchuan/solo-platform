package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbInviteSession;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * SIP 主动命令入口
 * <p>所有"本平台向下级设备发起的业务命令"在这里组装并发送。</p>
 * <p>每个命令的 XML 报文体单独抽到 {@link DeviceCommandXml} 中，便于复用与测试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipCommander {

    private final SipLayer sipLayer;
    private final SipSender sipSender;
    private final SipRequestHeaderProvider headerProvider;

    /**
     * 查询设备信息（DeviceInfo）
     * 设备会通过 MESSAGE Response/DeviceInfo 报文返回 manufacturer / model / firmware / deviceName 等
     */
    public void queryDeviceInfo(GbDevice device) {
        int sn = nextSn();
        String xml = DeviceCommandXml.buildQuery("DeviceInfo", sn, device.getDeviceId(), charsetOf(device));
        send(device, xml, "DeviceInfo", sn);
    }

    /**
     * 查询设备信息并等待应答（用于手动存活探测）
     * <p>通过 {@link SipSender#send} 带回调发送：设备回任何响应触发 {@code onOk}，
     * 事务超时触发 {@code onError}，业务超时由 {@link com.solo.gb28181.sip.SipSubscribe#sweep()} 回收。</p>
     *
     * @param onOk      收到设备应答回调（存活）
     * @param onError   非 2xx 回调
     * @param timeoutMs 订阅超时（毫秒，0 = 用默认 10s）
     */
    public void queryDeviceInfo(GbDevice device,
                                Consumer<ResponseEvent> onOk,
                                Consumer<ResponseEvent> onError,
                                long timeoutMs) {
        queryDeviceInfo(device, onOk, onError, null, timeoutMs);
    }

    /**
     * 查询设备信息并登记存活/超时回调（用于手动存活探测，fire-and-forget）
     * <p>设备回包→{@code onOk}；超时无应答→{@code onTimeout}。
     * 发送失败时同步调 {@code onError} 让调用方知晓。</p>
     */
    public void queryDeviceInfo(GbDevice device,
                                Consumer<ResponseEvent> onOk,
                                Consumer<ResponseEvent> onError,
                                Runnable onTimeout,
                                long timeoutMs) {
        int sn = nextSn();
        String xml = DeviceCommandXml.buildQuery("DeviceInfo", sn, device.getDeviceId(), charsetOf(device));
        try {
            CallIdHeader callId = headerProvider.newCallId(device);
            Request request = headerProvider.buildMessageRequest(device, xml, callId);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request, onOk, onError, onTimeout, timeoutMs);
            log.info("[设备检测] DeviceInfo 探测 → deviceId={} sn={} timeoutMs={}", device.getDeviceId(), sn, timeoutMs);
        } catch (Exception e) {
            log.error("[设备检测] DeviceInfo 探测发送失败 deviceId={}", device.getDeviceId(), e);
            if (onError != null) {
                try { onError.accept(null); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * 查询设备目录（Catalog）
     * 设备会通过多个 MESSAGE Response/Catalog 报文分批返回所有 Item（按 SumNum 累加）
     */
    public void queryCatalog(GbDevice device) {
        int sn = nextSn();
        String xml = DeviceCommandXml.buildQuery("Catalog", sn, device.getDeviceId(), charsetOf(device));
        send(device, xml, "Catalog", sn);
    }

    /**
     * 查询通道录像（RecordInfo）
     * <p>设备返回 Response/RecordInfo 分片报文，由 {@code RecordInfoResponseMessageHandler} 累计。
     * 调用方需要预先把 sn 注册到 pending 表，等响应到达时按 sn 唤醒等待者。</p>
     *
     * @param device     设备
     * @param channelId  通道国标 ID（RecordInfo 查询的是通道，不是设备本体）
     * @param sn         调用方持有的会话 SN，用于响应分片关联
     * @param startTime  ISO-8601 起始时间，例 {@code 2026-06-30T00:00:00}
     * @param endTime    ISO-8601 结束时间
     */
    public void queryRecordInfo(GbDevice device, String channelId, int sn, String startTime, String endTime) {
        String xml = DeviceCommandXml.buildRecordInfoQuery(sn, channelId, startTime, endTime, charsetOf(device));
        send(device, xml, "RecordInfo", sn);
    }

    // ---------------- INVITE / BYE (S2.2 实时点播) ----------------

    /**
     * 发起 INVITE
     *
     * @param device     目标设备
     * @param channelId  通道国标 ID
     * @param sdpBody    SDP 报文（由 {@link SdpBuilder} 生成，码流标识已写入其 a= 行）
     * @param ssrc       10 位 SSRC
     * @param callId     调用方持有的 Call-Id（保存到 invite session）
     * @param fromTag    调用方持有的 from-tag（保存到 invite session）
     * @param onOk       2xx 回调；通常用于解析 200 OK 中真实 SSRC（业务确认）
     * @param onError    非 2xx 回调
     * @param timeoutMs  订阅超时（毫秒）
     */
    public void sendInvite(GbDevice device, String channelId, String sdpBody, String ssrc,
                           CallIdHeader callId, String fromTag,
                           Consumer<ResponseEvent> onOk,
                           Consumer<ResponseEvent> onError,
                           long timeoutMs) {
        try {
            Request request = headerProvider.buildInviteRequest(device, channelId, sdpBody, ssrc, callId, fromTag);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request, onOk, onError, timeoutMs);
            log.info("[实时点播] INVITE → deviceId={} channelId={} ssrc={} callId={}",
                    device.getDeviceId(), channelId, ssrc, callId.getCallId());
        } catch (Exception e) {
            log.error("[实时点播] INVITE 发送失败 deviceId={} channelId={}",
                    device.getDeviceId(), channelId, e);
            if (onError != null) {
                try { onError.accept(null); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * 发起 BYE（结束既有 INVITE Dialog）
     */
    public void sendBye(GbDevice device, GbInviteSession session) {
        try {
            Request request = headerProvider.buildByeRequest(device, session);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request);
            log.info("[实时点播] BYE → deviceId={} channelId={} stream={}",
                    device.getDeviceId(), session.getChannelId(), session.getStreamId());
        } catch (Exception e) {
            log.error("[实时点播] BYE 发送失败 deviceId={} stream={}",
                    device.getDeviceId(), session.getStreamId(), e);
        }
    }

    // ---------------- INFO (S3 回放控制) ----------------

    /**
     * 在既有 INVITE Dialog 内发送 SIP INFO（携带 MANSRTSP body）
     * <p>用于历史回放的 PAUSE / PLAY / SCALE / SEEK 控制；rtspBody 由 {@link RtspControlBuilder} 生成。</p>
     */
    public void sendInfo(GbDevice device, GbInviteSession session, String rtspBody) {
        try {
            Request request = headerProvider.buildInfoRequest(device, session, rtspBody);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request);
            log.info("[回放控制] INFO → deviceId={} stream={}", device.getDeviceId(), session.getStreamId());
        } catch (Exception e) {
            log.error("[回放控制] INFO 发送失败 deviceId={} stream={}",
                    device.getDeviceId(), session.getStreamId(), e);
        }
    }

    // ---------------- 内部 ----------------

    /**
     * 发送 PTZ 云台控制指令（SIP MESSAGE → DeviceControl/PTZCmd）
     *
     * @param device    目标设备
     * @param channelId 通道国标 ID
     * @param ptzCmd    8 字节 PTZ 控制码十六进制串（{@link com.solo.gb28181.utils.SipUtils#buildPtzCmd}）
     */
    public void sendPtzCmd(GbDevice device, String channelId, String ptzCmd) {
        int sn = nextSn();
        String xml = DeviceCommandXml.buildPtzControl(sn, channelId, ptzCmd, charsetOf(device));
        try {
            CallIdHeader callId = headerProvider.newCallId(device);
            Request request = headerProvider.buildMessageRequest(device, xml, callId);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request);
            log.info("[云台控制] PTZ → deviceId={} channelId={} cmd={}", device.getDeviceId(), channelId, ptzCmd);
        } catch (Exception e) {
            log.error("[云台控制] PTZ 发送失败 deviceId={} channelId={}", device.getDeviceId(), channelId, e);
        }
    }

    private void send(GbDevice device, String xml, String cmdType, int sn) {
        try {
            CallIdHeader callId = headerProvider.newCallId(device);
            Request request = headerProvider.buildMessageRequest(device, xml, callId);
            sipSender.send(sipLayer.getLocalIp(device.getLocalIp()), request);
            log.info("[消息发送] {} 查询 → deviceId={} sn={}", cmdType, device.getDeviceId(), sn);
        } catch (Exception e) {
            log.error("[消息发送] {} 查询失败 deviceId={}", cmdType, device.getDeviceId(), e);
        }
    }

    public static int nextSn() {
        return ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    private static String charsetOf(GbDevice device) {
        return device.getCharset() == null || device.getCharset().isBlank() ? "GB2312" : device.getCharset();
    }
}
