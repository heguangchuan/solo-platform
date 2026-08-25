package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.event.alarm.DeviceAlarm;
import com.solo.gb28181.event.position.DevicePosition;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 级联 NOTIFY 报文体（XML）构造（ADR-030）。
 *
 * <p>本平台作为下级向上级推送订阅事件时使用。NOTIFY body 是 MANSCDP+xml 格式，
 * 形如：</p>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB2312"?&gt;
 * &lt;Notify&gt;
 *   &lt;CmdType&gt;Catalog|MobilePosition|Alarm&lt;/CmdType&gt;
 *   &lt;SN&gt;...&lt;/SN&gt;
 *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
 *   ... 事件字段 ...
 * &lt;/Notify&gt;
 * </pre>
 *
 * <p>与 {@link PlatformCatalogXml} 的 Response body 区别：Response 是被点播时回执，
 * NOTIFY 是主动推送；结构、字段、字集都与 Response 对齐以保持跨厂商兼容。</p>
 */
public final class PlatformNotifyXml {

    private PlatformNotifyXml() {}

    /** ISO8601 时间格式（无时区后缀，GB/T 28181 默认格式） */
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 构造单条 Catalog NOTIFY body——推送设备状态变化（在线/离线） */
    public static String buildCatalogItem(GbParentPlatform platform, String parentDeviceGbId,
                                          GbDevice device, String sn) {
        String charset = charset(platform);
        boolean online = device.getStatus() != null && device.getStatus() == 1;
        StringBuilder sb = new StringBuilder(512);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Notify>\r\n");
        sb.append("<CmdType>Catalog</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(device.getDeviceId()).append("</DeviceID>\r\n");
        sb.append("<SumNum>1</SumNum>\r\n");
        sb.append("<DeviceList Num=\"1\">\r\n");
        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(device.getDeviceId()).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escape(device.getName())).append("</Name>\r\n");
        sb.append("<Manufacturer>").append(escape(device.getManufacturer())).append("</Manufacturer>\r\n");
        sb.append("<Model>").append(escape(device.getModel())).append("</Model>\r\n");
        // Owner / CivilCode / Address：DeviceInfo 响应回填，存于 GbDevice；DeviceInfo 还没到
        // 设备级 NOTIFY 时为空是正常的（设备首次注册后 SIP 异步发 DeviceInfo Query）。
        sb.append("<Owner>").append(escape(device.getOwner())).append("</Owner>\r\n");
        sb.append("<CivilCode>").append(escape(device.getCivilCode())).append("</CivilCode>\r\n");
        sb.append("<Address>").append(escape(device.getAddress())).append("</Address>\r\n");
        sb.append("<Parental>0</Parental>\r\n");
        sb.append("<ParentID>").append(nullSafe(parentDeviceGbId)).append("</ParentID>\r\n");
        sb.append("<SafetyWay>0</SafetyWay>\r\n");
        // RegisterWay 固定 1（标准 SIP REGISTER 注册；GbDevice 无此字段）
        sb.append("<RegisterWay>1</RegisterWay>\r\n");
        sb.append("<Secrecy>0</Secrecy>\r\n");
        sb.append("<Status>").append(online ? "ON" : "OFF").append("</Status>\r\n");
        sb.append("</Item>\r\n");
        sb.append("</DeviceList>\r\n");
        sb.append("</Notify>\r\n");
        return sb.toString();
    }

    /** 构造多通道 Catalog NOTIFY body——推送通道目录变更（新增/删除/更新） */
    public static String buildCatalogList(GbParentPlatform platform, String parentDeviceGbId,
                                          String deviceId, List<GbDeviceChannel> channels, String sn) {
        String charset = charset(platform);
        StringBuilder sb = new StringBuilder(512 + channels.size() * 256);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Notify>\r\n");
        sb.append("<CmdType>Catalog</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("<SumNum>").append(channels.size()).append("</SumNum>\r\n");
        sb.append("<DeviceList Num=\"").append(channels.size()).append("\">\r\n");
        for (GbDeviceChannel c : channels) {
            appendChannelItem(sb, c, parentDeviceGbId);
        }
        sb.append("</DeviceList>\r\n");
        sb.append("</Notify>\r\n");
        return sb.toString();
    }

    private static void appendChannelItem(StringBuilder sb, GbDeviceChannel c, String parentDeviceGbId) {
        boolean online = "ON".equalsIgnoreCase(c.getStatus());
        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(c.getExposedOrChannelId()).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escape(c.getName())).append("</Name>\r\n");
        sb.append("<Manufacturer>").append(escape(c.getManufacturer())).append("</Manufacturer>\r\n");
        sb.append("<Model>").append(escape(c.getModel())).append("</Model>\r\n");
        sb.append("<Owner>").append(escape(c.getOwner())).append("</Owner>\r\n");
        sb.append("<CivilCode>").append(escape(c.getCivilCode())).append("</CivilCode>\r\n");
        sb.append("<Address>").append(escape(c.getAddress())).append("</Address>\r\n");
        sb.append("<Parental>0</Parental>\r\n");
        String parentId = c.getParentId();
        sb.append("<ParentID>").append((parentId == null || parentId.isEmpty() || "0".equals(parentId)) ? nullSafe(parentDeviceGbId) : parentId).append("</ParentID>\r\n");
        sb.append("<SafetyWay>0</SafetyWay>\r\n");
        sb.append("<RegisterWay>").append(c.getRegisterWay() == null ? 1 : c.getRegisterWay()).append("</RegisterWay>\r\n");
        sb.append("<Secrecy>0</Secrecy>\r\n");
        sb.append("<Status>").append(online ? "ON" : "OFF").append("</Status>\r\n");
        sb.append("</Item>\r\n");
    }

    /** 构造 MobilePosition NOTIFY body——设备移动位置变化 */
    public static String buildMobilePosition(GbParentPlatform platform, DevicePosition pos, String sn) {
        String charset = charset(platform);
        StringBuilder sb = new StringBuilder(384);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Notify>\r\n");
        sb.append("<CmdType>MobilePosition</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(nullSafe(pos.getDeviceId())).append("</DeviceID>\r\n");
        if (pos.getTime() != null) {
            sb.append("<Time>").append(pos.getTime().format(ISO_FMT)).append("</Time>\r\n");
        }
        if (pos.getLongitude() != null) {
            sb.append("<Longitude>").append(pos.getLongitude()).append("</Longitude>\r\n");
        }
        if (pos.getLatitude() != null) {
            sb.append("<Latitude>").append(pos.getLatitude()).append("</Latitude>\r\n");
        }
        if (pos.getSpeed() != null) sb.append("<Speed>").append(pos.getSpeed()).append("</Speed>\r\n");
        if (pos.getDirection() != null) sb.append("<Direction>").append(pos.getDirection()).append("</Direction>\r\n");
        if (pos.getAltitude() != null) sb.append("<Altitude>").append(pos.getAltitude()).append("</Altitude>\r\n");
        sb.append("</Notify>\r\n");
        return sb.toString();
    }

    /** 构造 Alarm NOTIFY body——设备报警 */
    public static String buildAlarm(GbParentPlatform platform, DeviceAlarm alarm, String sn) {
        String charset = charset(platform);
        StringBuilder sb = new StringBuilder(384);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Notify>\r\n");
        sb.append("<CmdType>Alarm</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(nullSafe(alarm.getChannelId() != null ? alarm.getChannelId() : alarm.getDeviceId())).append("</DeviceID>\r\n");
        if (alarm.getChannelId() != null && alarm.getDeviceId() != null) {
            // 国标报警 DeviceID 字段指通道；带通道时同时报设备 ID 方便上级溯源
            sb.append("<DeviceID2>").append(nullSafe(alarm.getDeviceId())).append("</DeviceID2>\r\n");
        }
        if (alarm.getAlarmPriority() != null) sb.append("<AlarmPriority>").append(alarm.getAlarmPriority()).append("</AlarmPriority>\r\n");
        if (alarm.getAlarmMethod() != null) sb.append("<AlarmMethod>").append(alarm.getAlarmMethod()).append("</AlarmMethod>\r\n");
        if (alarm.getAlarmTime() != null) sb.append("<AlarmTime>").append(alarm.getAlarmTime().format(ISO_FMT)).append("</AlarmTime>\r\n");
        if (alarm.getAlarmDescription() != null) sb.append("<AlarmDescription>").append(escape(alarm.getAlarmDescription())).append("</AlarmDescription>\r\n");
        if (alarm.getAlarmType() != null) sb.append("<AlarmType>").append(escape(alarm.getAlarmType())).append("</AlarmType>\r\n");
        if (alarm.getLongitude() != null) sb.append("<Longitude>").append(alarm.getLongitude()).append("</Longitude>\r\n");
        if (alarm.getLatitude() != null) sb.append("<Latitude>").append(alarm.getLatitude()).append("</Latitude>\r\n");
        sb.append("</Notify>\r\n");
        return sb.toString();
    }

    private static String charset(GbParentPlatform p) {
        return p.getCharacterSet() == null || p.getCharacterSet().isBlank() ? "GB2312" : p.getCharacterSet();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /** 最小 XML 转义——5 个破坏 XML 结构的字符 */
    private static String escape(String s) {
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
