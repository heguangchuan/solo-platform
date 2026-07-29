package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbGroupService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Catalog 响应处理（<Response><CmdType>Catalog</CmdType>...）
 * <p>设备目录会分片送回，Item 分为两类：</p>
 * <ul>
 *   <li><b>目录节点</b>（类型码 215 业务分组 / 216 虚拟组织，或 Parental=1 的非前端设备）→ 存 {@link GbGroup}</li>
 *   <li><b>通道</b>（类型码 130-199 前端设备）→ 存 {@link GbDeviceChannel}</li>
 * </ul>
 * <p>若源设备命中 {@link GbPlatform} 白名单，目录节点和通道都处理；<br>
 * 未命中白名单则忽略目录节点，仅通道入库并归到默认分组（普通相机/NVR 行为）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogResponseMessageHandler implements MessageHandler {

    private final CatalogDataCache cache;
    private final GbDeviceChannelService channelService;
    private final GbDeviceService deviceService;
    private final GbPlatformService gbPlatformService;
    private final GbGroupService gbGroupService;

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
            channelService.deleteByDeviceDbId(device.getId());
            markSyncDone(device, 0);
            return;
        }

        Element deviceList = root.element("DeviceList");
        if (deviceList == null) {
            log.warn("[通道目录] 报文无 DeviceList 节点 deviceId={}", device.getDeviceId());
            return;
        }

        // 判断源设备是否命中白名单（是下级平台）
        boolean isPlatform = gbPlatformService.lambdaQuery()
                .eq(GbPlatform::getPlatformId, device.getDeviceId())
                .eq(GbPlatform::getEnabled, 1)
                .exists();

        // 遍历 Item，按类型码分流
        List<GbDeviceChannel> chunkChannels = new ArrayList<>();
        List<GbGroup> chunkGroups = new ArrayList<>();
        // 记录通道 Item 的 <ParentID>（GB码），供批量入库时映射到目录节点雪花 id
        Map<String, String> channelParentIdMap = new HashMap<>();
        // 记录目录节点 Item 的 <ParentID>（GB码），供批量入库时解析父分组雪花 id（key = 本节点 groupId GB码）
        Map<String, String> groupParentIdMap = new HashMap<>();
        Iterator<Element> it = deviceList.elementIterator("Item");
        while (it.hasNext()) {
            Element item = it.next();
            String itemId = XmlUtils.childText(item, "DeviceID");
            if (itemId == null || itemId.isBlank()) continue;

            int typeCode = SipUtils.getTypeCodeFromGbCode(itemId);
            boolean isDirectoryType = (typeCode == 215 || typeCode == 216);

            if (isDirectoryType) {
                if (isPlatform) {
                    GbGroup group = parseGroup(item, device);
                    if (group != null) {
                        chunkGroups.add(group);
                        // 暂存目录节点 <ParentID>（GB码），等所有分组入库后再解析成父分组雪花 id
                        String parentId = XmlUtils.childText(item, "ParentID");
                        if (parentId != null && !parentId.isBlank()) {
                            groupParentIdMap.put(group.getGroupId(), parentId);
                        }
                    }
                } else {
                    log.debug("[通道目录] 未命中白名单，忽略目录节点 deviceId={} itemId={}", device.getDeviceId(), itemId);
                }
            } else {
                GbDeviceChannel channel = parseChannel(item, device);
                if (channel != null) {
                    chunkChannels.add(channel);
                    // 暂存 <ParentID>（GB码），等批量入库时对照目录节点映射
                    String parentId = XmlUtils.childText(item, "ParentID");
                    if (parentId != null && !parentId.isBlank()) {
                        channelParentIdMap.put(channel.getChannelId(), parentId);
                    }
                }
            }
        }

        // 累加分片（两个 ParentID 映射也累加进 batch，避免跨片丢失）
        CatalogDataCache.Batch batch = cache.accumulate(
                device.getDeviceId(), sn, sumNum, chunkChannels, chunkGroups,
                channelParentIdMap, groupParentIdMap);
        if (batch != null && batch.done) {
            // 收齐 → 先处理目录节点，再处理通道
            processBatch(batch, device, isPlatform);
        }
    }

    // ---------------- 批量入库 ----------------

    private void processBatch(CatalogDataCache.Batch batch, GbDevice device, boolean isPlatform) {
        List<GbGroup> groups = batch.groups;
        List<GbDeviceChannel> channels = batch.channels;
        Map<String, String> channelParentIdMap = batch.channelParentIdMap;
        Map<String, String> groupParentIdMap = batch.groupParentIdMap;

        // 白名单平台：始终保证有一个以平台自身为根的目录，用于兜底挂载没有 ParentID 或 ParentID 不在
        // 本次同步范围内的目录节点/通道。根目录 groupId 取平台国标 ID 转换后的 215 业务分组码。
        String platformRootCode = null;
        if (isPlatform) {
            platformRootCode = SipUtils.toBusinessGroupId(device.getDeviceId());
            GbPlatform platform = gbPlatformService.lambdaQuery()
                    .eq(GbPlatform::getPlatformId, device.getDeviceId())
                    .one();
            String rootName = (platform != null && platform.getName() != null && !platform.getName().isBlank())
                    ? platform.getName() : device.getDeviceId();
            GbGroup root = new GbGroup();
            root.setGroupId(platformRootCode);
            root.setName(rootName);
            root.setPlatformId(device.getPlatformId());
            root.setSort(1);
            groups.addFirst(root);
            log.info("[通道目录] 平台根目录已就位 deviceId={} groupCode={} name={}",
                    device.getDeviceId(), platformRootCode, rootName);
        }

        // 分组入库：id 主键改雪花后，按 groupId(20位国标码) 唯一键去重——已存在则复用其雪花 id，
        // 不存在则用 IdWorker 预分配。建立 GB码 → 雪花 id 映射，供父分组 / 通道归属反查。
        Map<String, Long> codeToId = new HashMap<>();
        if (!groups.isEmpty()) {
            // 查库中已存在的同 groupId 分组，复用其雪花 id（避免级联重复同步插重复行）
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (GbGroup g : groups) codes.add(g.getGroupId());
            Map<String, Long> existingIdByCode = new HashMap<>();
            gbGroupService.lambdaQuery().in(GbGroup::getGroupId, codes).list()
                    .forEach(e -> existingIdByCode.put(e.getGroupId(), e.getId()));

            for (GbGroup g : groups) {
                Long id = existingIdByCode.get(g.getGroupId());
                if (id == null) {
                    id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
                }
                g.setId(id);
                codeToId.put(g.getGroupId(), id);
            }

            // 解析父分组：目录节点 <ParentID>（GB码）命中本次范围 → 指向父分组雪花 id；
            // 级联平台下 ParentID 不在范围（含 null/根）→ 挂平台根目录；根目录自身 parentId=0
            Long platformRootId = platformRootCode == null ? null : codeToId.get(platformRootCode);
            for (GbGroup g : groups) {
                if (platformRootCode != null && platformRootCode.equals(g.getGroupId())) {
                    g.setParentId(0L);
                    continue;
                }
                String parentCode = groupParentIdMap.get(g.getGroupId());
                Long parentDbId = parentCode == null ? null : codeToId.get(parentCode);
                if (parentDbId != null) {
                    g.setParentId(parentDbId);
                } else if (isPlatform && platformRootId != null) {
                    g.setParentId(platformRootId);
                } else {
                    g.setParentId(0L);
                }
            }
            gbGroupService.saveOrUpdateBatch(groups);
        }

        // 再处理通道：按 ParentID(GB码) 映射到对应目录雪花 id
        if (!channels.isEmpty()) {
            Long platformRootId = platformRootCode == null ? null : codeToId.get(platformRootCode);
            for (GbDeviceChannel ch : channels) {
                // 归属决策优先级：
                //   1) 通道 <ParentID>(GB码) 命中本次同步的目录节点 → 归该目录
                //   2) 级联平台的通道且 (1) 未命中 → 归平台根目录
                //   3) 其余（普通相机/NVR）→ 留空让 saveOrUpdateBatch → resolveGroupId 回落默认分组
                String parentCode = channelParentIdMap.get(ch.getChannelId());
                Long parentDbId = parentCode == null ? null : codeToId.get(parentCode);
                if (parentDbId != null) {
                    ch.setGroupId(parentDbId);
                } else if (isPlatform && platformRootId != null) {
                    ch.setGroupId(platformRootId);
                }
                // 设置 platformId 继承自设备
                ch.setPlatformId(device.getPlatformId());
                // 级联平台推上来的通道来源标记为级联，普通设备推的标记为设备注册
                ch.setSource(isPlatform ? GbSource.CASCADE : GbSource.REGISTER);
            }
            channelService.saveOrUpdateBatch(channels);
        }

        markSyncDone(device, channels.size());
        log.info("[通道目录] 同步完成 deviceId={} 通道={} 目录={}",
                device.getDeviceId(), channels.size(), groups.size());
    }

    // ---------------- Item 解析 ----------------

    /**
     * 解析目录节点 Item（215 业务分组 / 216 虚拟组织），构造 GbGroup。
     * <p>id（雪花）与 parentId（父分组雪花 id）由 {@link #processBatch} 统一分配 / 解析，此处只填
     * groupId(20位国标码) 与 name；ParentID(GB码) 在调用处收进 groupParentIdMap。</p>
     */
    private GbGroup parseGroup(Element item, GbDevice device) {
        String groupId = XmlUtils.childText(item, "DeviceID");
        if (groupId == null || groupId.isBlank()) return null;

        GbGroup group = new GbGroup();
        // groupId = 20 位国标码；id / parentId 由 processBatch 回填（见方法注释）
        group.setGroupId(groupId);
        group.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        group.setPlatformId(device.getPlatformId());
        group.setSort(1);
        return group;
    }

    /**
     * 解析通道 Item（130-199 前端设备），构造 GbDeviceChannel
     * 与原有逻辑一致，仅新增 groupId 留空由 processBatch 回填，不在此处设默认值
     */
    private GbDeviceChannel parseChannel(Element item, GbDevice device) {
        String channelId = XmlUtils.childText(item, "DeviceID");
        if (channelId == null || channelId.isBlank()) return null;

        GbDeviceChannel ch = new GbDeviceChannel();
        ch.setDeviceDbId(device.getId());
        ch.setDeviceId(device.getDeviceId());
        ch.setChannelId(channelId);
        // source 由 processBatch 按 isPlatform 统一回填（级联=CASCADE，普通设备=REGISTER），此处不设
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
        // EndTime 国标格式可能很乱，先存原值/暂不解析
        ch.setSecrecy(parseInt(XmlUtils.childText(item, "Secrecy"), 0));
        ch.setIpAddress(emptyToNull(XmlUtils.childText(item, "IPAddress")));
        ch.setPort(parseIntOrNull(XmlUtils.childText(item, "Port")));
        ch.setPassword(emptyToNull(XmlUtils.childText(item, "Password")));
        String status = XmlUtils.childText(item, "Status");
        ch.setStatus(status == null || status.isBlank() ? "OFF" : status.toUpperCase());
        ch.setLongitude(parseDouble(XmlUtils.childText(item, "Longitude")));
        ch.setLatitude(parseDouble(XmlUtils.childText(item, "Latitude")));

        // PTZType 在 Info 子节点里（GB28181-2016 9.5.2）
        Element info = item.element("Info");
        if (info != null) {
            ch.setPtzType(parseInt(XmlUtils.childText(info, "PTZType"), 0));
        }
        // platformId 由 processBatch 统一回填，此处不设
        // groupId 由 processBatch 按 ParentID 映射，此处不设（留空回落 resolveGroupId 默认分组）
        return ch;
    }

    // ---------------- 辅助 ----------------

    private void markSyncDone(GbDevice device, int count) {
        device.setLastCatalogSyncTime(LocalDateTime.now());
        device.setChannelCount(count);
        deviceService.updateById(device);
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