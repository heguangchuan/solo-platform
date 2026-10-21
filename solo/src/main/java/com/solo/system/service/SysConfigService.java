package com.solo.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.system.entity.SysConfig;
import com.solo.system.request.ConfigPageRequest;
import com.solo.system.request.ConfigRequest;

import java.util.List;

public interface SysConfigService extends IService<SysConfig> {

    Page<SysConfig> pageQuery(ConfigPageRequest req);

    void createConfig(ConfigRequest request);

    void updateConfig(Long id, ConfigRequest request);

    void deleteConfig(Long id);

    void deleteBatch(List<Long> ids);
}
