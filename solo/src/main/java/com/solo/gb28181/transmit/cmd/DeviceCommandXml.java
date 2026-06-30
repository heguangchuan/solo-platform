package com.solo.gb28181.transmit.cmd;

/**
 * GB28181 MESSAGE 报文体（XML）构造
 */
public final class DeviceCommandXml {

    private DeviceCommandXml() {}

    /**
     * 构造一个 Query 报文
     * <p>报文示例：</p>
     * <pre>
     * &lt;?xml version="1.0" encoding="GB2312"?&gt;
     * &lt;Query&gt;
     *   &lt;CmdType&gt;Catalog&lt;/CmdType&gt;
     *   &lt;SN&gt;123456&lt;/SN&gt;
     *   &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
     * &lt;/Query&gt;
     * </pre>
     */
    public static String buildQuery(String cmdType, int sn, String deviceId, String charset) {
        StringBuilder sb = new StringBuilder(220);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Query>\r\n");
        sb.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("</Query>\r\n");
        return sb.toString();
    }

    /**
     * 构造一个 RecordInfo 录像查询 Query 报文
     *
     * <p>报文示例：</p>
     * <pre>
     * &lt;?xml version="1.0" encoding="GB2312"?&gt;
     * &lt;Query&gt;
     *   &lt;CmdType&gt;RecordInfo&lt;/CmdType&gt;
     *   &lt;SN&gt;567890&lt;/SN&gt;
     *   &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
     *   &lt;StartTime&gt;2026-06-30T00:00:00&lt;/StartTime&gt;
     *   &lt;EndTime&gt;2026-06-30T23:59:59&lt;/EndTime&gt;
     *   &lt;Secrecy&gt;0&lt;/Secrecy&gt;
     *   &lt;Type&gt;all&lt;/Type&gt;
     * &lt;/Query&gt;
     * </pre>
     *
     * @param sn         查询会话编号（响应分片回来时按 SN 关联）
     * @param channelId  通道国标 ID（不是设备 ID — RecordInfo 是按通道查询）
     * @param startTime  ISO-8601 起始时间，例 {@code 2026-06-30T00:00:00}
     * @param endTime    ISO-8601 结束时间
     * @param charset    设备字符集（一般 GB2312）
     */
    public static String buildRecordInfoQuery(int sn, String channelId, String startTime, String endTime, String charset) {
        StringBuilder sb = new StringBuilder(320);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Query>\r\n");
        sb.append("<CmdType>RecordInfo</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(channelId).append("</DeviceID>\r\n");
        sb.append("<StartTime>").append(startTime).append("</StartTime>\r\n");
        sb.append("<EndTime>").append(endTime).append("</EndTime>\r\n");
        // Secrecy=0 不限保密属性；Type=all 兼容大华 NVR（强制要求 Type 节点）
        sb.append("<Secrecy>0</Secrecy>\r\n");
        sb.append("<Type>all</Type>\r\n");
        sb.append("</Query>\r\n");
        return sb.toString();
    }
}
