package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.utils.GbCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 目录共享服务实现（ADR-023 合并版）。
 * <p>共享关系统一存 {@code gb_platform_share}（不分 group/channel），分组/通道的区分由
 * resource_id 指向的 {@code gb_device_channel} 行的 {@code parental} 字段派生。</p>
 * <p>前端契约保留 g_/c_ 前缀：g_ → 分组（parental=1），c_ → 通道（parental=0）。
 * 后端按前缀解析入库，零改动前端。</p>
 * <p>{@link #shareTree} 三级树：<b>设备节点</b>（gb_device，不可勾选）→ <b>分组</b>（parental=1）
 * → <b>通道</b>（parental=0）。因 catalog item 的 {@code parent_id} 大多是所属设备 GB 码
 * （通道直挂设备时），以设备作枝干才能把所有 item 都挂进树，不会漏。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogShareServiceImpl implements CatalogShareService {

    /** 前端 el-tree node-key 前缀：分组（parental=1）/ 通道（parental=0），避免主键碰撞 */
    private static final String GROUP_PREFIX = "g_";
    private static final String CHANNEL_PREFIX = "c_";
    /** 设备节点前缀（仅作枝干展示，不可勾选共享） */
    private static final String DEVICE_PREFIX = "d_";

    private final GbDeviceChannelMapper channelMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbPlatformShareMapper shareMapper;
    private final GbCodeGenerator gbCodeGenerator;

    @Override
    public List<GbShareTreeNodeResponse> shareTree() {
        // 1) 拉所有 catalog item（分组 parental=1 + 通道 parental=0），按 device_id 聚合
        List<GbDeviceChannel> allItems = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .orderByDesc(GbDeviceChannel::getParental)   // 分组在前
                        .orderByAsc(GbDeviceChannel::getChannelId));
        if (allItems.isEmpty()) {
            return new ArrayList<>();
        }

        // 2) 拉设备表（作枝干标签），按 GB 码索引
        Map<String, GbDevice> deviceByGbId = deviceMapper.selectList(
                        new LambdaQueryWrapper<GbDevice>().orderByAsc(GbDevice::getName)).stream()
                .collect(Collectors.toMap(GbDevice::getDeviceId, d -> d, (a, b) -> a, LinkedHashMap::new));

        // 3) 按 device_id 分桶（保持 allItems 内已排好的 parental/channelId 顺序）
        Map<String, List<GbDeviceChannel>> itemsByDevice = new LinkedHashMap<>();
        for (GbDeviceChannel it : allItems) {
            if (it.getDeviceId() == null) continue;
            itemsByDevice.computeIfAbsent(it.getDeviceId(), k -> new ArrayList<>()).add(it);
        }

        // 4) 组装：设备节点 → 该设备下的分组 + 通道（同级平铺，分组排前）
        //    以设备作枝干而非按 parent_id 递归：catalog item 的 parent_id 大多是所属设备 GB 码
        //    （通道直挂设备），按 parent_id 建树会因"设备不在 gb_device_channel 表里"而漏掉通道。
        List<GbShareTreeNodeResponse> roots = new ArrayList<>();
        for (Map.Entry<String, List<GbDeviceChannel>> entry : itemsByDevice.entrySet()) {
            String deviceGbId = entry.getKey();
            GbDevice device = deviceByGbId.get(deviceGbId);
            GbShareTreeNodeResponse deviceNode = new GbShareTreeNodeResponse();
            deviceNode.setId(DEVICE_PREFIX + deviceGbId);
            deviceNode.setLabel(device != null && device.getName() != null
                    ? device.getName() + " (" + deviceGbId + ")"
                    : deviceGbId);
            deviceNode.setType("DEVICE");
            deviceNode.setChildren(new ArrayList<>());

            for (GbDeviceChannel it : entry.getValue()) {
                boolean isGroup = it.getParental() != null && it.getParental() == 1;
                GbShareTreeNodeResponse child = new GbShareTreeNodeResponse();
                child.setId((isGroup ? GROUP_PREFIX : CHANNEL_PREFIX) + it.getId());
                String name = (it.getName() == null || it.getName().isBlank()) ? "-" : it.getName();
                child.setLabel(name + " (" + it.getChannelId() + ")");
                child.setType(isGroup ? "GROUP" : "CHANNEL");
                deviceNode.getChildren().add(child);
            }
            roots.add(deviceNode);
        }
        return roots;
    }

    @Override
    public GbPlatformSharedResponse getShared(Long platformId) {
        List<GbPlatformShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId));

        GbPlatformSharedResponse resp = new GbPlatformSharedResponse();
        // 一份 channelIds 列表混合 g_/c_ 前缀的节点 id，前端按前缀渲染。
        // 通过 JOIN catalog item 派生 parental 来派生前缀。
        List<String> nodeIds = shares.stream()
                .map(this::prefixResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        resp.setChannelIds(nodeIds);
        return resp;
    }

    /**
     * 拿 resource_id + parental 派生前缀节点 id。
     */
    private String prefixResourceId(GbPlatformShare s) {
        GbDeviceChannel c = channelMapper.selectById(s.getResourceId());
        if (c == null) return null;
        return (c.getParental() != null && c.getParental() == 1 ? GROUP_PREFIX : CHANNEL_PREFIX) + s.getResourceId();
    }

    @Override
    @Transactional
    public void saveShared(Long platformId, GbPlatformShareSaveRequest request) {
        // ADR-026 懒生成：默认透传原始 channelId，仅当"本批 + 历史合并"撞码时才为撞码集合统一取码。
        // 1) 解析节点 id
        List<Long> channelIds = parseIds(request.getChannelIds(), CHANNEL_PREFIX);
        List<Long> groupIds = parseIds(request.getChannelIds(), GROUP_PREFIX);

        // 2) 拉本批要共享的通道实体（仅 parental=0；不在表里的 resourceId 自然被过滤）
        Map<Long, GbDeviceChannel> channelByResourceId = channelIds.isEmpty()
                ? Map.of()
                : channelMapper.selectList(new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getParental, 0)
                        .in(GbDeviceChannel::getId, channelIds)).stream()
                .collect(Collectors.toMap(GbDeviceChannel::getId, c -> c, (a, b) -> a));

        // 3) 查"该平台删旧前"已共享的非本批通道的 channelId 集合。
        //    用于识别"本批与历史撞码"——必须在删旧前查，删旧后历史就没了。
        //    仅 parental=0 参与（分组不参与 exposedCode，ADR-023 决策 3）。
        Set<String> historyChannelIds = new HashSet<>();
        if (!channelByResourceId.isEmpty()) {
            List<GbDeviceChannel> existingShared = shareMapper.selectSharedChannels(platformId);
            for (GbDeviceChannel c : existingShared) {
                if (c.getChannelId() != null && !channelByResourceId.containsKey(c.getId())) {
                    historyChannelIds.add(c.getChannelId());
                }
            }
        }

        // 4) 查"本批通道是否已分码"（按 resource_id 跨平台稳定，ADR-020 决策 4）
        //    必须在删旧前捞：全量覆盖会先删本平台旧行，若本平台是该通道唯一分享方，
        //    删后就取不到，会重新生成不同码 → 破坏上级已缓存的旧码。
        Map<Long, String> exposedByChannel = new HashMap<>();
        if (!channelIds.isEmpty()) {
            shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                            .isNotNull(GbPlatformShare::getExposedCode)
                            .in(GbPlatformShare::getResourceId, channelIds)
                            .select(GbPlatformShare::getResourceId, GbPlatformShare::getExposedCode))
                    .forEach(s -> exposedByChannel.putIfAbsent(s.getResourceId(), s.getExposedCode()));
        }

        // 5) 撞码检测：本批按 channelId 分组，size>1 自身撞码；或与 historyChannelIds 相交视为撞码
        Map<String, List<Long>> conflictByChannelId = new HashMap<>();
        Map<String, List<Long>> batchByChannelId = channelByResourceId.values().stream()
                .filter(c -> c.getChannelId() != null)
                .collect(Collectors.groupingBy(
                        GbDeviceChannel::getChannelId,
                        Collectors.mapping(GbDeviceChannel::getId, Collectors.toList())));
        for (Map.Entry<String, List<Long>> e : batchByChannelId.entrySet()) {
            if (e.getValue().size() > 1 || historyChannelIds.contains(e.getKey())) {
                conflictByChannelId.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }

        // 6) 撞码集合中无码的统一按 (deviceId, channelId) 派生（纯函数，ADR-026 哈希派生版）
        //    无需 max+1、无需全表扫描，O(1) 算 hash。同一 (deviceId, channelId) 永远得同一码。
        Set<Long> conflictResourceIds = conflictByChannelId.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        List<Long> needCode = conflictResourceIds.stream()
                .filter(rid -> !exposedByChannel.containsKey(rid))
                .distinct()
                .toList();
        for (Long rid : needCode) {
            GbDeviceChannel ch = channelByResourceId.get(rid);
            if (ch == null) continue; // 防御：@Transactional 内不会发生
            String code = gbCodeGenerator.nextExposedChannelCode(ch.getDeviceId(), ch.getChannelId());
            exposedByChannel.put(rid, code);
        }

        // 7) 全量覆盖：先删该平台旧的共享关系
        shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId));

        // 8) 构插入
        List<GbPlatformShare> toInsert = new ArrayList<>();

        // 分组：exposedCode 写 null（分组不参与编码，ADR-023 决策 3）
        if (!groupIds.isEmpty()) {
            Set<Long> existingGroups = channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>()
                                    .eq(GbDeviceChannel::getParental, 1)
                                    .in(GbDeviceChannel::getId, groupIds))
                    .stream().map(GbDeviceChannel::getId).collect(Collectors.toSet());
            for (Long gid : groupIds) {
                if (!existingGroups.contains(gid)) continue; // 分组已不存在，跳过
                toInsert.add(newShare(platformId, gid, null));
            }
        }

        // 通道：撞码的写码，不撞码的写 null（沿用原始 channelId 透传给上级）
        for (Long cid : channelByResourceId.keySet()) {
            String code = conflictResourceIds.contains(cid) ? exposedByChannel.get(cid) : null;
            toInsert.add(newShare(platformId, cid, code));
        }

        if (!toInsert.isEmpty()) {
            Db.saveBatch(toInsert);
        }

        log.info("[通道推送] 保存共享: platformId={} 分组数={} 通道数={} 撞码通道数={}",
                platformId, groupIds.size(), channelByResourceId.size(), conflictResourceIds.size());
    }

    @Override
    public List<GbDeviceChannel> loadSharedCatalogItems(Long platformId) {
        return shareMapper.selectSharedCatalogItems(platformId);
    }

    @Override
    public List<GbDeviceChannel> loadSharedChannels(Long platformId) {
        return shareMapper.selectSharedChannels(platformId);
    }

    /** 解析带前缀的节点 id 列表 → 去重后的雪花主键列表 */
    private static List<Long> parseIds(List<String> nodeIds, String prefix) {
        if (nodeIds == null) return List.of();
        return nodeIds.stream()
                .filter(s -> s != null && s.startsWith(prefix))
                .map(s -> s.substring(prefix.length()))
                .distinct()
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private static GbPlatformShare newShare(Long platformId, Long resourceId, String exposedCode) {
        GbPlatformShare row = new GbPlatformShare();
        row.setPlatformId(platformId);
        row.setResourceId(resourceId);
        row.setExposedCode(exposedCode);
        return row;
    }
}
