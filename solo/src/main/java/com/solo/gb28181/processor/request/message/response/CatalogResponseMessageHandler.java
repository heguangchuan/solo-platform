package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
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
 * <h3>Item 分流（ID 长度 + 类型码 双信号）</h3>
 * <ul>
 *   <li><b>20 位标准 GB ID</b>：严格按类型码分流
 *     <ul>
 *       <li>类型码 200 / 215 / 216 → 目录节点（{@link GbGroup}）</li>
 *       <li>类型码 200-214 且 {@code Parental=1} → 目录节点（兼容对端把中心设备当分组上报的写法）</li>
 *       <li>其他 → 通道（{@link GbDeviceChannel}）</li>
 *     </ul>
 *     <p><b>注意：故意不收 130-198 + Parental=1</b>。海康相机普遍把 {@code <Parental>} 错填成 65535，
 *     兜底为 1 后会被该规则误吞进 {@code gb_group}，导致海康相机的通道完全没入库。</p>
 *   </li>
 *   <li><b>≤8 位纯数字 ID</b>：按 GB/T 2260 行政区域码处理，强制当目录节点</li>
 *   <li><b>9-19 位</b>：长度非标（既不是合法行政码也不是标准 GB ID），丢弃并打 WARN</li>
 * </ul>
 *
 * <h3>Parental 字段兜底</h3>
 * <p>部分 NVR 厂商会把 {@code Parental} 错填成 65535（应填 0/1），按"非 '0' 一律算 1"处理。
 * 路由判定和字段存储共用 {@link #parseParental}，保证两者一致。</p>
 *
 * <h3>ParentID 字段</h3>
 * <p>分组 Item：{@code <ParentID>} 空串时先兜底成设备自身的 DeviceID（而不是 "0"）。
 * 真正的"父是否可达"判定在 {@code CatalogSyncSupport} 的 final flush 阶段做——任何指向
 * 不存在分组的 parent（设备漏报父节点、报了非法 parent）都会被改挂到 device_id 下，
 * 保证分组树有唯一收口、不出现游离顶层节点。
 * 通道 Item：父 ID 仅用于 final flush 阶段反查 group_id，反查失败时回退默认分组。</p>
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

        // 遍历 Item，按 ID 长度 + 类型码 双信号分流
        List<GbDeviceChannel> chunkChannels = new ArrayList<>();
        List<GbGroup> chunkGroups = new ArrayList<>();
        Map<String, String> channelParentIdMap = new HashMap<>();
        Iterator<Element> it = deviceList.elementIterator("Item");
        while (it.hasNext()) {
            Element item = it.next();
            String itemId = XmlUtils.childText(item, "DeviceID");
            if (itemId == null || itemId.isBlank()) continue;

            int len = itemId.length();
            // ID 长度规则：20 位走 GB 标准类型码分流；≤8 位按 GB/T 2260 行政码当目录；
            // 9-19 位既不是合法行政码也不是标准 GB ID，丢弃
            if (len != 20 && len > 8) {
                log.warn("[通道目录] 异常 ID 长度 len={} deviceId={}，丢弃", len, itemId);
                continue;
            }

            int typeCode = (len >= 13) ? SipUtils.getTypeCodeFromGbCode(itemId) : 0;
            int parental = parseParental(item);

            boolean isDirectoryType;
            if (len <= 8) {
                // 6-8 位行政码 → 强制目录
                isDirectoryType = true;
            } else {
                // 20 位标准 GB ID：200/215/216 是目录；200-214 范围内 Parental=1 也按目录
                // （200+ 是中心设备类，海康相机不会用，冲突面小）。
                // ⚠️ 故意不收 130-198 + Parental=1：海康相机普遍把 <Parental> 错填成 65535，
                // 兜底成 1 后会被这条规则误吞进 gb_group，导致海康相机的通道完全没入库。
                isDirectoryType = (typeCode == 200 || typeCode == 215 || typeCode == 216)
                        || (typeCode >= 200 && typeCode <= 214 && parental == 1);
            }

            // ParentID：空串 → 设备自身 DeviceID（让"顶层 215/216"挂在设备节点下，而非根）
            String parentId = XmlUtils.childText(item, "ParentID");
            String effectiveParentId = (parentId == null || parentId.isBlank())
                    ? device.getDeviceId() : parentId;

            if (isDirectoryType) {
                GbGroup group = parseGroup(item, device, effectiveParentId);
                if (group != null) {
                    chunkGroups.add(group);
                }
            } else {
                GbDeviceChannel channel = parseChannel(item, device);
                if (channel != null) {
                    chunkChannels.add(channel);
                    channelParentIdMap.put(channel.getChannelId(), effectiveParentId);
                }
            }
        }

        // 累加分片（内部阈值 flush；sumNum 收齐时返回 true）
        boolean complete = cache.accumulate(ctx, chunkChannels, chunkGroups, channelParentIdMap);

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
        CatalogSyncContext stub = new CatalogSyncContext(deviceId, sn, sumNum, 1, 0L);
        stub.done = true;
        stub.completenessRatio = 1.0;
        return stub;
    }

    // ---------------- Item 解析（与原实现一致） ----------------

    private GbGroup parseGroup(Element item, GbDevice device, String parentId) {
        String groupId = XmlUtils.childText(item, "DeviceID");
        if (groupId == null || groupId.isBlank()) return null;

        GbGroup group = new GbGroup();
        group.setGroupId(groupId);
        group.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        // parent_id 在 parse 阶段直接写入（空串已在 handleForDevice 兜底成 device_id）
        group.setParentId(parentId);
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
        ch.setParental(parseParental(item));
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

    /**
     * 解析 {@code Parental} 字段，对异常值做兜底。
     * <p>部分 NVR 厂商会把 {@code Parental} 错填成 65535（应填 0/1），按"非 '0' 一律算 1"处理。
     * 路由判定（{@link #handleForDevice} 的目录/通道分流）和字段存储（{@link #parseChannel}）
     * 共用此方法，保证两者行为一致。</p>
     */
    private static int parseParental(Element item) {
        String s = XmlUtils.childText(item, "Parental");
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.trim()) == 0 ? 0 : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

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
