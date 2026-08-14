package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.ServiceException;
import com.solo.common.utils.TreeUtils;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPlatformGroup;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbPlatformGroupMapper;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.request.UnmountedChannelPageRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;
import com.solo.gb28181.service.PlatformGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 上级平台推送目录分组服务实现
 */
@Service
@RequiredArgsConstructor
public class PlatformGroupServiceImpl implements PlatformGroupService {

    private final GbPlatformGroupMapper groupMapper;
    private final GbDeviceChannelMapper channelMapper;

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
    public IPage<GbDeviceChannel> pageUnmountedChannels(Long platformId, UnmountedChannelPageRequest req) {
        // 简化：挂载关系未持久化时，所有 parental=0 的 catalog 通道都是"未挂载"
        LambdaQueryWrapper<GbDeviceChannel> wrapper = new LambdaQueryWrapper<GbDeviceChannel>()
                .eq(GbDeviceChannel::getParental, 0);
        if (StringUtils.hasText(req.getKeyword())) {
            String kw = req.getKeyword().trim();
            wrapper.and(w -> w.like(GbDeviceChannel::getName, kw).or().like(GbDeviceChannel::getChannelId, kw));
        }
        wrapper.orderByAsc(GbDeviceChannel::getDeviceId, GbDeviceChannel::getChannelId);
        return channelMapper.selectPage(
                new Page<>(req.getPage(), req.getPageSize()), wrapper);
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
        GbPlatformGroup existing = getOwnedGroup(platformId, groupId);
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
        getOwnedGroup(platformId, groupId);
        // 拒绝有子节点
        Long childCount = groupMapper.selectCount(new LambdaQueryWrapper<GbPlatformGroup>()
                .eq(GbPlatformGroup::getPlatformId, platformId)
                .eq(GbPlatformGroup::getParentId, groupId));
        if (childCount != null && childCount > 0) {
            throw new ServiceException("该分组下还有子分组，请先删除子分组");
        }
        groupMapper.deleteById(groupId);
    }

    /**
     * 校验分组存在且属于该 platformId
     */
    private GbPlatformGroup getOwnedGroup(Long platformId, Long groupId) {
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
