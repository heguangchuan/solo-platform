package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.*;
import com.solo.gb28181.event.catalog.ShareChangedEvent;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformGroupMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.utils.GbCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>{@code gb_platform_share} <b>只存通道</b>（{@code gb_device_channel} 中 parental=0 的行）。
 * 分组是本平台内部的目录组织，当前不推送给上级（Catalog XML 只发通道 Item）。</p>
 * <p>share 表与推送分组（{@code gb_platform_group}）关联：UI 强制先在弹窗左侧选中分组才能挂载。
 * 同一通道在同一平台下只能挂载一次（{@code uk_platform_resource}），换组要先卸载旧组。
 * Catalog 响应 / INVITE 反查走全量（不分组）路径——上级不关心"挂在哪个组里"。</p>
 * <p>撞码检测是<b>平台级</b>的：上级看到扁平 DeviceID 空间，跨组同码也会让上级 INVITE 认错通道。</p>
 * <p>{@link #shareTree} 仍返回三级树（设备 → 分组 → 通道）供旧勾选 UI 用；前端契约保留 g_/c_ 前缀。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogShareServiceImpl implements CatalogShareService {

    private static final String LOG_TAG = "通道推送";

    /** 前端 el-tree node-key 前缀：分组 / 通道，避免主键碰撞 */
    private static final String GROUP_PREFIX = "g_";
    private static final String CHANNEL_PREFIX = "c_";
    /** 设备节点前缀（仅作枝干展示，不可勾选共享） */
    private static final String DEVICE_PREFIX = "d_";

    private final GbDeviceChannelMapper channelMapper;
    private final GbDeviceMapper deviceMapper;
    private final GbPlatformShareMapper shareMapper;
    private final GbPlatformGroupMapper groupMapper;
    private final GbCodeGenerator gbCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final GbParentPlatformService gbParentPlatformService;
    private final PlatformCommander platformCommander;
    private final Gb28181Properties properties;

    @Override
    public List<GbShareTreeNodeResponse> shareTree() {
        // 1) 拉所有 catalog item（分组 parental=1 + 通道 parental=0），按 device_id 聚合
        List<GbDeviceChannel> allItems = channelMapper.selectList(
                new LambdaQueryWrapper<GbDeviceChannel>()
                        .orderByDesc(GbDeviceChannel::getParental)
                        .orderByAsc(GbDeviceChannel::getChannelId));
        if (allItems.isEmpty()) {
            return new ArrayList<>();
        }

        // 2) 拉设备表（作枝干标签）
        Map<String, GbDevice> deviceByGbId = deviceMapper.selectList(
                        new LambdaQueryWrapper<GbDevice>().orderByAsc(GbDevice::getName)).stream()
                .collect(Collectors.toMap(GbDevice::getDeviceId, d -> d, (a, b) -> a, LinkedHashMap::new));

        // 3) 按 device_id 分桶
        Map<String, List<GbDeviceChannel>> itemsByDevice = new LinkedHashMap<>();
        for (GbDeviceChannel it : allItems) {
            if (it.getDeviceId() == null) {
                continue;
            }
            itemsByDevice.computeIfAbsent(it.getDeviceId(), k -> new ArrayList<>()).add(it);
        }

        // 4) 组装
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
        // 全量（不分组）：本表只存通道，节点 id 统一 c_ 前缀
        List<GbPlatformShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId));

        GbPlatformSharedResponse resp = new GbPlatformSharedResponse();
        List<String> nodeIds = shares.stream()
                .map(s -> CHANNEL_PREFIX + s.getResourceId())
                .collect(Collectors.toList());
        resp.setChannelIds(nodeIds);
        return resp;
    }

    /**
     * @deprecated 旧 catalog 树"全量覆盖勾选"入口。{@code gb_platform_share.group_id} 已改为 NOT NULL
     *             （必须挂在某个推送分组下），本方法没有 groupId 参数无法正确入库，故直接拒绝。
     *             管理台请走按组接口：{@link #addShares} / {@link #removeShare} / {@link #clearShares}。
     */
    @Deprecated
    @Override
    public void saveShared(Long platformId, GbPlatformShareSaveRequest request) {
        throw new com.solo.common.ServiceException(
                "该接口已废弃：共享通道必须挂载到具体的推送分组下，请改用按分组的添加/卸载接口");
    }

    @Override
    public List<GbDeviceChannel> loadSharedCatalogItems(Long platformId) {
        // 推送给上级的目录结构 = 推送分组树（gb_platform_group，parental=1）+ 挂载通道（parental=0）。
        // 分组 DeviceID 用 group.code（215/216），通道 parentId 指向所在分组 code。只推送"有通道的分组
        // 及其祖先分组"——空分组（已清空挂载）不推给上级，避免上级残留空目录节点。
        List<GbPlatformShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId));

        List<GbPlatformGroup> groups = groupMapper.selectList(
                new LambdaQueryWrapper<GbPlatformGroup>()
                        .eq(GbPlatformGroup::getPlatformId, platformId)
                        .orderByAsc(GbPlatformGroup::getId));
        Map<Long, GbPlatformGroup> groupById = groups.stream()
                .collect(Collectors.toMap(GbPlatformGroup::getId, g -> g, (a, b) -> a));
        Map<Long, String> codeByGroupId = groupById.values().stream()
                .collect(Collectors.toMap(GbPlatformGroup::getId, GbPlatformGroup::getCode, (a, b) -> a));

        // 有通道的分组 + 它们的祖先分组（避免通道 ParentID 指向上级没见过的分组）
        Set<Long> neededGroupIds = new HashSet<>();
        for (GbPlatformShare s : shares) {
            Long gid = s.getGroupId();
            while (gid != null && gid != 0L && groupById.containsKey(gid)) {
                if (!neededGroupIds.add(gid)) {
                    break;
                }
                gid = groupById.get(gid).getParentId();
            }
        }

        List<GbDeviceChannel> result = new ArrayList<>();
        for (GbPlatformGroup g : groups) {
            if (!neededGroupIds.contains(g.getId())) {
                continue;
            }
            GbDeviceChannel node = new GbDeviceChannel();
            node.setChannelId(g.getCode());
            node.setName(g.getName());
            node.setParental(1);
            node.setStatus("ON");
            String parentCode = g.getParentId() != null && g.getParentId() != 0L
                    ? codeByGroupId.getOrDefault(g.getParentId(), "0") : "0";
            node.setParentId(parentCode);
            result.add(node);
        }

        if (!shares.isEmpty()) {
            List<Long> resourceIds = shares.stream()
                    .map(GbPlatformShare::getResourceId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, GbDeviceChannel> channelById = resourceIds.isEmpty()
                    ? Map.of()
                    : channelMapper.selectBatchIds(resourceIds).stream()
                            .collect(Collectors.toMap(GbDeviceChannel::getId, c -> c, (a, b) -> a));
            for (GbPlatformShare s : shares) {
                GbDeviceChannel c = channelById.get(s.getResourceId());
                if (c == null) {
                    continue;
                }
                c.setParentId(codeByGroupId.getOrDefault(s.getGroupId(), "0"));
                c.setExposedCode(s.getExposedCode());
                result.add(c);
            }
        }

        // 分组在前、通道在后，同类型按 channelId 升序，保证跨片顺序稳定
        result.sort(Comparator
                .comparingInt((GbDeviceChannel c) -> c.getParental() != null && c.getParental() == 1 ? 0 : 1)
                .thenComparing(GbDeviceChannel::getChannelId, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @Override
    public List<GbDeviceChannel> loadSharedChannels(Long platformId) {
        return shareMapper.selectSharedChannels(platformId);
    }

    @Override
    public void pushCatalog(Long platformId) {
        GbParentPlatform platform = gbParentPlatformService.getById(platformId);
        if (platform == null || !Integer.valueOf(1).equals(platform.getEnabled())) {
            throw new ServiceException("上级平台不存在或已禁用");
        }
        List<GbDeviceChannel> items = loadSharedCatalogItems(platformId);
        platformCommander.sendNotifyCatalogList(platform, properties.getId(), items,
                String.valueOf(PlatformCommander.nextSn()));
        log.info("[{}] 主动推送 platformId={} 目录条目={}",
                LOG_TAG, platformId, items.size());
    }

    // ==================== 按组增删（推送目录弹窗用） ====================

    @Override
    public IPage<GbDeviceChannel> pageSharedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword) {
        requireOwnedGroup(platformId, groupId);
        return shareMapper.selectSharedByGroupPage(page, platformId, groupId, normalize(keyword));
    }

    @Override
    public IPage<GbDeviceChannel> pageUnmountedByGroup(Long platformId, Long groupId, Page<GbDeviceChannel> page, String keyword) {
        requireOwnedGroup(platformId, groupId);
        return shareMapper.selectUnmountedByGroupPage(page, platformId, groupId, normalize(keyword));
    }

    @Override
    @Transactional
    public int addShares(Long platformId, Long groupId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return 0;
        }
        GbPlatformGroup group = requireOwnedGroup(platformId, groupId);

        // 1) 拉资源实体
        List<GbDeviceChannel> resources = channelMapper.selectBatchIds(resourceIds);
        if (resources.isEmpty()) {
            return 0;
        }
        Map<Long, GbDeviceChannel> resourceById = resources.stream()
                .collect(Collectors.toMap(GbDeviceChannel::getId, c -> c, (a, b) -> a));

        // 2) 查该平台所有 share（本批若有重复 / 已挂其他组会一起出现 → 全部幂等跳过）
        //    uk_platform_resource (platform_id, resource_id) 唯一：同一资源在本平台任何组都已挂则
        //    不能再加；同组重复添加也是幂等跳过。
        List<GbPlatformShare> existing = shareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId)
                        .in(GbPlatformShare::getResourceId, resourceIds));
        // 记录"已挂本组"与"已挂其他组"——后者要让用户知道在哪组，不能静默跳过
        Set<Long> alreadySharedInGroup = new HashSet<>();
        Set<Long> alreadySharedInOtherGroup = new HashSet<>();
        for (GbPlatformShare s : existing) {
            if (groupId.equals(s.getGroupId())) {
                alreadySharedInGroup.add(s.getResourceId());
            } else {
                alreadySharedInOtherGroup.add(s.getResourceId());
            }
        }
        if (!alreadySharedInOtherGroup.isEmpty()) {
            // 找一下被占用的组名，给出更友好的错误信息
            List<GbPlatformGroup> occupiedGroups = groupMapper.selectBatchIds(
                    shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                                    .eq(GbPlatformShare::getPlatformId, platformId)
                                    .in(GbPlatformShare::getResourceId, alreadySharedInOtherGroup)
                                    .select(GbPlatformShare::getGroupId))
                            .stream().map(GbPlatformShare::getGroupId).distinct().toList());
            String groupNames = occupiedGroups.stream()
                    .map(g -> "「" + g.getName() + "」")
                    .collect(Collectors.joining("、"));
            throw new com.solo.common.ServiceException(
                    "所选资源中 " + alreadySharedInOtherGroup.size() + " 项已挂载到其他分组（" + groupNames + "），请先在对应分组中卸载后再添加到「" + group.getName() + "」");
        }

        // 3) 查"该平台已有 share"的 channelId 用于撞码检测。
        //    ！！必须按 platform_id 全量查，不能限定 group_id ！！
        //    撞码是平台级命名空间的问题：上级平台看到的是扁平的 DeviceID 空间，A 组的 34020...0001
        //    和 B 组另一台设备的同码通道推上去就是两个同码 Item，上级 INVITE 必然认错通道。
        //    分组只是本平台 admin UI 的组织结构，对上级不可见（Catalog XML 靠 channel.parent_id 表达层级）。
        Set<String> historyChannelIds = new HashSet<>();
        List<Long> existingChannelResourceIds = shareMapper.selectList(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId)
                        .select(GbPlatformShare::getResourceId))
                .stream()
                .map(GbPlatformShare::getResourceId)
                .filter(Objects::nonNull)
                .toList();
        if (!existingChannelResourceIds.isEmpty()) {
            channelMapper.selectList(
                            new LambdaQueryWrapper<GbDeviceChannel>()
                                    .in(GbDeviceChannel::getId, existingChannelResourceIds)
                                    .select(GbDeviceChannel::getChannelId))
                    .forEach(c -> {
                        if (c.getChannelId() != null) historyChannelIds.add(c.getChannelId());
                    });
        }

        // 4) 撞码检测：本批按 channelId 分组，size>1 自身撞码；任一与 historyChannelIds 相交视为撞码
        Map<String, List<Long>> conflictByChannelId = new HashMap<>();
        for (GbDeviceChannel c : resources) {
            if (c.getChannelId() == null) {
                continue;
            }
            if (alreadySharedInGroup.contains(c.getId())) {
                continue;
            }
            conflictByChannelId.computeIfAbsent(c.getChannelId(), k -> new ArrayList<>()).add(c.getId());
        }
        for (Map.Entry<String, List<Long>> e : new ArrayList<>(conflictByChannelId.entrySet())) {
            if (historyChannelIds.contains(e.getKey()) || e.getValue().size() > 1) {
                conflictByChannelId.put(e.getKey(), e.getValue());
            } else {
                conflictByChannelId.remove(e.getKey());
            }
        }
        Set<Long> needExposed = conflictByChannelId.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        // 4.5) 查"本批通道是否已分码"（按 resource_id 跨平台捞旧码，ADR-020 决策 4 / ADR-026 决策 6
        //      "已分码不收回"）。同一通道对所有上级复用同一 exposedCode，上级已缓存的旧 DeviceID 不失效。
        //      虽然 nextExposedChannelCode 是纯函数（同输入同输出），但历史码可能是老 max+1 版本分配的，
        //      不捞旧码会漂移。
        Map<Long, String> exposedByResource = new HashMap<>();
        if (!needExposed.isEmpty()) {
            shareMapper.selectList(new LambdaQueryWrapper<GbPlatformShare>()
                            .isNotNull(GbPlatformShare::getExposedCode)
                            .in(GbPlatformShare::getResourceId, needExposed)
                            .select(GbPlatformShare::getResourceId, GbPlatformShare::getExposedCode))
                    .forEach(s -> exposedByResource.putIfAbsent(s.getResourceId(), s.getExposedCode()));
        }

        // 5) 批量插（跳过已挂本组的）
        List<GbPlatformShare> toInsert = new ArrayList<>();
        for (Long rid : resourceIds) {
            if (alreadySharedInGroup.contains(rid)) {
                continue;
            }
            GbDeviceChannel res = resourceById.get(rid);
            if (res == null) {
                continue;
            }
            String exposed = null;
            if (needExposed.contains(rid)) {
                // 优先复用旧码（跨平台稳定），无旧码才按 (deviceId, channelId) 哈希派生
                exposed = exposedByResource.get(rid);
                if (exposed == null) {
                    exposed = gbCodeGenerator.nextExposedChannelCode(res.getDeviceId(), res.getChannelId());
                }
            }
            toInsert.add(newShare(platformId, groupId, rid, exposed));
        }
        if (!toInsert.isEmpty()) {
            Db.saveBatch(toInsert);
            eventPublisher.publishEvent(new ShareChangedEvent(this, platformId));
        }
        log.info("[{}] 批量挂载 platformId={} groupId={} groupName={} 输入={} 新增={} 撞码={}（复用旧码={}） 已挂本组跳过={}",
                LOG_TAG, platformId, groupId, group.getName(), resourceIds.size(), toInsert.size(),
                needExposed.size(), exposedByResource.size(), alreadySharedInGroup.size());
        return toInsert.size();
    }

    @Override
    @Transactional
    public boolean removeShare(Long platformId, Long groupId, Long resourceId) {
        requireOwnedGroup(platformId, groupId);
        int n = shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId)
                .eq(GbPlatformShare::getGroupId, groupId)
                .eq(GbPlatformShare::getResourceId, resourceId));
        if (n > 0) {
            log.info("[{}] 卸载 platformId={} groupId={} resourceId={}", LOG_TAG, platformId, groupId, resourceId);
            eventPublisher.publishEvent(new ShareChangedEvent(this, platformId));
        }
        return n > 0;
    }

    @Override
    @Transactional
    public int clearShares(Long platformId, Long groupId) {
        GbPlatformGroup group = requireOwnedGroup(platformId, groupId);
        int n = shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId)
                .eq(GbPlatformShare::getGroupId, groupId));
        if (n > 0) {
            eventPublisher.publishEvent(new ShareChangedEvent(this, platformId));
        }
        log.info("[{}] 清除组内挂载 platformId={} groupId={} groupName={} 删除条数={}",
                LOG_TAG, platformId, groupId, group.getName(), n);
        return n;
    }

    @Override
    @Transactional
    public int clearAllSharesByPlatform(Long platformId) {
        int n = shareMapper.delete(new LambdaQueryWrapper<GbPlatformShare>()
                .eq(GbPlatformShare::getPlatformId, platformId));
        if (n > 0) {
            eventPublisher.publishEvent(new ShareChangedEvent(this, platformId));
        }
        log.info("[{}] 清除该平台全部分组挂载 platformId={} 删除条数={}", LOG_TAG, platformId, n);
        return n;
    }

    @Override
    @Transactional
    public int addAllUnmounted(Long platformId, Long groupId) {
        // 列出"未挂载"（不在该平台任何 share 里）的所有通道 id——同组/其他组已挂的不算
        List<Long> allUnmounted = shareMapper.selectUnmountedByGroupAllIds(platformId, groupId);
        if (allUnmounted.isEmpty()) {
            log.info("[{}] 一键添加：无未挂载通道 platformId={} groupId={}", LOG_TAG, platformId, groupId);
            return 0;
        }
        int n = addShares(platformId, groupId, allUnmounted);
        log.info("[{}] 一键添加完成 platformId={} groupId={} 候选={} 新增={}",
                LOG_TAG, platformId, groupId, allUnmounted.size(), n);
        return n;
    }

    // ==================== helpers ====================

    private static String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    /**
     * 校验分组存在且属于该 platformId。抛 ServiceException 让全局异常处理统一翻译。
     */
    private GbPlatformGroup requireOwnedGroup(Long platformId, Long groupId) {
        GbPlatformGroup g = groupMapper.selectById(groupId);
        if (g == null) {
            throw new com.solo.common.ServiceException("分组不存在");
        }
        if (!platformId.equals(g.getPlatformId())) {
            throw new com.solo.common.ServiceException("分组不属于该上级平台");
        }
        return g;
    }

    private static GbPlatformShare newShare(Long platformId, Long groupId, Long resourceId, String exposedCode) {
        GbPlatformShare row = new GbPlatformShare();
        row.setPlatformId(platformId);
        row.setGroupId(groupId);
        row.setResourceId(resourceId);
        row.setExposedCode(exposedCode);
        return row;
    }
}
