package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.support.CatalogSyncContext;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Catalog 响应处理（&lt;Response&gt;&lt;CmdType&gt;Catalog&lt;/CmdType&gt;...）。
 * <p>10w+ 通道重写：handler 不再持有"目录节点 ID 解析 + 父 group 映射 + 平台根目录创建"等业务逻辑，
 * 只负责 XML 解析 + Item 分流 + 调 {@link CatalogDataCache#accumulate}。
 * 落库在 {@code catalogSyncExecutor} 上异步跑，handler 立即返回，不阻塞 SIP 分发线程。</p>
 *
 * <h3>Item 分流</h3>
 * <ul>
 *   <li>类型码 215/216 或 Parental=1 → 目录节点（{@link GbGroup}）</li>
 *   <li>类型码 130-199 → 通道（{@link GbDeviceChannel}）</li>
 * </ul>
 *
 * <h3>complete 路径</h3>
 * <p>累计收到 &gt;= sumNum 时由 {@code accumulate} 内部触发收尾，
 * 本类只处理"sumNum=0 空目录"和"sweep 强收"两个特殊路径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogResponseMessageHandler implements MessageHandler {

    private final CatalogDataCache cache;
    private final GbDeviceChannelService channelService;
    private final GbDeviceService deviceService;
    private final GbPlatformService gbPlatformService;

    @Override
    public String getRootType() { return "Response"; }

    @Override
    public String getCmdType() { return "Catalog"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        int sn = parseInt(XmlUtils.childText(root, "SN"), -1);
        int sumNum = parseInt(XmlUtils.childText(root, "SumNum"), 0);
        if (sn < 0) {
            log.warn("[通道目录] 缺失 SN，丢弃 deviceId={}", device.getDeviceId());
            return;
        }

        if (sumNum == 0) {
            log.info("[通道目录] 设备无通道 deviceId={}", device.getDeviceId());
            // 设备通告空目录：清理并记录 lastCatalogSyncTime
            channelService.deleteByDeviceDbId(device.getId());
            device.setLastCatalogSyncTime(java.time.LocalDateTime.now());
            device.setChannelCount(0);
            deviceService.updateById(device);
            // 通知进度服务收尾（防前端等不到完成态）
            cache.finalize(buildIdleContextForEmpty(device.getDeviceId(), sn, sumNum),
                    true, "sumNum=0-空目录");
            return;
        }

        Element deviceList = root.element("DeviceList");
        if (deviceList == null) {
            log.warn("[通道目录] 报文无 DeviceList 节点 deviceId={}", device.getDeviceId());
            return;
        }

        // 启动 / 复用同步上下文
        CatalogSyncContext ctx = cache.startSync(device.getDeviceId(), sn, sumNum);
        if (ctx == null) {
            return;
        }

        // 遍历 Item，按类型码分流
        List<GbDeviceChannel> chunkChannels = new ArrayList<>();
        List<GbGroup> chunkGroups = new ArrayList<>();
        Map<String, String> channelParentIdMap = new HashMap<>();
        Map<String, String> groupParentIdMap = new HashMap<>();
        Iterator<Element> it = deviceList.elementIterator("Item");
        while (it.hasNext()) {
            Element item = it.next();
            String itemId = XmlUtils.childText(item, "DeviceID");
            if (itemId == null || itemId.isBlank()) continue;

            int typeCode = SipUtils.getTypeCodeFromGbCode(itemId);
            boolean isDirectoryType = (typeCode == 215 || typeCode == 216);

            if (isDirectoryType) {
                GbGroup group = parseGroup(item, device);
                if (group != null) {
                    chunkGroups.add(group);
                    String parentId = XmlUtils.childText(item, "ParentID");
                    if (parentId != null && !parentId.isBlank()) {
                        groupParentIdMap.put(group.getGroupId(), parentId);
                    }
                }
            } else {
                GbDeviceChannel channel = parseChannel(item, device);
                if (channel != null) {
                    chunkChannels.add(channel);
                    String parentId = XmlUtils.childText(item, "ParentID");
                    if (parentId != null && !parentId.isBlank()) {
                        channelParentIdMap.put(channel.getChannelId(), parentId);
                    }
                }
            }
        }

        // 累加分片（内部阈值 flush；sumNum 收齐时返回 true）
        boolean complete = cache.accumulate(ctx, chunkChannels, chunkGroups, channelParentIdMap, groupParentIdMap);

        if (complete) {
            // 收齐分支已在 accumulate 内 finalize，这里仅记录日志
            log.info("[通道目录] 本片触发收齐 deviceId={} sn={}", device.getDeviceId(), sn);
        }
    }

    /**
     * sumNum=0 路径下也要触发一次 finalize 通知前端"已完成"，但 CatalogSyncContext 已 done。
     * 这里直接走一个轻量 ctx（不进 contexts Map），publish 一次事件即可。
     */
    private CatalogSyncContext buildIdleContextForEmpty(String deviceId, int sn, int sumNum) {
        CatalogSyncContext stub = new CatalogSyncContext(deviceId, sn, sumNum, 1);
        stub.done = true;
        stub.completenessRatio = 1.0;
        return stub;
    }

    // ---------------- Item 解析（与原实现一致） ----------------

    private GbGroup parseGroup(Element item, GbDevice device) {
        String groupId = XmlUtils.childText(item, "DeviceID");
        if (groupId == null || groupId.isBlank()) return null;

        GbGroup group = new GbGroup();
        group.setGroupId(groupId);
        group.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        group.setPlatformId(device.getPlatformId());
        group.setSort(1);
        return group;
    }

    private GbDeviceChannel parseChannel(Element item, GbDevice device) {
        String channelId = XmlUtils.childText(item, "DeviceID");
        if (channelId == null || channelId.isBlank()) return null;

        GbDeviceChannel ch = new GbDeviceChannel();
        ch.setDeviceDbId(device.getId());
        ch.setDeviceId(device.getDeviceId());
        ch.setChannelId(channelId);
        ch.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        ch.setManufacturer(emptyToNull(XmlUtils.childText(item, "Manufacturer")));
        ch.setModel(emptyToNull(XmlUtils.childText(item, "Model")));
        ch.setOwner(emptyToNull(XmlUtils.childText(item, "Owner")));
        ch.setCivilCode(emptyToNull(XmlUtils.childText(item, "CivilCode")));
        ch.setBlock(emptyToNull(XmlUtils.childText(item, "Block")));
        ch.setAddress(emptyToNull(XmlUtils.childText(item, "Address")));
        ch.setParental(parseInt(XmlUtils.childText(item, "Parental"), 0));
        ch.setSafetyWay(parseInt(XmlUtils.childText(item, "SafetyWay"), 0));
        ch.setRegisterWay(parseInt(XmlUtils.childText(item, "RegisterWay"), 1));
        ch.setCertNum(emptyToNull(XmlUtils.childText(item, "CertNum")));
        ch.setCertifiable(parseInt(XmlUtils.childText(item, "Certifiable"), 0));
        ch.setErrCode(parseIntOrNull(XmlUtils.childText(item, "ErrCode")));
        ch.setSecrecy(parseInt(XmlUtils.childText(item, "Secrecy"), 0));
        ch.setIpAddress(emptyToNull(XmlUtils.childText(item, "IPAddress")));
        ch.setPort(parseIntOrNull(XmlUtils.childText(item, "Port")));
        ch.setPassword(emptyToNull(XmlUtils.childText(item, "Password")));
        String status = XmlUtils.childText(item, "Status");
        ch.setStatus(status == null || status.isBlank() ? "OFF" : status.toUpperCase());
        ch.setLongitude(parseDouble(XmlUtils.childText(item, "Longitude")));
        ch.setLatitude(parseDouble(XmlUtils.childText(item, "Latitude")));

        Element info = item.element("Info");
        if (info != null) {
            ch.setPtzType(parseInt(XmlUtils.childText(info, "PTZType"), 0));
        }
        return ch;
    }

    // ---------------- 辅助 ----------------

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
