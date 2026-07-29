package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.utils.TreeUtils;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.mapper.GbGroupMapper;
import com.solo.gb28181.request.GbGroupPageRequest;
import com.solo.gb28181.response.GbGroupResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbGroupService;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 国标资源目录分组服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbGroupServiceImpl extends ServiceImpl<GbGroupMapper, GbGroup> implements GbGroupService {

    private final Gb28181Properties properties;
    private final GbDeviceChannelService channelService;

    /**
     * entity → VO：透传所有 entity 字段，视图字段留空由后续 markDefault / buildTree 填
     */
    private GbGroupResponse toResponse(GbGroup src) {
        GbGroupResponse vo = new GbGroupResponse();
        BeanUtils.copyProperties(src, vo);
        return vo;
    }

    /**
     * 打上 defaultGroup 标识（主键等于 gb28181.default-group.id），供前端禁用删除/限制编辑
     */
    private void markDefault(List<GbGroupResponse> groups) {
        if (groups == null || groups.isEmpty()) return;
        Long defaultId = properties.getDefaultGroup().getId();
        groups.forEach(g -> g.setDefaultGroup(defaultId.equals(g.getId())));
    }

    @Override
    public Page<GbGroupResponse> pageQuery(GbGroupPageRequest request) {
        LambdaQueryWrapper<GbGroup> wrapper = new LambdaQueryWrapper<>();
        if (request.getGroupId() != null && !request.getGroupId().isBlank()) {
            wrapper.like(GbGroup::getGroupId, request.getGroupId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbGroup::getName, request.getName());
        }
        if (request.getParentId() != null) {
            wrapper.eq(GbGroup::getParentId, request.getParentId());
        }
        if (request.getPlatformId() != null && !request.getPlatformId().isBlank()) {
            wrapper.eq(GbGroup::getPlatformId, request.getPlatformId());
        }
        wrapper.orderByAsc(GbGroup::getSort).orderByDesc(GbGroup::getId).orderByDesc(GbGroup::getCreateTime);
        Page<GbGroup> raw = page(new Page<>(request.getPage(), request.getPageSize()), wrapper);

        List<GbGroupResponse> rows = new ArrayList<>(raw.getRecords().size());
        raw.getRecords().forEach(g -> rows.add(toResponse(g)));
        markDefault(rows);

        Page<GbGroupResponse> resp = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        resp.setRecords(rows);
        return resp;
    }

    @Override
    public List<GbGroupResponse> tree() {
        List<GbGroup> all = lambdaQuery()
                .orderByAsc(GbGroup::getSort)
                .orderByAsc(GbGroup::getId)
                .list();
        List<GbGroupResponse> rows = new ArrayList<>(all.size());
        all.forEach(g -> rows.add(toResponse(g)));
        // 扁平列表先打标，节点引用同一份，组装成树后自然带上
        markDefault(rows);
        // parentId 不指向列表中任一 id 的节点即为根（根节点 parentId=0）
        return TreeUtils.buildTree(rows, GbGroupResponse::getParentId, GbGroupResponse::getId, GbGroupResponse::setChildren);
    }

    @Override
    public void createGroup(GbGroup group) {
        validateGroupId(group.getGroupId());
        if (group.getName() == null || group.getName().isBlank()) {
            throw new ServiceException("分组名称不能为空");
        }
        boolean dupGb = lambdaQuery().eq(GbGroup::getGroupId, group.getGroupId()).exists();
        if (dupGb) {
            throw new ServiceException("分组国标ID已存在：" + group.getGroupId());
        }
        // 父节点校验：未指定则挂根；指定则父节点必须存在
        if (group.getParentId() == null) {
            group.setParentId(0L);
        } else if (!Long.valueOf(0L).equals(group.getParentId()) && getById(group.getParentId()) == null) {
            throw new ServiceException("父级分组不存在：" + group.getParentId());
        }
        // id 由 MyBatis-Plus 雪花算法生成（默认分组的 0000 由 Bootstrap 显式写入）
        group.setId(null);
        group.setPlatformId(group.getPlatformId() == null || group.getPlatformId().isBlank()
                ? "0000" : group.getPlatformId());
        group.setSort(group.getSort() == null ? 0 : group.getSort());
        save(group);
        log.info("[资源目录] 新增分组 id={} groupId={} name={}",
                group.getId(), group.getGroupId(), group.getName());
    }

    @Override
    public void updateGroup(GbGroup group) {
        if (group.getId() == null) {
            throw new ServiceException("分组ID不能为空");
        }
        GbGroup exist = getById(group.getId());
        if (exist == null) {
            throw new ServiceException("分组不存在");
        }
        // 默认分组是通道归属兜底，仅允许改名称/排序，不可改国标ID/父节点
        if (properties.getDefaultGroup().getId().equals(exist.getId())) {
            if (group.getName() == null || group.getName().isBlank()) {
                throw new ServiceException("分组名称不能为空");
            }
            exist.setName(group.getName());
            exist.setSort(group.getSort() == null ? exist.getSort() : group.getSort());
            updateById(exist);
            log.info("[资源目录] 更新默认分组 id={} name={} sort={}", exist.getId(), exist.getName(), exist.getSort());
            return;
        }
        // 国标ID可改，但仍须满足 20 位且第 11-13 位为 215/216
        if (group.getGroupId() != null && !group.getGroupId().equals(exist.getGroupId())) {
            validateGroupId(group.getGroupId());
            boolean dupGb = lambdaQuery()
                    .eq(GbGroup::getGroupId, group.getGroupId())
                    .ne(GbGroup::getId, exist.getId())
                    .exists();
            if (dupGb) {
                throw new ServiceException("分组国标ID已存在：" + group.getGroupId());
            }
            exist.setGroupId(group.getGroupId());
        }
        // id 不可改（会导致已挂通道悬挂），只更新可编辑字段
        exist.setName(group.getName());
        exist.setParentId(group.getParentId());
        exist.setSort(group.getSort() == null ? exist.getSort() : group.getSort());
        updateById(exist);
        log.info("[资源目录] 更新分组 id={} name={}", exist.getId(), exist.getName());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        GbGroup group = getById(id);
        if (group == null) {
            return;
        }
        Long defaultId = properties.getDefaultGroup().getId();
        if (defaultId.equals(group.getId())) {
            throw new ServiceException("默认分组不可删除");
        }
        // 挂在该分组下的通道回落到默认分组，避免 group_id 悬挂
        channelService.lambdaUpdate()
                .eq(GbDeviceChannel::getGroupId, group.getId())
                .set(GbDeviceChannel::getGroupId, defaultId)
                .update();
        // 该分组下的子节点 parentId 回落到根节点
        lambdaUpdate()
                .eq(GbGroup::getParentId, group.getId())
                .set(GbGroup::getParentId, 0L)
                .update();
        removeById(id);
        log.info("[资源目录] 删除分组 id={}，通道已回落默认分组 defaultGroup={}，子节点已回落根节点",
                group.getId(), defaultId);
    }

    /**
     * 校验 20 位国标 ID：必须 20 位数字，且第 11-13 位为 215（业务分组）或 216（虚拟组织）。
     */
    private void validateGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new ServiceException("分组国标ID不能为空");
        }
        if (groupId.length() != 20 || !groupId.chars().allMatch(Character::isDigit)) {
            throw new ServiceException("分组国标ID必须是20位数字");
        }
        int typeCode = SipUtils.getTypeCodeFromGbCode(groupId);
        if (typeCode != 215 && typeCode != 216) {
            throw new ServiceException("分组国标ID第11-13位必须是 215（业务分组）或 216（虚拟组织）");
        }
    }
}
