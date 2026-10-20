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
     * 设置预置位 PTZ 命令编码（告诉设备将当前位置保存到指定预置位槽）
     * <p>国标规定：设置预置位时指令码为 0x81，预置位编号放在第 6 字节</p>
     *
     * @param presetId 预置位编号 (1-255)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
    public static String buildPtzSetPreset(int presetId) {
        return buildPtzPresetCmd(0x81, presetId);
    }

    /**
     * 调用预置位 PTZ 命令编码（告诉设备转到指定预置位）
     * <p>国标规定：调用预置位时指令码为 0x82，预置位编号放在第 6 字节</p>
     *
     * @param presetId 预置位编号 (1-255)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
    public static String buildPtzCallPreset(int presetId) {
        return buildPtzPresetCmd(0x82, presetId);
    }

    /**
     * 删除预置位 PTZ 命令编码
     * <p>国标规定：删除预置位时指令码为 0x83，预置位编号放在第 6 字节</p>
     *
     * @param presetId 预置位编号 (1-255)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
    public static String buildPtzDeletePreset(int presetId) {
        return buildPtzPresetCmd(0x83, presetId);
    }

    /**
     * 预置位 PTZ 命令编码（8 字节标准格式）
     * <p>格式：A5 0F 01 [cmdCode] 00 [presetId] 00 [checksum]，预置位编号在第 6 字节</p>
     *
     * @param cmdCode  指令码：0x81=设置, 0x82=调用, 0x83=删除
     * @param presetId 预置位编号 (1-255)
     * @return 8 字节 PTZ 控制码（16 进制大写串）
     */
    private static String buildPtzPresetCmd(int cmdCode, int presetId) {
        int preset = presetId & 0xFF;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + 0x00 + preset + 0x00) & 0xFF;
        return String.format("A50F01%02X00%02X00%02X", cmdCode, preset, checkCode);
    }

    // ---------------- 聚焦控制码 ----------------

    /**
     * 聚焦控制 PTZ 命令编码
     * <p>字节3组合码1：bit6=聚焦近(0x40)，bit7=聚焦远(0x80)</p>
     * <p>字节7（第8字节）高4位=聚焦速度(0x00-0xF0)</p>
     *
     * @param direction 0=停 1=近 2=远
     * @param focusSpeed 聚焦速度 (0-15)
     * @return 8 字节 PTZ 控制码（16进制大写串）
     */
    public static String buildPtzFocusCmd(int direction, int focusSpeed) {
        int cmdCode = 0;
        if (direction == 1) cmdCode |= 0x40;    // 聚焦近
        else if (direction == 2) cmdCode |= 0x80;  // 聚焦远

        int focusByte = (focusSpeed & 0x0F) << 4;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + 0x00 + 0x00 + focusByte) & 0xFF;
        return String.format("A50F01%02X0000%02X%02X", cmdCode, focusByte, checkCode);
    }

    // ---------------- 光圈控制码 ----------------

    /**
     * 光圈控制 PTZ 命令编码
     * <p>字节4（第5字节）组合码2：bit0=光圈大(0x01)，bit1=光圈小(0x02)</p>
     * <p>字节7（第8字节）低4位=光圈速度(0x00-0x0F)</p>
     *
     * @param direction 0=停 1=大(开) 2=小(关)
     * @param irisSpeed 光圈速度 (0-15)
     * @return 8 字节 PTZ 控制码（16进制大写串）
     */
    public static String buildPtzIrisCmd(int direction, int irisSpeed) {
        int cmdCode2 = 0;
        if (direction == 1) cmdCode2 |= 0x01;   // 光圈大
        else if (direction == 2) cmdCode2 |= 0x02;  // 光圈小

        int irisByte = irisSpeed & 0x0F;
        int checkCode = (0xA5 + 0x0F + 0x01 + 0x00 + cmdCode2 + 0x00 + irisByte) & 0xFF;
        return String.format("A50F0100%02X00%02X%02X", cmdCode2, irisByte, checkCode);
    }

    // ---------------- 雨刷控制码 ----------------

    /**
     * 雨刷控制 PTZ 命令编码
     * <p>走辅助开关指令，使用扩展字节</p>
     *
     * @param on true=开 false=关
     * @return 8 字节 PTZ 控制码（16进制大写串）
     */
    public static String buildPtzWiperCmd(boolean on) {
        int cmd = on ? 0x0B : 0x0C;
        int checkCode = (0xA5 + 0x0F + 0x01 + cmd + 0x00 + 0x00 + 0x00) & 0xFF;
        return String.format("A50F01%02X000000%02X", cmd, checkCode);
    }

    // ---------------- 巡航控制码（扩展指令 0x84~0x89） ----------------

    /**
     * 添加巡航点
     * <p>指令码 0x84：byte3=cruiseId, byte4=presetId, byte5=组合码(0)；
     * 停留时间由 0x87 单独下发，不在本指令携带</p>
     */
    public static String buildPtzCruiseAddPoint(int cruiseId, int presetId, int staySeconds) {
        return buildPtzCruiseCmd(0x84, cruiseId & 0xFF, presetId & 0xFF, 0x00);
    }

    /**
     * 删除巡航点
     * <p>指令码 0x85：byte3=cruiseId, byte4=presetId</p>
     */
    public static String buildPtzCruiseRemovePoint(int cruiseId, int presetId) {
        return buildPtzCruiseCmd(0x85, cruiseId & 0xFF, presetId & 0xFF, 0x00);
    }

    /**
     * 设置巡航速度
     * <p>指令码 0x86：byte3=cruiseId, byte4=speed(1~255)</p>
     */
    public static String buildPtzCruiseSetSpeed(int cruiseId, int speed) {
        return buildPtzCruiseCmd(0x86, cruiseId & 0xFF, speed & 0xFF, 0x00);
    }

    /**
     * 设置巡航停留时间
     * <p>指令码 0x87：byte3=cruiseId, byte4=staySeconds</p>
     */
    public static String buildPtzCruiseSetStay(int cruiseId, int staySeconds) {
        return buildPtzCruiseCmd(0x87, cruiseId & 0xFF, staySeconds & 0xFF, 0x00);
    }

    /**
     * 开始巡航
     * <p>指令码 0x88：byte3=cruiseId</p>
     */
    public static String buildPtzCruiseStart(int cruiseId) {
        return buildPtzCruiseCmd(0x88, cruiseId & 0xFF, 0x00, 0x00);
    }

    /**
     * 停止巡航
     * <p>指令码 0x89：byte3=cruiseId</p>
     */
    public static String buildPtzCruiseStop(int cruiseId) {
        return buildPtzCruiseCmd(0x89, cruiseId & 0xFF, 0x00, 0x00);
    }

    /**
     * 巡航扩展指令通用构造
     * <p>格式：A5 0F 01 [cmd] [byte3] [byte4] [byte5] [checksum]</p>
     */
    private static String buildPtzCruiseCmd(int cmdCode, int b3, int b4, int b5) {
        int checkCode = (0xA5 + 0x0F + 0x01 + cmdCode + b3 + b4 + b5) & 0xFF;
        return String.format("A50F01%02X%02X%02X%02X%02X", cmdCode, b3, b4, b5, checkCode);
    }

    // ---------------- 内嵌类 ----------------

    /** 设备远端地址（IP+Port） */
    public record RemoteAddressInfo(String ip, int port) {
        public String hostAddress() { return ip + ":" + port; }
    }
}
