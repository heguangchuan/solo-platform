package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 本平台没单独探健康度，在线就 ONLINE+OK，不在线就 OFFLINE+FAULT。
public final class PlatformDeviceStatusXml {

    private static final String RESULT_OK = "OK";
    private static final String RESULT_ERROR = "ERROR";

    private PlatformDeviceStatusXml() {}

    // 国标时间不带时区。
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                        GbDevice device) {
        boolean online = device.getStatus() != null && device.getStatus() == 1;
        return build(platform, deviceId, sn, online);
    }

    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                        String channelStatus) {
        boolean online = "ON".equalsIgnoreCase(channelStatus);
        return build(platform, deviceId, sn, online);
    }

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
