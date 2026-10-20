package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.solo.common.ServiceException;
import com.solo.common.utils.TreeUtils;
import com.solo.gb28181.entity.GbPlatformGroup;
import com.solo.gb28181.entity.GbPlatformShare;
import com.solo.gb28181.event.catalog.ShareChangedEvent;
import com.solo.gb28181.mapper.GbPlatformGroupMapper;
import com.solo.gb28181.mapper.GbPlatformShareMapper;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;
import com.solo.gb28181.service.PlatformGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 上级平台推送目录分组服务实现（推送树 CRUD；share 表由 CatalogShareService 管理）。
 *
 * <p>2026-08：删分组时联动清理该组下所有 share，避免出现"分组删了但 share 还挂着 group_id"
 * 孤儿数据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformGroupServiceImpl implements PlatformGroupService {

    private static final String LOG_TAG = "推送分组";

    private final GbPlatformGroupMapper groupMapper;
    private final GbPlatformShareMapper shareMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<GbPlatformGroupResponse> listGroupTree(Long platformId) {
        List<GbPlatformGroup> all = groupMapper.selectList(
                new LambdaQueryWrapper<GbPlatformGroup>()
                        .eq(GbPlatformGroup::getPlatformId, platformId)
                        .orderByAsc(GbPlatformGroup::getParentId, GbPlatformGroup::getId));
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        // Entity → VO（不挂 children，由 TreeUtils 在 VO 上构树）
        List<GbPlatformGroupResponse> nodes = all.stream().map(e -> {
            GbPlatformGroupResponse r = new GbPlatformGroupResponse();
            BeanUtils.copyProperties(e, r);
            return r;
        }).toList();
        // 用 TreeUtils 在 VO 上构树，根 = parentId == 0
        return TreeUtils.build(
                nodes,
                GbPlatformGroupResponse::getParentId,
                GbPlatformGroupResponse::getId,
                n -> n.getParentId() != null && n.getParentId() == 0L,
                GbPlatformGroupResponse::setChildren);
    }

    @Override
    @Transactional
    public void createGroup(Long platformId, GbPlatformGroupSaveRequest req) {
        // 父分组（如果非 0）必须属于同一 platformId
        validateParent(platformId, req.getParentId(), null);
        // code 平台内唯一
        Long same = groupMapper.selectCount(new LambdaQueryWrapper<GbPlatformGroup>()
                .eq(GbPlatformGroup::getPlatformId, platformId)
                .eq(GbPlatformGroup::getCode, req.getCode()));
        if (same != null && same > 0) {
            throw new ServiceException("国标编码在该平台下已存在");
        }
        GbPlatformGroup entity = new GbPlatformGroup();
        entity.setName(req.getName());
        entity.setCode(req.getCode());
        entity.setPlatformId(platformId);
        entity.setParentId(req.getParentId());
        groupMapper.insert(entity);
    }

    @Override
    @Transactional
    public void updateGroup(Long platformId, Long groupId, GbPlatformGroupSaveRequest req) {
        GbPlatformGroup existing = requireOwnedGroup(platformId, groupId);
        // 不能把自己设为自己的父级
        if (req.getParentId() != null && req.getParentId().equals(groupId)) {
            throw new ServiceException("不能把分组设为自己的父分组");
        }
        // 父分组校验
        validateParent(platformId, req.getParentId(), groupId);
        // code 唯一（除自己外）
        Long same = groupMapper.selectCount(new LambdaQueryWrapper<GbPlatformGroup>()
                .eq(GbPlatformGroup::getPlatformId, platformId)
                .eq(GbPlatformGroup::getCode, req.getCode())
                .ne(GbPlatformGroup::getId, groupId));
        if (same != null && same > 0) {
            throw new ServiceException("国标编码在该平台下已被其他分组占用");
        }
        existing.setName(req.getName());
        existing.setCode(req.getCode());
        existing.setParentId(req.getParentId());
        groupMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void deleteGroup(Long platformId, Long groupId) {
        requireOwnedGroup(platformId, groupId);
        // 拒绝有子节点
        Long childCount = groupMapper.selectCount(new LambdaQueryWrapper<GbPlatformGroup>()
                .eq(GbPlatformGroup::getPlatformId, platformId)
                .eq(GbPlatformGroup::getParentId, groupId));
        if (childCount != null && childCount > 0) {
            throw new ServiceException("该分组下还有子分组，请先删除子分组");
        }
        // 联动清理该组下所有 share，避免 group_id 孤儿
        int unmounted = shareMapper.delete(
                new LambdaQueryWrapper<GbPlatformShare>()
                        .eq(GbPlatformShare::getPlatformId, platformId)
                        .eq(GbPlatformShare::getGroupId, groupId));
        groupMapper.deleteById(groupId);
        if (unmounted > 0) {
            log.info("[{}] 删分组时联动清理挂载 platformId={} groupId={} unmounted={}",
                    LOG_TAG, platformId, groupId, unmounted);
            eventPublisher.publishEvent(new ShareChangedEvent(this, platformId));
        }
    }

    @Override
    public GbPlatformGroup requireOwnedGroup(Long platformId, Long groupId) {
        GbPlatformGroup g = groupMapper.selectById(groupId);
        if (g == null) {
            throw new ServiceException("分组不存在");
        }
        if (!platformId.equals(g.getPlatformId())) {
            throw new ServiceException("分组不属于该上级平台");
        }
        return g;
    }

    /**
     * 校验父分组（如果非 0）必须存在 + 属于同一 platformId
     */
    private void validateParent(Long platformId, Long parentId, Long excludeGroupId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        GbPlatformGroup parent = groupMapper.selectById(parentId);
        if (parent == null) {
            throw new ServiceException("父分组不存在");
        }
        if (!platformId.equals(parent.getPlatformId())) {
            throw new ServiceException("父分组不属于该上级平台");
        }
        if (excludeGroupId != null && parentId.equals(excludeGroupId)) {
            throw new ServiceException("不能把分组设为自己的父分组");
        }
    }
}
