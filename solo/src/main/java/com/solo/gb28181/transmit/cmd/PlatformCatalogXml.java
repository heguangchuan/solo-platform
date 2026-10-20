package com.solo.gb28181.transmit.cmd;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;

import java.util.List;

/**
 * 级联 Catalog Response 报文体（XML）构造（ADR-023 合并版）。
 *
 * <p>本平台作为下级向上级回复 Catalog 查询时使用。与 {@link DeviceCommandXml} 分离：
 * 后者构造发往下级设备的短命令，本类构造回给上级的目录分片。</p>
 *
 * <p>Item 类型（按 GB/T 28181-2016）：由 {@code GbDeviceChannel.parental} 区分：</p>
 * <ul>
 *   <li><b>分组 Item</b>（parental=1）：{@code <DeviceID>}/{@code <Name>}/{@code <ParentID>}，
 *       固定 {@code <Parental>1</Parental>}，分组类型码 215 业务分组 / 216 虚拟组织
 *       （216 时额外补 {@code <BusinessGroupID>}），无 {@code <Status>}。</li>
 *   <li><b>通道 Item</b>（parental=0）：完整前端设备字段集，{@code <Parental>0</Parental>}，
 *       {@code <Status>} 由 {@code status} 映射（ON/OFF）。</li>
 * </ul>
 */
public final class PlatformCatalogXml {

    private PlatformCatalogXml() {}

    /**
     * 构造一片 Catalog Response XML。
     *
     * @param platform    上级平台配置；从 p 取 serverGbId 拼 URI 等
     * @param deviceGbId  本平台国标编号（从全局 gb28181.id 取，ADR-036）；Item 内部兜底 ParentID 用
     * @param sn          原请求的 SN，透传回去
     * @param sumNum      全量总条数（每片都填相同值）
     * @param items       本片中的 catalog item（分组 parental=1 + 通道 parental=0），调用方保证
     *                    分组排在通道之前
     */
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

    /**
     * 渲染 DeviceList 片段（分组 parental=1 + 通道 parental=0），供 Response 与 NOTIFY 复用。
     */
    public static void appendDeviceList(StringBuilder sb, String deviceGbId, List<GbDeviceChannel> items) {
        sb.append("<DeviceList Num=\"").append(items.size()).append("\">\r\n");
        for (GbDeviceChannel it : items) {
            appendItem(sb, it, deviceGbId);
        }
        sb.append("</DeviceList>\r\n");
    }

    /**
     * 合并版 Item 渲染：按 {@code parental} 选字段集。
     * <p>分组（parental=1）：DeviceID = item.channelId（20 位国标码 215/216）；
     *   ParentID = item.parentId（父分组 GB 码；ROOT_PARENT 时兜底到 deviceGbId）。</p>
     * <p>通道（parental=0）：DeviceID 优先 exposed_code（平台唯一），回退 channelId；
     *   ParentID = item.parentId（父分组 GB 码；ROOT_PARENT 时兜底到 deviceGbId）。</p>
     */
    private static void appendItem(StringBuilder sb, GbDeviceChannel it, String deviceGbId) {
        boolean isGroup = it.getParental() != null && it.getParental() == 1;
        String reportId;
        if (isGroup) {
            // 分组：直接用自身 GB 码
            reportId = it.getChannelId();
        } else {
            // 通道：暴露码 helper 统一回退（ADR-026 决策 1 配套保险）
            reportId = it.getExposedOrChannelId();
        }

        String parentCode = resolveParentCode(it.getParentId(), deviceGbId);

        sb.append("<Item>\r\n");
        sb.append("<DeviceID>").append(nullSafe(reportId)).append("</DeviceID>\r\n");
        sb.append("<Name>").append(escapeXml(it.getName())).append("</Name>\r\n");
        if (isGroup) {
            // 分组：固定 Manufacturer/Model/Owner
            sb.append("<Manufacturer>Solo</Manufacturer>\r\n");
            sb.append("<Model>Group</Model>\r\n");
            sb.append("<Owner>Solo</Owner>\r\n");
        } else {
            // 通道：业务字段
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
                // 216 虚拟组织需要 BusinessGroupID；solo 无独立字段，用本平台 deviceGbId 兜底
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

    /**
     * 解析 item.parentId → XML 中实际写入的 &lt;ParentID&gt;：
     * <ul>
     *   <li>"0"（根哨兵）→ 兜底 deviceGbId（挂到平台自身节点下）</li>
     *   <li>其他值 → 直接用 item.parentId 自身（已是父 item 的 20 位 GB 码）</li>
     * </ul>
     */
    private static String resolveParentCode(String parentId, String deviceGbId) {
        if (parentId == null || ROOT_PARENT.equals(parentId)) {
            return deviceGbId;
        }
        return parentId;
    }

    /** 根哨兵——item.parent_id == "0" 表无父 */
    private static final String ROOT_PARENT = "0";

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
