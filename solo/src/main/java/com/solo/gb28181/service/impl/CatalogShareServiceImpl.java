package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.entity.GbPlatformCatalog;
import com.solo.gb28181.entity.GbPlatformGbChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbGroupMapper;
import com.solo.gb28181.mapper.GbPlatformCatalogMapper;
import com.solo.gb28181.mapper.GbPlatformGbChannelMapper;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;
import com.solo.gb28181.service.CatalogShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目录共享服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogShareServiceImpl implements CatalogShareService {

    private static final String GROUP_PREFIX = "g_";
    private static final String CHANNEL_PREFIX = "c_";

    private final GbGroupMapper groupMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbPlatformCatalogMapper catalogMapper;
    private final GbPlatformGbChannelMapper channelShareMapper;

    @Override
    public List<GbShareTreeNodeResponse> shareTree() {
        // 1. 分组节点，主键 -> 节点（保持 sort 顺序，children 用可变 list 以便挂通道）
        List<GbGroup> groups = groupMapper.selectList(
                new LambdaQueryWrapper<GbGroup>()
                        .orderByAsc(GbGroup::getSort)
                        .orderByAsc(GbGroup::getId));
        Map<String, GbShareTreeNodeResponse> groupNodes = new LinkedHashMap<>(groups.size());
        for (GbGroup g : groups) {
            GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
            node.setId(GROUP_PREFIX + g.getId());
            node.setLabel(g.getName());
            node.setType("GROUP");
            node.setChildren(new ArrayList<>());
            groupNodes.put(g.getId(), node);
        }

        // 2. 通道作为叶子挂到所属分组（group_id 引用 gb_group.id 主键）
        List<GbDeviceChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .orderByAsc(GbDeviceChannel::getChannelId));
        for (GbDeviceChannel ch : channels) {
            GbShareTreeNodeResponse parent = groupNodes.get(ch.getGroupId());
            if (parent == null) {
                // group_id 悬挂（分组被删且未回落）——跳过，不污染树
                continue;
            }
            GbShareTreeNodeResponse node = new GbShareTreeNodeResponse();
            node.setId(CHANNEL_PREFIX + ch.getId());
            String name = (ch.getName() != null && !ch.getName().isBlank()) ? ch.getName() : ch.getReportName();
            node.setLabel(name + " (" + ch.getChannelId() + ")");
            node.setType("CHANNEL");
            parent.getChildren().add(node);
        }

        // 3. 组装分组层级：parentId 指向 gb_group.id，根节点 parentId 不在集合中（通常为 0）
        List<GbShareTreeNodeResponse> roots = new ArrayList<>();
        for (GbGroup g : groups) {
            GbShareTreeNodeResponse self = groupNodes.get(g.getId());
            GbShareTreeNodeResponse parent = groupNodes.get(g.getParentId());
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
        List<GbPlatformCatalog> catalogs = catalogMapper.selectList(
                new LambdaQueryWrapper<GbPlatformCatalog>()
                        .eq(GbPlatformCatalog::getPlatformId, platformId));
        List<GbPlatformGbChannel> chs = channelShareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformGbChannel>()
                        .eq(GbPlatformGbChannel::getPlatformId, platformId));

        GbPlatformSharedResponse resp = new GbPlatformSharedResponse();
        resp.setGroupIds(catalogs.stream()
                .map(c -> GROUP_PREFIX + c.getGroupId())
                .collect(Collectors.toList()));
        resp.setChannelIds(chs.stream()
                .map(c -> CHANNEL_PREFIX + c.getChannelId())
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    @Transactional
    public void saveShared(Long platformId, GbPlatformShareSaveRequest request) {
        // 全量覆盖：先删旧
        catalogMapper.delete(new LambdaQueryWrapper<GbPlatformCatalog>()
                .eq(GbPlatformCatalog::getPlatformId, platformId));
        channelShareMapper.delete(new LambdaQueryWrapper<GbPlatformGbChannel>()
                .eq(GbPlatformGbChannel::getPlatformId, platformId));

        // 分组：解析 g_ 前缀主键，过滤已不存在的分组后入库
        List<String> groupNodeIds = request.getGroupIds() == null ? List.of() : request.getGroupIds();
        List<String> groupIds = groupNodeIds.stream()
                .filter(s -> s != null && s.startsWith(GROUP_PREFIX))
                .map(s -> s.substring(GROUP_PREFIX.length()))
                .distinct()
                .collect(Collectors.toList());
        if (!groupIds.isEmpty()) {
            Set<String> existing = groupMapper.selectList(
                            new LambdaQueryWrapper<GbGroup>().in(GbGroup::getId, groupIds))
                    .stream()
                    .map(GbGroup::getId)
                    .collect(Collectors.toSet());
            for (String gid : groupIds) {
                if (!existing.contains(gid)) {
                    continue; // 分组已不存在，跳过
                }
                GbPlatformCatalog row = new GbPlatformCatalog();
                row.setPlatformId(platformId);
                row.setGroupId(gid);
                catalogMapper.insert(row);
            }
        }

        // 通道：解析 c_ 前缀主键，过滤已不存在的通道后入库
        List<String> channelNodeIds = request.getChannelIds() == null ? List.of() : request.getChannelIds();
        List<Long> channelIds = channelNodeIds.stream()
                .filter(s -> s != null && s.startsWith(CHANNEL_PREFIX))
                .map(s -> s.substring(CHANNEL_PREFIX.length()))
                .distinct()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        if (!channelIds.isEmpty()) {
            Set<Long> existing = channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>().in(GbDeviceChannel::getId, channelIds))
                    .stream()
                    .map(GbDeviceChannel::getId)
                    .collect(Collectors.toSet());
            for (Long cid : channelIds) {
                if (!existing.contains(cid)) {
                    continue; // 通道已不存在，跳过
                }
                GbPlatformGbChannel row = new GbPlatformGbChannel();
                row.setPlatformId(platformId);
                row.setChannelId(cid);
                channelShareMapper.insert(row);
            }
        }

        log.info("[通道推送] 保存共享: platformId={} 分组数={} 通道数={}",
                platformId, groupIds.size(), channelIds.size());
    }
}
