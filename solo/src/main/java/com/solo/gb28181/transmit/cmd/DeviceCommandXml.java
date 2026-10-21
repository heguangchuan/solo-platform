package com.solo.gb28181.transmit.cmd;

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

    /**
     * 构造一个 PTZ 云台控制 Control 报文（DeviceControl + PTZCmd）
     *
     * <p>报文示例：</p>
     * <pre>
     * &lt;?xml version="1.0" encoding="GB2312"?&gt;
     * &lt;Control&gt;
     *   &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
     *   &lt;SN&gt;123456&lt;/SN&gt;
     *   &lt;DeviceID&gt;34020000001310000001&lt;/DeviceID&gt;
     *   &lt;PTZCmd&gt;A50F01080064006416&lt;/PTZCmd&gt;
     *   &lt;Info&gt;
     *     &lt;ControlPriority&gt;5&lt;/ControlPriority&gt;
     *   &lt;/Info&gt;
     * &lt;/Control&gt;
     * </pre>
     *
     * @param sn        命令序号
     * @param channelId 通道国标 ID（PTZ 指令发给通道，不是设备）
     * @param ptzCmd    8 字节 PTZ 控制码十六进制字符串（由 {@link com.solo.gb28181.utils.SipUtils#buildPtzCmd} 生成）
     * @param charset   设备字符集
     */
    public static String buildPtzControl(int sn, String channelId, String ptzCmd, String charset) {
        StringBuilder sb = new StringBuilder(320);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Control>\r\n");
        sb.append("<CmdType>DeviceControl</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(channelId).append("</DeviceID>\r\n");
        sb.append("<PTZCmd>").append(ptzCmd).append("</PTZCmd>\r\n");
        sb.append("<Info>\r\n");
        sb.append("<ControlPriority>5</ControlPriority>\r\n");
        sb.append("</Info>\r\n");
        sb.append("</Control>\r\n");
        return sb.toString();
    }

    /**
     * 构造预置位查询 Query 报文（Query/Preset）
     *
     * <p>设备返回 Response/Preset，其中 PresetList/Item 列出设备端所有预置位。</p>
     *
     * @param sn        命令序号（响应回来时按 SN 关联）
     * @param channelId 通道国标 ID（预置位按通道查询）
     * @param charset   设备字符集
     */
    public static String buildPresetQuery(int sn, String channelId, String charset) {
        StringBuilder sb = new StringBuilder(260);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Query>\r\n");
        sb.append("<CmdType>Preset</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(channelId).append("</DeviceID>\r\n");
        sb.append("</Query>\r\n");
        return sb.toString();
    }

    /**
     * 构造 SUBSCRIBE XML 报文（Catalog / MobilePosition / Alarm 共用）
     */
    public static String buildSubscribe(String cmdType, int sn, String deviceId, int expires, String charset) {
        StringBuilder sb = new StringBuilder(260);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Subscribe>\r\n");
        sb.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<Expires>").append(Math.max(0, expires)).append("</Expires>\r\n");
        sb.append("</Subscribe>\r\n");
        return sb.toString();
    }
}
