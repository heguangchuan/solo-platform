package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.response.RecordItemResponse;

import java.util.List;

/**
 * <p>本平台作为下级，收到上级 {@code <Query><CmdType>RecordInfo</CmdType>...</Query>}
 * 后回包使用。数据源：本平台不存录像，收到查询后主动 {@code SipCommander.queryRecordInfo}
 * 转发给下属设备，把设备响应按相同字段集转回上级（SN 改为外层 SN，详见
 * {@link com.solo.gb28181.support.UpperRecordQueryHolder}）。</p>
 *
 * <p>字段对齐 GB/T 28181-2016 9.5.5 RecordInfo Response：</p>
 * <ul>
 *   <li>{@code SumNum}：本片/本次录像段总条数</li>
 *   <li>{@code RecordList Num=N}：Item 列表</li>
 *   <li>空响应：{@code SumNum=0 RecordList Num=0}，不省略 RecordList 节点（部分上级 XML 解析器
 *       期望节点存在）</li>
 * </ul>
 */
public final class PlatformRecordInfoXml {

    private PlatformRecordInfoXml() {}

    /**
     * 构造 RecordInfo Response XML。
     *
     * @param platform 上级平台配置；从 p 取字符集
     * @param deviceId 上级请求的 DeviceID（透传回去；按 GB 协议此处通常是 channelId）
     * @param sn       原请求的 SN，透传回去
     * @param items    录像段列表（按设备返回的顺序，调用方已排序）
     */
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

    /** 最小 XML 转义：与 PlatformDeviceInfoXml 同款（后续可抽 XmlBuilderUtils） */
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
