package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;

/**
 * <p>本平台作为下级，收到上级 {@code <Query><CmdType>DeviceInfo</CmdType>...</Query>}
 * 后回包使用。与 {@link PlatformCatalogXml} 同构：构造侧只管拼 XML，发包走
 * {@link PlatformCommander}。</p>
 *
 * <p>字段对齐 GB/T 28181-2016 9.5.4 DeviceInfo Response：</p>
 * <ul>
 *   <li>{@code Result=OK}：设备登记在册，下发完整厂商/型号/固件信息</li>
 *   <li>{@code Result=ERROR}：设备未登记，告知上级"我这儿没这个设备"</li>
 *   <li>{@code MaxCamera}：本平台当前未持久化通道数（Catalog 同步时回填），暂固定 0；
 *       后续在 {@code gb_device} 加 {@code max_camera} 列后回填</li>
 * </ul>
 */
public final class PlatformDeviceInfoXml {

    private static final String RESULT_OK = "OK";
    private static final String RESULT_ERROR = "ERROR";

    private PlatformDeviceInfoXml() {}

    /**
     * 构造 DeviceInfo 正常响应 XML（Result=OK）。
     *
     * @param platform 上级平台配置；从 p 取字符集
     * @param deviceId 上级请求的 DeviceID（透传回去）
     * @param sn       原请求的 SN，透传回去
     * @param device   本平台登记的设备实体（不可为 null，由调用方保证）
     */
    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                       GbDevice device) {
        String charset = charsetOf(platform);
        StringBuilder sb = new StringBuilder(320);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>DeviceInfo</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<Result>").append(RESULT_OK).append("</Result>\r\n");
        appendTag(sb, "DeviceName", device.getName());
        // 厂商/型号/固件：来自设备注册时上 ReportInfo 报文（DeviceInfoResponseMessageHandler 回填）
        appendTag(sb, "Manufacturer", device.getManufacturer());
        appendTag(sb, "Model", device.getModel());
        appendTag(sb, "Firmware", device.getFirmware());
        sb.append("<MaxCamera>0</MaxCamera>\r\n");
        sb.append("<MaxAlarm>0</MaxAlarm>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    /**
     * 构造 DeviceInfo 错误响应 XML（Result=ERROR）。
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
        sb.append("<CmdType>DeviceInfo</CmdType>\r\n");
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

    private static void appendTag(StringBuilder sb, String name, Object value) {
        sb.append('<').append(name).append('>');
        if (value != null) {
            String s = value.toString();
            sb.append(escapeXml(s));
        }
        sb.append("</").append(name).append(">\r\n");
    }

    /** 最小 XML 转义：只处理会破坏 XML 结构的 5 个字符（与 PlatformCatalogXml 同款） */
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
