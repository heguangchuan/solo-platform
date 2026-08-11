package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目录共享服务实现（ADR-023 合并版）。
 * <p>共享关系统一存 {@code gb_platform_share}（不分 group/channel），分组/通道的区分由
 * resource_id 指向的 {@code gb_device_channel} 行的 {@code parental} 字段派生。</p>
 * <p>前端契约保留 g_/c_ 前缀：g_ → 分组（parental=1），c_ → 通道（parental=0）。
 * 后端按前缀解析入库，零改动前端。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogShareServiceImpl implements CatalogShareService {

    /** 前端 el-tree node-key 前缀：分组（parental=1）/ 通道（parental=0），避免主键碰撞 */
    private static final String GROUP_PREFIX = "g_";
    private static final String CHANNEL_PREFIX = "c_";

    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformShareMapper shareMapper;
    private final GbCodeGenerator gbCodeGenerator;

    @Override
    public List<GbShareTreeNodeResponse> shareTree() {
        // 1) 拉所有 catalog item：分组（parental=1）和通道（parental=0）合并查询
        // 分组在 gb_device_channel 中 device_id 为 NULL，单独查更直接
        List<GbDeviceChannel> groups = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getParental, 1)
                        .orderByAsc(GbDeviceChannel::getChannelId));
        // 通道按所属设备全量查（量级可控）
        List<GbDeviceChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .eq(GbDeviceChannel::getParental, 0)
                        .orderByAsc(GbDeviceChannel::getChannelId));

        // 2) 分组按自身 channelId（GB 码）建索引（保持出现顺序），children 用可变 list
        Map<String, GbShareTreeNodeResponse> groupByCode = new LinkedHashMap<>();
        for (GbDeviceChannel g : groups) {
            if (g.getChannelId() == null) continue;
            GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
            node.setId(GROUP_PREFIX + g.getId());
            node.setLabel(g.getName());
            node.setType("GROUP");
            node.setChildren(new ArrayList<>());
            groupByCode.put(g.getChannelId(), node);
        }

        // 3) 通道作为叶子挂到所属分组（parentId = 所属分组 GB 码）；parentId 不可达则跳过
        for (GbDeviceChannel ch : channels) {
            String parentCode = ch.getParentId();
            if (parentCode == null || parentCode.isBlank() || "0".equals(parentCode)) {
                continue;  // 根哨兵或未指定父项，不展示
            }
            GbShareTreeNodeResponse parent = groupByCode.get(parentCode);
            if (parent == null) {
                continue;  // parentCode 悬挂（分组被删且未回落）——跳过，不污染树
            }
            GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
            node.setId(CHANNEL_PREFIX + ch.getId());
            // 通道展示用 channelId（20 位国标码），原 CatalogItem.code 等价字段
            node.setLabel(ch.getName() + " (" + ch.getChannelId() + ")");
            node.setType("CHANNEL");
            parent.getChildren().add(node);
        }

        // 4) 组装分组层级：parentId 命中某分组 → 挂其下；为 "0"/null/不在集合 → 根节点
        List<GbShareTreeNodeResponse> roots = new ArrayList<>();
        for (GbDeviceChannel g : groups) {
            if (g.getChannelId() == null) continue;
            GbShareTreeNodeResponse self = groupByCode.get(g.getChannelId());
            String parentCode = g.getParentId();
            GbShareTreeNodeResponse parent = (parentCode == null || parentCode.isBlank() || "0".equals(parentCode))
                    ? null : groupByCode.get(parentCode);
            if (parent != null) {
                parent.getChildren().add(self);
            } else {
                roots.add(self);
            }
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
        // 当前端仅做勾选态回填时这个列表够用；后端在 save 时按前缀分类入库。
        List<String> nodeIds = shares.stream()
                .map(s -> {
                    // 需要 JOIN 派生 parental 才能派生前缀；这里简化为不加前缀直接给资源 id。
                    // 但前端 el-tree 期望带前缀的 node-key——改为 JOIN 派生：
                    return prefixResourceId(s);
                })
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
        // 通道 exposed_code 须在删除前解析：全量覆盖会先删本平台旧行，若本平台是该通道唯一分享方，
        // 删后就取不到，会重新生成不同码 → 破坏上级已缓存的旧码。
        // 故先按 resource_id 一次性捞已分配的 exposed_code（任一平台行），保证同一通道 exposed_code 恒定。
        List<Long> channelIds = parseIds(request.getChannelIds(), CHANNEL_PREFIX);
        List<Long> groupIds = parseIds(request.getChannelIds(), GROUP_PREFIX);
        Map<Long, String> exposedByChannel = new HashMap<>();
        if (!channelIds.isEmpty()) {
            shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                            .isNotNull(GbPlatformShare::getExposedCode)
                            .in(GbPlatformShare::getResourceId, channelIds)
                            .select(GbPlatformShare::getResourceId, GbPlatformShare::getExposedCode))
                    .forEach(s -> exposedByChannel.putIfAbsent(s.getResourceId(), s.getExposedCode()));
        }

        // 全量覆盖：先删该平台旧的共享关系
        shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId));

        List<GbPlatformShare> toInsert = new ArrayList<>();

        // 分组：解析 g_ 前缀主键，过滤已不存在的分组后入库
        if (!groupIds.isEmpty()) {
            Set<Long> existing = channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>()
                                    .eq(GbDeviceChannel::getParental, 1)
                                    .in(GbDeviceChannel::getId, groupIds))
                    .stream()
                    .map(GbDeviceChannel::getId)
                    .collect(Collectors.toSet());
            for (Long gid : groupIds) {
                if (!existing.contains(gid)) continue; // 分组已不存在，跳过
                toInsert.add(newShare(platformId, gid, null));  // 分组 exposedCode 为 null
            }
        }

        // 通道：过滤已不存在的通道后入库；每个通道分配/复用 exposed_code（对上级暴露的唯一码）
        if (!channelIds.isEmpty()) {
            Set<Long> existing = channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>()
                                    .eq(GbDeviceChannel::getParental, 0)
                                    .in(GbDeviceChannel::getId, channelIds))
                    .stream()
                    .map(GbDeviceChannel::getId)
                    .collect(Collectors.toSet());
            // 现存且尚无 exposed_code 的通道，一次性批量取码（避免逐个通道全表扫描的 O(n²)）
            List<Long> needCode = channelIds.stream()
                    .filter(existing::contains)
                    .filter(cid -> !exposedByChannel.containsKey(cid))
                    .distinct()
                    .toList();
            List<String> newCodes = gbCodeGenerator.nextExposedChannelCodes(needCode.size());
            for (int i = 0; i < needCode.size(); i++) {
                exposedByChannel.put(needCode.get(i), newCodes.get(i));
            }
            for (Long cid : channelIds) {
                if (!existing.contains(cid)) continue; // 通道已不存在，跳过
                toInsert.add(newShare(platformId, cid, exposedByChannel.get(cid)));
            }
        }

        if (!toInsert.isEmpty()) {
            Db.saveBatch(toInsert);
        }

        log.info("[通道推送] 保存共享: platformId={} 分组数={} 通道数={}",
                platformId, groupIds.size(), channelIds.size());
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
