package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>本平台作为下级，收到上级 {@code <Query><CmdType>DeviceStatus</CmdType>...</Query>}
 * 后回包使用。与 {@link PlatformDeviceInfoXml} 同构：构造侧只管拼 XML，发包走
 * {@link PlatformCommander}。</p>
 *
 * <p>字段对齐 GB/T 28181-2016 9.6.2 DeviceStatus Response：</p>
 * <ul>
 *   <li>{@code Result=OK}：设备登记在册，下发在线状态（Online）+ 健康状态（Status）+ 设备时间</li>
 *   <li>{@code Result=ERROR}：设备未登记，告知上级"我这儿没这个设备"</li>
 *   <li>{@code Online}：1=在线→ONLINE，0=离线/2=未注册→OFFLINE</li>
 *   <li>{@code Status}：设备健康状态，在线→OK，离线→FAULT（本平台只维护在线态，无独立健康度采集）</li>
 *   <li>{@code DeviceTime}：本平台当前时间，供上级校时参考（可选字段，国标允许缺省）</li>
 * </ul>
 */
public final class PlatformDeviceStatusXml {

    private static final String RESULT_OK = "OK";
    private static final String RESULT_ERROR = "ERROR";

    private PlatformDeviceStatusXml() {}

    /** ISO8601 时间格式（无时区后缀，GB/T 28181 默认格式） */
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 构造 DeviceStatus 正常响应 XML（Result=OK）——设备维度。
     * <p>设备在线态来自 {@link GbDevice#getStatus()}（0 离线 / 1 在线 / 2 未注册）。</p>
     *
     * @param platform 上级平台配置；从 p 取字符集
     * @param deviceId 上级请求的 DeviceID（透传回去）
     * @param sn       原请求的 SN，透传回去
     * @param device   本平台登记的设备实体（不可为 null，由调用方保证）
     */
    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                        GbDevice device) {
        boolean online = device.getStatus() != null && device.getStatus() == 1;
        return build(platform, deviceId, sn, online);
    }

    /**
     * 构造 DeviceStatus 正常响应 XML（Result=OK）——通道维度。
     * <p>国标 DeviceStatus 的 Online/Status 本就是按通道的，一个 NVR 下几十个通道在线/录像态各异。
     * 通道在线态来自 {@link com.solo.gb28181.entity.GbDeviceChannel#getStatus()}
     * （{@code ON}/{@code OFF}/{@code UNREGISTERED} 字符串）。</p>
     *
     * @param platform      上级平台配置；从 p 取字符集
     * @param deviceId      上级请求的 DeviceID（即通道国标码，透传回去）
     * @param sn            原请求的 SN，透传回去
     * @param channelStatus 通道 status 字符串（{@code ON}/{@code OFF}/{@code UNREGISTERED}，可空）
     */
    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                        String channelStatus) {
        boolean online = "ON".equalsIgnoreCase(channelStatus);
        return build(platform, deviceId, sn, online);
    }

    /** 核心：Online/Status 均由 online 推导（在线→ONLINE+OK，否则→OFFLINE+FAULT）。 */
    private static String build(GbParentPlatform platform, String deviceId, String sn, boolean online) {
        String charset = charsetOf(platform);
        StringBuilder sb = new StringBuilder(320);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>DeviceStatus</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<Result>").append(RESULT_OK).append("</Result>\r\n");
        sb.append("<Online>").append(online ? "ONLINE" : "OFFLINE").append("</Online>\r\n");
        sb.append("<Status>").append(online ? "OK" : "FAULT").append("</Status>\r\n");
        sb.append("<DeviceTime>").append(LocalDateTime.now().format(ISO_FMT)).append("</DeviceTime>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    /**
     * 构造 DeviceStatus 错误响应 XML（Result=ERROR）。
     * <p>上级查了一个本平台没登记的设备时回这个，避免上级无限重试。</p>
     *
     * @param reason 错误原因（写入日志，XML 不带）
     */
    public static String buildErrorResponse(GbParentPlatform platform, String deviceId, String sn,
                                             String reason) {
        String charset = charsetOf(platform);
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>DeviceStatus</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<Result>").append(RESULT_ERROR).append("</Result>\r\n");
        sb.append("<Reason>").append(reason == null ? "" : escapeXml(reason)).append("</Reason>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    private static String charsetOf(GbParentPlatform platform) {
        return platform != null && platform.getCharacterSet() != null
                ? platform.getCharacterSet() : "GB2312";
    }

    /** 最小 XML 转义：只处理会破坏 XML 结构的 5 个字符（与 PlatformDeviceInfoXml 同款） */
    private static String escapeXml(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
