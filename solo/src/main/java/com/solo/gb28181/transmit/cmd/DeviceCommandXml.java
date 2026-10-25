package com.solo.gb28181.transmit.cmd;

import org.dom4j.Element;

public final class DeviceCommandXml {

    private DeviceCommandXml() {
    }

    /**
     * 构造一个 Query 报文
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
     * @param sn        查询会话编号（响应分片回来时按 SN 关联）
     * @param channelId 通道国标 ID（不是设备 ID — RecordInfo 是按通道查询）
     * @param startTime ISO-8601 起始时间，
     * @param endTime   ISO-8601 结束时间
     * @param charset   设备字符集
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

    // 拉框放大/缩小，zoomIn=true 是放大。坐标都是播放画面像素值，前端画框给的
    public static String buildDragZoomControl(int sn, String channelId, boolean zoomIn,
                                              int length, int width, int midX, int midY,
                                              int lengthX, int lengthY, String charset) {
        String tag = zoomIn ? "DragZoomIn" : "DragZoomOut";
        StringBuilder sb = new StringBuilder(380);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Control>\r\n");
        sb.append("<CmdType>DeviceControl</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(channelId).append("</DeviceID>\r\n");
        sb.append('<').append(tag).append(">\r\n");
        sb.append("<Length>").append(length).append("</Length>\r\n");
        sb.append("<Width>").append(width).append("</Width>\r\n");
        sb.append("<MidPointX>").append(midX).append("</MidPointX>\r\n");
        sb.append("<MidPointY>").append(midY).append("</MidPointY>\r\n");
        sb.append("<LengthX>").append(lengthX).append("</LengthX>\r\n");
        sb.append("<LengthY>").append(lengthY).append("</LengthY>\r\n");
        sb.append("</").append(tag).append(">\r\n");
        sb.append("</Control>\r\n");
        return sb.toString();
    }

    // 看守位。关闭只发 Enabled=0；开启才带归位时间和预置位编号
    public static String buildHomePositionControl(int sn, String channelId, boolean enabled,
                                                  Integer resetTime, Integer presetIndex, String charset) {
        StringBuilder sb = new StringBuilder(300);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Control>\r\n");
        sb.append("<CmdType>DeviceControl</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(channelId).append("</DeviceID>\r\n");
        sb.append("<HomePosition>\r\n");
        sb.append("<Enabled>").append(enabled ? 1 : 0).append("</Enabled>\r\n");
        if (enabled) {
            sb.append("<ResetTime>").append(resetTime == null ? 0 : resetTime).append("</ResetTime>\r\n");
            sb.append("<PresetIndex>").append(presetIndex == null ? 0 : presetIndex).append("</PresetIndex>\r\n");
        }
        sb.append("</HomePosition>\r\n");
        sb.append("</Control>\r\n");
        return sb.toString();
    }

    // 布防/撤防，guard=true 是布防
    public static String buildGuardControl(int sn, String deviceId, boolean guard, String charset) {
        return buildSimpleControl(sn, deviceId, "GuardCmd", guard ? "SetGuard" : "ResetGuard", charset);
    }

    // 报警复位，method/type 一般不用填，设备有的话按方式和类型复位
    public static String buildAlarmControl(int sn, String deviceId, String method, String type, String charset) {
        StringBuilder sb = new StringBuilder(280);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Control>\r\n");
        sb.append("<CmdType>DeviceControl</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<AlarmCmd>ResetAlarm</AlarmCmd>\r\n");
        boolean hasInfo = (method != null && !method.isBlank()) || (type != null && !type.isBlank());
        if (hasInfo) {
            sb.append("<Info>\r\n");
            if (method != null && !method.isBlank()) {
                sb.append("<AlarmMethod>").append(method).append("</AlarmMethod>\r\n");
            }
            if (type != null && !type.isBlank()) {
                sb.append("<AlarmType>").append(type).append("</AlarmType>\r\n");
            }
            sb.append("</Info>\r\n");
        }
        sb.append("</Control>\r\n");
        return sb.toString();
    }

    // 设备本地录像开关，record=true 开始录
    public static String buildRecordControl(int sn, String channelId, boolean record, String charset) {
        return buildSimpleControl(sn, channelId, "RecordCmd", record ? "Record" : "StopRecord", charset);
    }

    // 让设备立刻吐一个 IDR 帧，直播卡花屏的时候点一下
    public static String buildIFrameControl(int sn, String channelId, String charset) {
        return buildSimpleControl(sn, channelId, "IFameCmd", "Send", charset);
    }

    // 远程重启设备，内容固定就是 Boot
    public static String buildTeleBootControl(int sn, String deviceId, String charset) {
        return buildSimpleControl(sn, deviceId, "TeleBoot", "Boot", charset);
    }

    // 上面几个报文长得都一个样，提一个公共的
    private static String buildSimpleControl(int sn, String deviceId, String tag, String value, String charset) {
        StringBuilder sb = new StringBuilder(240);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Control>\r\n");
        sb.append("<CmdType>DeviceControl</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append('<').append(tag).append('>').append(value).append("</").append(tag).append(">\r\n");
        sb.append("</Control>\r\n");
        return sb.toString();
    }

    /**
     * 级联透传用：拷贝上级的 Control 报文，只把 SN 换成本平台序号、
     * DeviceID 改成设备侧真实通道码（上级传的可能是撞码暴露码），命令体原样保留。
     */
    public static String buildPassthroughControl(Element upstreamRoot, int sn, String channelId, String charset) {
        Element root = upstreamRoot.createCopy();
        setChildText(root, "SN", String.valueOf(sn));
        setChildText(root, "DeviceID", channelId);
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append(root.asXML());
        return sb.toString();
    }

    /**
     * 直接子元素存在就改文本，不存在就补一个
     */
    private static void setChildText(Element root, String name, String value) {
        Element child = root.element(name);
        if (child == null) {
            root.addElement(name).setText(value);
        } else {
            child.setText(value);
        }
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
