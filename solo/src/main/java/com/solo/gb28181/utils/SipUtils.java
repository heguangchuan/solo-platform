package com.solo.gb28181.utils;

import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Subject;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderAddress;
import javax.sip.header.SubjectHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@UtilityClass
public class SipUtils {


    public static String getUserIdFromFromHeader(Request request) {
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        return getUser(from);
    }

    public static String getUserIdFromToHeader(Request request) {
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        return getUser(to);
    }

    private static String getUser(HeaderAddress h) {
        if (h == null || h.getAddress() == null) return null;
        if (h.getAddress() instanceof AddressImpl a && a.getURI() instanceof SipUri uri) {
            return uri.getUser();
        }
        if (h.getAddress().getURI() instanceof SipURI uri) {
            return uri.getUser();
        }
        return null;
    }

    // NAT 后 From 里的内网地址没用。默认拿收包源地址；关掉后才先看 Via 的 received/rport。
    public static RemoteAddressInfo getRemoteAddress(SIPRequest request, boolean useSourceIpAsRemoteAddress) {
        String ip;
        int port;
        java.net.InetAddress peerAddr = request.getPeerPacketSourceAddress();
        int peerPort = request.getPeerPacketSourcePort();
        boolean hasPeer = peerAddr != null && peerPort > 0;

        if (useSourceIpAsRemoteAddress && hasPeer) {
            ip = peerAddr.getHostAddress();
            port = peerPort;
        } else {
            ViaHeader via = request.getTopmostViaHeader();
            ip = via.getReceived();
            port = via.getRPort();
            if ((ip == null || ip.isBlank() || port == -1) && hasPeer) {
                ip = peerAddr.getHostAddress();
                port = peerPort;
            }
            // 最后兜底：Via 头的 host/port（TCP 或特殊场景 peerAddress 可能为 null）
            if (ip == null || ip.isBlank()) {
                ip = via.getHost();
            }
            if (port == -1) {
                port = via.getPort();
            }
        }
        return new RemoteAddressInfo(ip, port);
    }

    public static String getTransport(Request request) {
        if (request instanceof SIPRequest r) {
            ViaHeader via = r.getTopmostViaHeader();
            return via == null ? "UDP" : via.getTransport();
        }
        return "UDP";
    }


    // Subject 长这样：channelId:ssrc,remoteId:seq；返回 [channelId, remoteId]。
    public static String[] getChannelIdFromSubject(Request request) {
        SubjectHeader subject = (Subject) request.getHeader("subject");
        if (subject == null) return null;
        String s = subject.getSubject();
        String[] result = new String[2];
        if (s.contains(",")) {
            String[] parts = s.split(",");
            result[0] = parts[0].split(":")[0];
            result[1] = parts[1].split(":")[0];
        } else {
            result[0] = s.split(":")[0];
        }
        return result;
    }


    public static String newViaTag() {
        return "z9hG4bK" + ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999);
    }

    public static String newFromTag() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String newToTag() {
        return String.valueOf(System.currentTimeMillis());
    }

    public static String newCallIdSeed() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000_000));
    }


    public static int getTypeCodeFromGbCode(String deviceId) {
        if (deviceId == null || deviceId.length() < 13) return 0;
        try {
            return Integer.parseInt(deviceId.substring(10, 13));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isFrontEnd(String deviceId) {
        int type = getTypeCodeFromGbCode(deviceId);
        return type > 130 && type < 199;
    }

    public static String toBusinessGroupId(String gbCode) {
        if (gbCode == null || gbCode.length() != 20) return gbCode;
        return gbCode.substring(0, 10) + "215" + gbCode.substring(13);
    }

    
    public static String buildPtzCmd(int leftRight, int upDown, int inOut, int moveSpeed, int zoomSpeed) {
        int cmdCode = 0;
        if (leftRight == 2) cmdCode |= 0x01;       // 右
        else if (leftRight == 1) cmdCode |= 0x02;  // 左
        if (upDown == 2) cmdCode |= 0x04;          // 下
        else if (upDown == 1) cmdCode |= 0x08;     // 上
        if (inOut == 2) cmdCode |= 0x10;           // 放大
        else if (inOut == 1) cmdCode |= 0x20;      // 缩小

        // zoom 速度：byte6 高 4 位有效(0x00-0xF0)，低 4 位固定 0
        int zoomByte = (zoomSpeed & 0x0F) << 4;

        StringBuilder b = new StringBuilder("A50F01");
        b.append(String.format("%02X", cmdCode));
        b.append(String.format("%02X", moveSpeed));
        b.append(String.format("%02X", moveSpeed));
        b.append(String.format("%02X", zoomByte));

        // 校验和：字节 0-6 累加取低 8 位
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + moveSpeed + moveSpeed + zoomByte) & 0xFF;
        b.append(String.format("%02X", checkCode));
        return b.toString();
    }

    public static String buildPtzSetPreset(int presetId) {
        return buildPtzPresetCmd(0x81, presetId);
    }

    public static String buildPtzCallPreset(int presetId) {
        return buildPtzPresetCmd(0x82, presetId);
    }

    public static String buildPtzDeletePreset(int presetId) {
        return buildPtzPresetCmd(0x83, presetId);
    }

    // 0x81/82/83 = 设置/调用/删除；A5 0F 01 cmd 00 preset 00 checksum。
    private static String buildPtzPresetCmd(int cmdCode, int presetId) {
        int preset = presetId & 0xFF;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + 0x00 + preset + 0x00) & 0xFF;
        return String.format("A50F01%02X00%02X00%02X", cmdCode, preset, checkCode);
    }

    // 方向 0停/1近/2远；速度 0-15，放在最后一个字节的高 4 位。
    public static String buildPtzFocusCmd(int direction, int focusSpeed) {
        int cmdCode = 0;
        if (direction == 1) cmdCode |= 0x40;    // 聚焦近
        else if (direction == 2) cmdCode |= 0x80;  // 聚焦远

        int focusByte = (focusSpeed & 0x0F) << 4;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + 0x00 + 0x00 + focusByte) & 0xFF;
        return String.format("A50F01%02X0000%02X%02X", cmdCode, focusByte, checkCode);
    }


    // 方向 0停/1大/2小；速度 0-15，放在最后一个字节的低 4 位。
    public static String buildPtzIrisCmd(int direction, int irisSpeed) {
        int cmdCode2 = 0;
        if (direction == 1) cmdCode2 |= 0x01;   // 光圈大
        else if (direction == 2) cmdCode2 |= 0x02;  // 光圈小

        int irisByte = irisSpeed & 0x0F;
        int checkCode = (0xA5 + 0x0F + 0x01 + 0x00 + cmdCode2 + 0x00 + irisByte) & 0xFF;
        return String.format("A50F0100%02X00%02X%02X", cmdCode2, irisByte, checkCode);
    }

    public static String buildPtzWiperCmd(boolean on) {
        int cmd = on ? 0x0B : 0x0C;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmd + 0x00 + 0x00 + 0x00) & 0xFF;
        return String.format("A50F01%02X000000%02X", cmd, checkCode);
    }

    // 0x84 不携带停留时间，得另发 0x87。
    public static String buildPtzCruiseAddPoint(int cruiseId, int presetId, int staySeconds) {
        return buildPtzCruiseCmd(0x84, cruiseId & 0xFF, presetId & 0xFF, 0x00);
    }

    public static String buildPtzCruiseRemovePoint(int cruiseId, int presetId) {
        return buildPtzCruiseCmd(0x85, cruiseId & 0xFF, presetId & 0xFF, 0x00);
    }

    public static String buildPtzCruiseSetSpeed(int cruiseId, int speed) {
        return buildPtzCruiseCmd(0x86, cruiseId & 0xFF, speed & 0xFF, 0x00);
    }

    public static String buildPtzCruiseSetStay(int cruiseId, int staySeconds) {
        return buildPtzCruiseCmd(0x87, cruiseId & 0xFF, staySeconds & 0xFF, 0x00);
    }

    public static String buildPtzCruiseStart(int cruiseId) {
        return buildPtzCruiseCmd(0x88, cruiseId & 0xFF, 0x00, 0x00);
    }

    public static String buildPtzCruiseStop(int cruiseId) {
        return buildPtzCruiseCmd(0x89, cruiseId & 0xFF, 0x00, 0x00);
    }

    // 巡航指令统一是 A5 0F 01 cmd b3 b4 b5 checksum。
    private static String buildPtzCruiseCmd(int cmdCode, int b3, int b4, int b5) {
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + b3 + b4 + b5) & 0xFF;
        return String.format("A50F01%02X%02X%02X%02X%02X", cmdCode, b3, b4, b5, checkCode);
    }


    public record RemoteAddressInfo(String ip, int port) {
        public String hostAddress() { return ip + ":" + port; }
    }
}
