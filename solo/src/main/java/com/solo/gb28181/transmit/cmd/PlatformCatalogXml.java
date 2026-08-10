package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbParentPlatform;

import java.util.List;

/**
 * 级联 Catalog Response 报文体（XML）构造
 *
 * <p>本平台作为下级向上级回复 Catalog 查询时使用。与 {@link DeviceCommandXml} 分离：
 * 后者构造发往下级设备的短命令，本类构造回给上级的目录分片。</p>
 *
 * <p>Item 类型（按 GB/T 28181-2016）：</p>
 * <ul>
 *   <li><b>分组 Item</b>（{@link GbGroup}）：类型码取 groupId 第 11-13 位；215 业务分组仅出
 *   DeviceID/Name/ParentID，216 虚拟组织额外补 BusinessGroupID，均带 Parental=1、无 Status。</li>
 *   <li><b>通道 Item</b>（{@link GbDeviceChannel}）：完整前端设备字段集，Parental=0，
 *   Status 由 gb_device_channel.status 映射（ON/OFF）。</li>
 * </ul>
 */
public final class PlatformCatalogXml {

    private PlatformCatalogXml() {}

    /**
     * 构造一片 Catalog Response XML。
     *
     * @param platform    上级平台配置；From/To 语义由发送侧构造，本方法只用 deviceGbId 作 &lt;DeviceID&gt; +
     *                    通道 ParentID 兜底
     * @param sn          原请求的 SN，透传回去
     * @param sumNum      全量总条数（每片都填相同值）
     * @param groups      本片中的分组 Item（放在 pageChannels 之前）
     * @param channels    本片中的通道 Item
     * @param groupIdCode 通道所属 group 主键(雪花 id) -> group 20 位国标码 的映射（只包含"在共享列表内"的分组）
     */
    public static String buildResponse(GbParentPlatform platform, String sn, int sumNum,
                                       List<GbGroup> groups, List<GbDeviceChannel> channels,
                                       java.util.Map<Long, String> groupIdCode) {
        String charset = platform.getCharacterSet() == null ? "GB2312" : platform.getCharacterSet();
        int itemNum = groups.size() + channels.size();

        StringBuilder sb = new StringBuilder(600 + itemNum * 300);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Response>\r\n");
        sb.append("<CmdType>Catalog</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(platform.getDeviceGbId()).append("</DeviceID>\r\n");
        sb.append("<SumNum>").append(sumNum).append("</SumNum>\r\n");
        sb.append("<DeviceList Num=\"").append(itemNum).append("\">\r\n");

        for (GbGroup g : groups) {
            appendGroupItem(sb, g, platform, groupIdCode);
        }
        for (GbDeviceChannel ch : channels) {
            appendChannelItem(sb, ch, platform, groupIdCode);
        }

        sb.append("</DeviceList>\r\n");
        sb.append("</Response>\r\n");
        return sb.toString();
    }

    /**
     * 分组 Item：DeviceID = group.group_id（20 位国标码）；ParentID 直接取 g.getParentId()（也是国标码），
     * 父分组未指定（根节点 parentId="0"）→ 兜底到 platform.deviceGbId。
     */
    private static void appendGroupItem(StringBuilder sb, GbGroup g, GbParentPlatform platform,
                                        java.util.Map<Long, String> groupIdCode) {
        String parentCode = "0".equals(g.getParentId()) ? platform.getDeviceGbId() : g.getParentId();
        int type = typeCode(g.getGroupId());

        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(nullSafe(g.getGroupId())).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escapeXml(g.getName())).append("</Name>\r\n");
        sb.append("<Manufacturer>Solo</Manufacturer>\r\n");
        sb.append("<Model>Group</Model>\r\n");
        sb.append("<Owner>Solo</Owner>\r\n");
        sb.append("<CivilCode></CivilCode>\r\n");
        sb.append("<Address></Address>\r\n");
        sb.append("<Parental>1</Parental>\r\n");
        sb.append("<ParentID>").append(parentCode).append("</ParentID>\r\n");
        sb.append("<SafetyWay>0</SafetyWay>\r\n");
        sb.append("<RegisterWay>1</RegisterWay>\r\n");
        sb.append("<Secrecy>0</Secrecy>\r\n");
        sb.append("<Status>ON</Status>\r\n");
        if (type == 216) {
            // 216 虚拟组织需要 BusinessGroupID；solo 无独立字段，用平台 deviceGbId 兜底
            sb.append("<BusinessGroupID>").append(platform.getDeviceGbId()).append("</BusinessGroupID>\r\n");
        }
        sb.append("</Item>\r\n");
    }

    /**
     * 通道 Item：ParentID 优先取通道所属分组的 group_id 20 位码；分组未共享 / 找不到 → 兜底
     * platform.deviceGbId（挂到平台自身节点下）。
     */
    private static void appendChannelItem(StringBuilder sb, GbDeviceChannel ch, GbParentPlatform platform,
                                          java.util.Map<Long, String> groupIdCode) {
        String parentCode = groupIdCode.getOrDefault(ch.getGroupId(), platform.getDeviceGbId());
        String name = (ch.getName() != null && !ch.getName().isBlank()) ? ch.getName() : ch.getReportName();
        boolean online = "ON".equalsIgnoreCase(ch.getStatus());
        // 对上级暴露用 exposed_code（平台唯一，解决跨设备/级联撞码）；存量未分配则回退原始 channelId
        String reportId = (ch.getExposedCode() != null && !ch.getExposedCode().isBlank())
                ? ch.getExposedCode() : ch.getChannelId();

        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(nullSafe(reportId)).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escapeXml(name)).append("</Name>\r\n");
        appendTag(sb, "Manufacturer", ch.getManufacturer());
        appendTag(sb, "Model", ch.getModel());
        appendTag(sb, "Owner", ch.getOwner());
        appendTag(sb, "CivilCode", ch.getCivilCode());
        appendTag(sb, "Address", ch.getAddress());
        sb.append("<Parental>0</Parental>\r\n");
        sb.append("<ParentID>").append(parentCode).append("</ParentID>\r\n");
        appendTag(sb, "SafetyWay", ch.getSafetyWay());
        appendTag(sb, "RegisterWay", ch.getRegisterWay() == null ? 1 : ch.getRegisterWay());
        appendTag(sb, "Secrecy", ch.getSecrecy() == null ? 0 : ch.getSecrecy());
        appendTag(sb, "IPAddress", ch.getIpAddress());
        appendTag(sb, "Port", ch.getPort());
        appendTag(sb, "Password", ch.getPassword());
        appendTag(sb, "PTZType", ch.getPtzType() == null ? 0 : ch.getPtzType());
        sb.append("<Status>").append(online ? "ON" : "OFF").append("</Status>\r\n");
        appendTag(sb, "Longitude", ch.getLongitude() == null ? 0 : ch.getLongitude());
        appendTag(sb, "Latitude", ch.getLatitude() == null ? 0 : ch.getLatitude());
        sb.append("</Item>\r\n");
    }

    /** 取 20 位国标码的类型段（第 11-13 位）；非法编码返回 0 */
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

    /** 最小 XML 转义：只处理会破坏 XML 结构的 5 个字符 */
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
