package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;

import java.util.List;

// 分组 parental=1，通道 parental=0；216 虚拟组织还得补 BusinessGroupID。
// 通道 DeviceID 优先 exposed_code，分组直接用 channelId。
public final class PlatformCatalogXml {

    private PlatformCatalogXml() {}

    // sumNum 是全部条目数，每片都填同一个；DeviceList Num 才是本片条数。
    public static String buildResponse(GbParentPlatform platform, String deviceGbId, String sn, int sumNum,
                                       List<GbDeviceChannel> items) {
        String charset = platform.getCharacterSet() == null ? "GB2312" : platform.getCharacterSet();
        int itemNum = items.size();

        StringBuilder sb = new StringBuilder(600 + itemNum * 300);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>Catalog</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceGbId).append("</DeviceID>\r\n");
        sb.append("<SumNum>").append(sumNum).append("</SumNum>\r\n");
        appendDeviceList(sb, deviceGbId, items);
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    public static void appendDeviceList(StringBuilder sb, String deviceGbId, List<GbDeviceChannel> items) {
        sb.append("<DeviceList Num=\"").append(items.size()).append("\">\r\n");
        for (GbDeviceChannel it : items) {
            appendItem(sb, it, deviceGbId);
        }
        sb.append("</DeviceList>\r\n");
    }

    private static void appendItem(StringBuilder sb, GbDeviceChannel it, String deviceGbId) {
        boolean isGroup = it.getParental() != null && it.getParental() == 1;
        String reportId;
        if (isGroup) {
            reportId = it.getChannelId();
        } else {
            reportId = it.getExposedOrChannelId();
        }

        String parentCode = resolveParentCode(it.getParentId(), deviceGbId);

        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(nullSafe(reportId)).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escapeXml(it.getName())).append("</Name>\r\n");
        if (isGroup) {
            sb.append("<Manufacturer>Solo</Manufacturer>\r\n");
            sb.append("<Model>Group</Model>\r\n");
            sb.append("<Owner>Solo</Owner>\r\n");
        } else {
            appendTag(sb, "Manufacturer", it.getManufacturer());
            appendTag(sb, "Model", it.getModel());
            appendTag(sb, "Owner", it.getOwner());
        }
        if (isGroup) {
            sb.append("<CivilCode></CivilCode>\r\n");
            sb.append("<Address></Address>\r\n");
        } else {
            appendTag(sb, "CivilCode", it.getCivilCode());
            appendTag(sb, "Address", it.getAddress());
        }
        sb.append("<Parental>").append(isGroup ? 1 : 0).append("</Parental>\r\n");
        sb.append("<ParentID>").append(parentCode).append("</ParentID>\r\n");
        if (isGroup) {
            sb.append("<SafetyWay>0</SafetyWay>\r\n");
            sb.append("<RegisterWay>1</RegisterWay>\r\n");
            sb.append("<Secrecy>0</Secrecy>\r\n");
            sb.append("<Status>ON</Status>\r\n");
            int type = typeCode(it.getChannelId());
            if (type == 216) {
                // solo 没单独存 BusinessGroupID，先拿本平台编号顶上
                sb.append("<BusinessGroupID>").append(deviceGbId).append("</BusinessGroupID>\r\n");
            }
        } else {
            appendTag(sb, "SafetyWay", it.getSafetyWay());
            appendTag(sb, "RegisterWay", it.getRegisterWay() == null ? 1 : it.getRegisterWay());
            appendTag(sb, "Secrecy", it.getSecrecy() == null ? 0 : it.getSecrecy());
            appendTag(sb, "IPAddress", it.getIpAddress());
            appendTag(sb, "Port", it.getPort());
            appendTag(sb, "Password", it.getPassword());
            appendTag(sb, "PTZType", it.getPtzType() == null ? 0 : it.getPtzType());
            boolean online = "ON".equalsIgnoreCase(it.getStatus());
            sb.append("<Status>").append(online ? "ON" : "OFF").append("</Status>\r\n");
            appendTag(sb, "Longitude", it.getLongitude() == null ? 0 : it.getLongitude());
            appendTag(sb, "Latitude", it.getLatitude() == null ? 0 : it.getLatitude());
        }
        sb.append("</Item>\r\n");
    }

    // parentId 为 0 或空就挂到本平台节点，其他值本身就是父节点国标码。
    private static String resolveParentCode(String parentId, String deviceGbId) {
        if (parentId == null || ROOT_PARENT.equals(parentId)) {
            return deviceGbId;
        }
        return parentId;
    }

    private static final String ROOT_PARENT = "0";

    private static int typeCode(String gbCode) {
        if (gbCode == null || gbCode.length() < 13) return 0;
        try {
            return Integer.parseInt(gbCode.substring(10, 13));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void appendTag(StringBuilder sb, String name, Object value) {
        sb.append('<').append(name).append('>');
        if (value != null) {
            sb.append(value instanceof String s ? escapeXml(s) : value.toString());
        }
        sb.append("</").append(name).append(">\r\n");
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
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
