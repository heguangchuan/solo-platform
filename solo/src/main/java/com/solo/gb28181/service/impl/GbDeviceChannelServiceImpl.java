package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.service.GbDeviceChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 国标设备通道服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceChannelServiceImpl extends ServiceImpl<GbDeviceChannelMapper, GbDeviceChannel>
        implements GbDeviceChannelService {

    @Override
    public Page<GbDeviceChannel> pageQuery(GbDeviceChannelPageRequest request) {
        LambdaQueryWrapper<GbDeviceChannel> wrapper = new LambdaQueryWrapper<>();
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            wrapper.eq(GbDeviceChannel::getDeviceId, request.getDeviceId());
        }
        if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
            wrapper.like(GbDeviceChannel::getChannelId, request.getChannelId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbDeviceChannel::getName, request.getName());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(GbDeviceChannel::getStatus, request.getStatus());
        }
        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            wrapper.eq(GbDeviceChannel::getParentId, request.getParentId());
        }
        wrapper.orderByAsc(GbDeviceChannel::getChannelId);
        return page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
    }

    @Override
    @Transactional
    public void saveOrUpdateBatch(List<GbDeviceChannel> channels) {
        if (channels == null || channels.isEmpty()) return;
        for (GbDeviceChannel ch : channels) {
            GbDeviceChannel exist = lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceId, ch.getDeviceId())
                    .eq(GbDeviceChannel::getChannelId, ch.getChannelId())
                    .one();
            if (exist == null) {
                save(ch);
            } else {
                ch.setId(exist.getId());
                ch.setCreateTime(exist.getCreateTime());
                updateById(ch);
            }
        }
        log.info("[通道目录] 批量入库 device={} count={}", channels.get(0).getDeviceId(), channels.size());
    }

    @Override
    @Transactional
    public void deleteByDeviceDbId(Long deviceDbId) {
        lambdaUpdate().eq(GbDeviceChannel::getDeviceDbId, deviceDbId).remove();
    }
}
