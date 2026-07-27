package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.mapper.GbPlatformMapper;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 下级级联平台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlatformServiceImpl extends ServiceImpl<GbPlatformMapper, GbPlatform> implements GbPlatformService {

    private final GbDeviceService gbDeviceService;

    @Override
    public Page<GbPlatformVO> pageQuery(GbPlatformPageRequest request) {
        LambdaQueryWrapper<GbPlatform> wrapper = new LambdaQueryWrapper<>();
        if (request.getPlatformId() != null && !request.getPlatformId().isBlank()) {
            wrapper.like(GbPlatform::getPlatformId, request.getPlatformId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbPlatform::getName, request.getName());
        }
        if (request.getEnabled() != null) {
            wrapper.eq(GbPlatform::getEnabled, request.getEnabled());
        }
        wrapper.orderByDesc(GbPlatform::getCreateTime);

        Page<GbPlatform> page = page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
        Page<GbPlatformVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private GbPlatformVO toVO(GbPlatform platform) {
        GbPlatformVO vo = new GbPlatformVO();
        vo.setId(platform.getId());
        vo.setPlatformId(platform.getPlatformId());
        vo.setName(platform.getName());
        vo.setPassword(platform.getPassword());
        vo.setEnabled(platform.getEnabled());
        vo.setRemark(platform.getRemark());
        vo.setCreateTime(platform.getCreateTime());
        vo.setUpdateTime(platform.getUpdateTime());

        // 关联 gb_device 回填运行态
        GbDevice device = gbDeviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, platform.getPlatformId())
                .one();
        if (device != null) {
            vo.setStatus(device.getStatus());
            vo.setKeepaliveTime(device.getKeepaliveTime());
            vo.setChannelCount(device.getChannelCount());
        }
        return vo;
    }

    @Override
    public void createByManual(GbPlatform platform) {
        if (platform.getPlatformId() == null || platform.getPlatformId().isBlank()) {
            throw new ServiceException("平台国标ID不能为空");
        }
        if (platform.getPlatformId().length() != 20) {
            throw new ServiceException("平台国标ID必须为20位");
        }
        boolean dup = lambdaQuery().eq(GbPlatform::getPlatformId, platform.getPlatformId()).exists();
        if (dup) {
            throw new ServiceException("平台国标ID已存在：" + platform.getPlatformId());
        }
        platform.setId(null);
        if (platform.getEnabled() == null) {
            platform.setEnabled(1);
        }
        save(platform);
        log.info("[级联平台] 新增白名单: platformId={} name={}", platform.getPlatformId(), platform.getName());
    }

    @Override
    public void updateByManual(GbPlatform platform) {
        GbPlatform exist = getById(platform.getId());
        if (exist == null) {
            throw new ServiceException("平台不存在");
        }
        // platformId 不可改
        exist.setName(platform.getName());
        exist.setPassword(platform.getPassword());
        exist.setEnabled(platform.getEnabled());
        exist.setRemark(platform.getRemark());
        updateById(exist);
        log.info("[级联平台] 编辑白名单: platformId={}", exist.getPlatformId());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        // 本期只删白名单记录，不动已注册的 gb_device 行
        for (Long id : ids) {
            GbPlatform platform = getById(id);
            if (platform != null) {
                removeById(id);
                log.info("[级联平台] 删除白名单: platformId={} name={}", platform.getPlatformId(), platform.getName());
            }
        }
    }

    @Override
    public GbPlatform findEnabledByPlatformId(String platformId) {
        return lambdaQuery()
                .eq(GbPlatform::getPlatformId, platformId)
                .eq(GbPlatform::getEnabled, 1)
                .one();
    }
}