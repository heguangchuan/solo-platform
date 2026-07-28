package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbParentPlatformMapper;
import com.solo.gb28181.request.GbParentPlatformPageRequest;
import com.solo.gb28181.service.GbParentPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上级平台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbParentPlatformServiceImpl extends ServiceImpl<GbParentPlatformMapper, GbParentPlatform> implements GbParentPlatformService {

    @Override
    public Page<GbParentPlatform> pageQuery(GbParentPlatformPageRequest request) {
        LambdaQueryWrapper<GbParentPlatform> wrapper = new LambdaQueryWrapper<>();
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbParentPlatform::getName, request.getName());
        }
        if (request.getServerGbId() != null && !request.getServerGbId().isBlank()) {
            wrapper.like(GbParentPlatform::getServerGbId, request.getServerGbId());
        }
        if (request.getEnabled() != null) {
            wrapper.eq(GbParentPlatform::getEnabled, request.getEnabled());
        }
        wrapper.orderByDesc(GbParentPlatform::getCreateTime);

        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    public void create(GbParentPlatform entity) {
        if (entity.getServerGbId() == null || entity.getServerGbId().isBlank()) {
            throw new ServiceException("上级平台国标编码不能为空");
        }
        if (entity.getServerGbId().length() != 20) {
            throw new ServiceException("上级平台国标编码必须为20位");
        }
        boolean dup = lambdaQuery().eq(GbParentPlatform::getServerGbId, entity.getServerGbId()).exists();
        if (dup) {
            throw new ServiceException("上级平台国标编码已存在：" + entity.getServerGbId());
        }
        entity.setId(null);
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        // 新增时状态默认离线
        entity.setStatus(0);
        save(entity);
        log.info("[上级平台] 新增配置: serverGbId={} name={}", entity.getServerGbId(), entity.getName());
    }

    @Override
    public void updatePlatform(GbParentPlatform entity) {
        GbParentPlatform exist = getById(entity.getId());
        if (exist == null) {
            throw new ServiceException("上级平台不存在");
        }
        // serverGbId 不可改（关联注册身份基准）
        entity.setServerGbId(exist.getServerGbId());
        updateById(entity);
        log.info("[上级平台] 编辑配置: serverGbId={}", exist.getServerGbId());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        removeByIds(ids);
        log.info("[上级平台] 批量删除: ids={}", ids);
    }
}
