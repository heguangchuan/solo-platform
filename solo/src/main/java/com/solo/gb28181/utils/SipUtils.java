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

/**
 * SIP 报文字段提取与 GB28181 协议工具集
 */
@Slf4j
@UtilityClass
public class SipUtils {

    // ---------------- 地址 / 用户名 ----------------

    /** 从 From 头取 SIP 用户名（GB28181 里通常是设备 ID 或平台 ID） */
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

    /**
     * 从请求获取设备真实地址
     * <p>NAT 场景：From 里的 IP 可能是设备内网 IP，必须取 Via.received/rport 或 socket 源端。</p>
     *
     * @param request                          请求
     * @param useSourceIpAsRemoteAddress       true=直接取 UDP/TCP 收包源；false=优先取 Via 头
     */
    public static RemoteAddressInfo getRemoteAddress(SIPRequest request, boolean useSourceIpAsRemoteAddress) {
        String ip;
        int port;
        if (useSourceIpAsRemoteAddress) {
            ip = request.getPeerPacketSourceAddress().getHostAddress();
            port = request.getPeerPacketSourcePort();
        } else {
            ViaHeader via = request.getTopmostViaHeader();
            ip = via.getReceived();
            port = via.getRPort();
            if (ip == null || ip.isBlank() || port == -1) {
                ip = request.getPeerPacketSourceAddress().getHostAddress();
                port = request.getPeerPacketSourcePort();
            }
        }
        return new RemoteAddressInfo(ip, port);
    }

    /** 来自请求顶层 Via 头的传输协议（UDP/TCP） */
    public static String getTransport(Request request) {
        if (request instanceof SIPRequest r) {
            ViaHeader via = r.getTopmostViaHeader();
            return via == null ? "UDP" : via.getTransport();
        }
        return "UDP";
    }

    // ---------------- Subject / SSRC ----------------

    /**
     * 从 Subject 头解析国标点播/回放语义的 channelId
     * <p>Subject 格式：{@code <ChannelID>:<SSRC>,<RemoteID>:<SeqNum>}</p>
     *
     * @return 长度为 2 的数组：[0]=channelId，[1]=remoteId（可能为 null）
     */
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

    // ---------------- 标识生成 ----------------

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

    // ---------------- GB 编码 ----------------

    /** 取设备 ID 的设备类型码（11-13 位） */
    public static int getTypeCodeFromGbCode(String deviceId) {
        if (deviceId == null || deviceId.length() < 13) return 0;
        try {
            return Integer.parseInt(deviceId.substring(10, 13));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 判断 GB 编码是否是前端设备（摄像机/IPC） */
    public static boolean isFrontEnd(String deviceId) {
        int type = getTypeCodeFromGbCode(deviceId);
        return type > 130 && type < 199;
    }

    /**
     * 将任意 20 位国标码的类型段（第 11-13 位）替换为 215（业务分组），
     * 行政区划+行业段（前 10 位）和序号段（后 7 位）保留不变。
     * <p>用于把下级平台的 200 系国标 ID 转成合规的业务分组 ID，
     * 作为自动创建的平台根目录的 groupId，避免 validateGroupId 校验失败。</p>
     */
    public static String toBusinessGroupId(String gbCode) {
        if (gbCode == null || gbCode.length() != 20) return gbCode;
        return gbCode.substring(0, 10) + "215" + gbCode.substring(13);
    }

    // ---------------- PTZ 控制码（GB/T 28181-2016 附录 A） ----------------

    /**
     * 国标 PTZ 控制码计算
     *
     * @param leftRight 左移右移 0=停 1=左 2=右
     * @param upDown    上移下移 0=停 1=上 2=下
     * @param inOut     缩放    0=停 1=缩小 2=放大
     * @param moveSpeed 移动速度 (0-255)
     * @param zoomSpeed 缩放速度 (0-15)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
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

    /**
     * 调用预置位 PTZ 命令编码
     * <p>国标规定：调用预置位时，Pan/Tilt/Zoom 全为 0，预置位编号放在第 5 字节</p>
     *
     * @param presetId 预置位编号 (1-255)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
    public static String buildPtzCallPreset(int presetId) {
        // 字节 1: 所有方向停止 → 0x00
        // 字节 2: 所有缩放停止 → 0x00
        // 字节 3: Pan 速度 → 0x00
        // 字节 4: Tilt 速度 → 0x00
        // 字节 5: 预置位编号
        // 字节 6: 变焦速度低四位 → 0x00
        // 字节 7: 校验和
        int b1 = 0;
        int b2 = 0;
        int b3 = 0;
        int b4 = 0;
        int b5 = presetId & 0xFF;
        int b6 = 0;

        int checkCode = (0xA5 + 0x0F + 0x01 + b1 + b2 + b3 + b4 + b5 + b6) & 0xFF;

        return String.format("A50F01%02X%02X%02X%02X%02X%02X", b1, b2, b3, b4, b5, b6, checkCode);
    }
    // ---------------- 内嵌类 ----------------

    /** 设备远端地址（IP+Port） */
    public record RemoteAddressInfo(String ip, int port) {
        public String hostAddress() { return ip + ":" + port; }
    }
}
