package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.mapper.CatalogItemMapper;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbGroupMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.CatalogItem;
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
 * 目录共享服务实现
 * <p>共享关系统一存 {@code gb_platform_share}（type 区分 group / channel）；可共享资源树由
 * {@link CatalogItemMapper} 的统一读模型（{@link CatalogItem}）组装，按 20 位国标码（parentCode）建层级。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogShareServiceImpl implements CatalogShareService {

    /** 前端 el-tree node-key 前缀：分组 / 通道，避免分组主键与通道主键碰撞 */
    private static final String GROUP_PREFIX = "g_";
    private static final String CHANNEL_PREFIX = "c_";

    private final GbGroupMapper groupMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final CatalogItemMapper catalogItemMapper;
    private final GbPlatformShareMapper shareMapper;
    private final GbCodeGenerator gbCodeGenerator;

    @Override
    public List<GbShareTreeNodeResponse> shareTree() {
        List<CatalogItem> items = catalogItemMapper.selectAll();

        // 1. 分组节点：按自身国标码(code)建索引（保持出现顺序），children 用可变 list 以便挂通道/子分组
        Map<String, GbShareTreeNodeResponse> groupByCode = new LinkedHashMap<>();
        List<CatalogItem> groups = new ArrayList<>();
        List<CatalogItem> channels = new ArrayList<>();
        for (CatalogItem it : items) {
            if (GbPlatformShare.TYPE_GROUP.equals(it.getType())) {
                groups.add(it);
                GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
                node.setId(GROUP_PREFIX + it.getId());
                node.setLabel(it.getName());
                node.setType("GROUP");
                node.setChildren(new ArrayList<>());
                groupByCode.put(it.getCode(), node);
            } else {
                channels.add(it);
            }
        }

        // 2. 通道作为叶子挂到所属分组（parentCode = 所属分组国标码）
        for (CatalogItem ch : channels) {
            GbShareTreeNodeResponse parent = groupByCode.get(ch.getParentCode());
            if (parent == null) {
                // parentCode 悬挂（分组被删且未回落）——跳过，不污染树
                continue;
            }
            GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
            node.setId(CHANNEL_PREFIX + ch.getId());
            node.setLabel(ch.getName() + " (" + ch.getCode() + ")");
            node.setType("CHANNEL");
            parent.getChildren().add(node);
        }

        // 3. 组装分组层级：parentCode 命中某分组 → 挂其下；为 null / 不在集合中 → 根节点
        List<GbShareTreeNodeResponse> roots = new ArrayList<>();
        for (CatalogItem g : groups) {
            GbShareTreeNodeResponse self = groupByCode.get(g.getCode());
            GbShareTreeNodeResponse parent = g.getParentCode() == null ? null : groupByCode.get(g.getParentCode());
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
        resp.setGroupIds(shares.stream()
                .filter(s -> GbPlatformShare.TYPE_GROUP.equals(s.getType()))
                .map(s -> GROUP_PREFIX + s.getResourceId())
                .collect(Collectors.toList()));
        resp.setChannelIds(shares.stream()
                .filter(s -> GbPlatformShare.TYPE_CHANNEL.equals(s.getType()))
                .map(s -> CHANNEL_PREFIX + s.getResourceId())
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    @Transactional
    public void saveShared(Long platformId, GbPlatformShareSaveRequest request) {
        // 通道 exposed_code 须在删除前解析：全量覆盖会先删本平台旧行，若本平台是该通道唯一分享方，
        // 删后就取不到，会重新生成不同码 → 破坏上级已缓存的旧码。
        // 故先按 resource_id 一次性捞已分配的 exposed_code（任一平台行），保证同一通道 exposed_code 恒定。
        List<Long> channelIds = parseIds(request.getChannelIds(), CHANNEL_PREFIX);
        Map<Long, String> exposedByChannel = new HashMap<>();
        if (!channelIds.isEmpty()) {
            shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                            .eq(GbPlatformShare::getType, GbPlatformShare.TYPE_CHANNEL)
                            .in(GbPlatformShare::getResourceId, channelIds)
                            .isNotNull(GbPlatformShare::getExposedCode)
                            .select(GbPlatformShare::getResourceId, GbPlatformShare::getExposedCode))
                    .forEach(s -> exposedByChannel.putIfAbsent(s.getResourceId(), s.getExposedCode()));
        }

        // 全量覆盖：先删该平台旧的共享关系
        shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId));

        List<GbPlatformShare> toInsert = new ArrayList<>();

        // 分组：解析 g_ 前缀主键，过滤已不存在的分组后入库
        List<Long> groupIds = parseIds(request.getGroupIds(), GROUP_PREFIX);
        if (!groupIds.isEmpty()) {
            Set<Long> existing = groupMapper.selectList(
                            new LambdaQueryWrapper<GbGroup>().in(GbGroup::getId, groupIds))
                    .stream()
                    .map(GbGroup::getId)
                    .collect(Collectors.toSet());
            for (Long gid : groupIds) {
                if (!existing.contains(gid)) continue; // 分组已不存在，跳过
                toInsert.add(newShare(platformId, gid, GbPlatformShare.TYPE_GROUP, null));
            }
        }

        // 通道：过滤已不存在的通道后入库；每个通道分配/复用 exposed_code（对上级暴露的唯一码）
        if (!channelIds.isEmpty()) {
            Set<Long> existing = channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>().in(GbDeviceChannel::getId, channelIds))
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
                toInsert.add(newShare(platformId, cid, GbPlatformShare.TYPE_CHANNEL, exposedByChannel.get(cid)));
            }
        }

        if (!toInsert.isEmpty()) {
            Db.saveBatch(toInsert);
        }

        log.info("[通道推送] 保存共享: platformId={} 分组数={} 通道数={}",
                platformId, groupIds.size(), channelIds.size());
    }

    @Override
    public List<GbGroup> loadSharedGroups(Long platformId) {
        return shareMapper.selectSharedGroups(platformId);
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

    private static GbPlatformShare newShare(Long platformId, Long resourceId, String type, String exposedCode) {
        GbPlatformShare row = new GbPlatformShare();
        row.setPlatformId(platformId);
        row.setResourceId(resourceId);
        row.setType(type);
        row.setExposedCode(exposedCode);
        return row;
    }
}
