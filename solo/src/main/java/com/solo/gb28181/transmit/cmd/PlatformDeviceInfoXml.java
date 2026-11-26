package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;

// MaxCamera/MaxAlarm 现在没有持久化字段，先按 0 回。
public final class PlatformDeviceInfoXml {

    private static final String RESULT_OK = "OK";
    private static final String RESULT_ERROR = "ERROR";

    private PlatformDeviceInfoXml() {}

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
        appendTag(sb, "Manufacturer", device.getManufacturer());
        appendTag(sb, "Model", device.getModel());
        appendTag(sb, "Firmware", device.getFirmware());
        sb.append("<MaxCamera>0</MaxCamera>\r\n");
        sb.append("<MaxAlarm>0</MaxAlarm>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

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
