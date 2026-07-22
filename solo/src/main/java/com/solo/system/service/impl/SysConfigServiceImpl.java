package com.solo.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.system.constant.SystemConstants;
import com.solo.system.entity.SysConfig;
import com.solo.system.mapper.SysConfigMapper;
import com.solo.system.request.ConfigPageRequest;
import com.solo.system.request.ConfigRequest;
import com.solo.system.service.SysConfigService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统参数服务实现类
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public Page<SysConfig> pageQuery(ConfigPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysConfig> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (req.getConfigName() != null && !req.getConfigName().isBlank()) {
            wrapper.like(SysConfig::getConfigName, req.getConfigName());
        }
        if (req.getConfigKey() != null && !req.getConfigKey().isBlank()) {
            wrapper.like(SysConfig::getConfigKey, req.getConfigKey());
        }
        wrapper.orderByDesc(SysConfig::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createConfig(ConfigRequest request) {
        SysConfig existing = getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, request.getConfigKey()));
        if (existing != null) {
            throw new ServiceException("参数键已存在");
        }
        SysConfig config = new SysConfig();
        config.setConfigName(request.getConfigName());
        config.setConfigKey(request.getConfigKey());
        config.setConfigValue(request.getConfigValue());
        config.setRemark(request.getRemark());
        save(config);
    }

    @Override
    public void updateConfig(Long id, ConfigRequest request) {
        SysConfig config = getById(id);
        if (config == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "参数不存在");
        }
        if (request.getConfigName() != null) config.setConfigName(request.getConfigName());
        if (request.getConfigValue() != null) config.setConfigValue(request.getConfigValue());
        if (request.getRemark() != null) config.setRemark(request.getRemark());
        updateById(config);
    }

    @Override
    public void deleteConfig(Long id) {
        SysConfig config = getById(id);
        if (config == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "参数不存在");
        }
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        removeByIds(ids);
    }
}
