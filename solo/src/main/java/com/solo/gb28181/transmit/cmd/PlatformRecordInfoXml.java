package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.response.RecordItemResponse;

import java.util.List;

// 空结果也保留 RecordList Num=0，有些上级少了这个节点就解析失败。
public final class PlatformRecordInfoXml {

    private PlatformRecordInfoXml() {}

    public static String buildResponse(GbParentPlatform platform, String deviceId, String sn,
                                       List<RecordItemResponse> items) {
        String charset = charsetOf(platform);
        int sumNum = items == null ? 0 : items.size();

        StringBuilder sb = new StringBuilder(320 + sumNum * 300);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>RecordInfo</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<SumNum>").append(sumNum).append("</SumNum>\r\n");
        sb.append("<RecordList Num=\"").append(sumNum).append("\">\r\n");

        if (items != null) {
            for (RecordItemResponse it : items) {
                appendItem(sb, it);
            }
        }

        sb.append("</RecordList>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    private static void appendItem(StringBuilder sb, RecordItemResponse it) {
        sb.append("<Item>\r\n");
        appendTag(sb, "DeviceID", it.getChannelId());
        appendTag(sb, "Name", it.getName());
        appendTag(sb, "FilePath", it.getFilePath());
        appendTag(sb, "Address", it.getAddress());
        appendTag(sb, "StartTime", it.getStartTime());
        appendTag(sb, "EndTime", it.getEndTime());
        appendTag(sb, "Secrecy", it.getSecrecy());
        appendTag(sb, "Type", it.getType());
        appendTag(sb, "FileSize", it.getFileSize());
        appendTag(sb, "RecorderID", it.getRecorderId());
        sb.append("</Item>\r\n");
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
