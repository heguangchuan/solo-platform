package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.sip.SipLayer;
import com.solo.gb28181.sip.SipSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import java.util.concurrent.ThreadLocalRandom;

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
     * 查询设备目录（Catalog）
     * 设备会通过多个 MESSAGE Response/Catalog 报文分批返回所有 Item（按 SumNum 累加）
     */
    public void queryCatalog(GbDevice device) {
        int sn = nextSn();
        String xml = DeviceCommandXml.buildQuery("Catalog", sn, device.getDeviceId(), charsetOf(device));
        send(device, xml, "Catalog", sn);
    }

    // ---------------- 内部 ----------------

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

    private static int nextSn() {
        return ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    private static String charsetOf(GbDevice device) {
        return device.getCharset() == null || device.getCharset().isBlank() ? "GB2312" : device.getCharset();
    }
}
