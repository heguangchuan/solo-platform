package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.request.GbDeviceChannelPageRequest;
import com.solo.gb28181.response.GbDeviceChannelResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 国标设备通道服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceChannelServiceImpl extends ServiceImpl<GbDeviceChannelMapper, GbDeviceChannel>
        implements GbDeviceChannelService {

    private final GbDeviceMapper deviceMapper;

    @Override
    public Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request) {
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
        if (request.getSource() != null) {
            wrapper.eq(GbDeviceChannel::getSource, request.getSource());
        }
        wrapper.orderByAsc(GbDeviceChannel::getChannelId);
        Page<GbDeviceChannel> raw = page(new Page<>(request.getPage(), request.getPageSize()), wrapper);

        // 转 response + 回填 deviceName
        List<GbDeviceChannelResponse> rows = raw.getRecords().stream()
                .map(GbDeviceChannelServiceImpl::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
        fillDeviceName(rows);

        Page<GbDeviceChannelResponse> resp = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        resp.setRecords(rows);
        return resp;
    }

    private static GbDeviceChannelResponse toResponse(GbDeviceChannel src) {
        GbDeviceChannelResponse vo = new GbDeviceChannelResponse();
        BeanUtils.copyProperties(src, vo);
        return vo;
    }

    /**
     * 按 deviceId 批量查设备名，回填到当前页所有 response。一次性 IN 查询，避免 N+1。
     */
    private void fillDeviceName(List<GbDeviceChannelResponse> rows) {
        if (rows.isEmpty()) return;
        Set<String> deviceIds = rows.stream()
                .map(GbDeviceChannelResponse::getDeviceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        if (deviceIds.isEmpty()) return;
        Map<String, String> nameMap = deviceMapper.selectList(
                        new LambdaQueryWrapper<GbDevice>().in(GbDevice::getDeviceId, deviceIds))
                .stream()
                .collect(Collectors.toMap(GbDevice::getDeviceId,
                        d -> d.getName() == null ? "" : d.getName(),
                        (a, b) -> a));
        rows.forEach(r -> r.setDeviceName(nameMap.get(r.getDeviceId())));
    }

    @Override
    public void createByManual(GbDeviceChannel channel) {
        if (channel.getDeviceDbId() == null) {
            throw new ServiceException("所属设备不能为空");
        }
        if (channel.getChannelId() == null || channel.getChannelId().isBlank()) {
            throw new ServiceException("通道国标ID不能为空");
        }
        GbDevice device = deviceMapper.selectById(channel.getDeviceDbId());
        if (device == null) {
            throw new ServiceException("所属设备不存在");
        }
        boolean dup = lambdaQuery()
                .eq(GbDeviceChannel::getDeviceDbId, channel.getDeviceDbId())
                .eq(GbDeviceChannel::getChannelId, channel.getChannelId())
                .exists();
        if (dup) {
            throw new ServiceException("通道国标ID已存在：" + channel.getChannelId());
        }
        // 回填冗余字段，状态与来源由系统决定
        channel.setId(null);
        channel.setDeviceId(device.getDeviceId());
        channel.setStatus(GbSource.CHANNEL_STATUS_UNREGISTERED);
        channel.setSource(GbSource.SYSTEM);
        channel.setStreamId(null);
        save(channel);
        log.info("[通道新增] 系统手动新增通道: device={} channel={}", device.getDeviceId(), channel.getChannelId());
    }

    @Override
    public void updateByManual(GbDeviceChannel channel) {
        GbDeviceChannel exist = getById(channel.getId());
        if (exist == null) {
            throw new ServiceException("通道不存在");
        }
        // 只更新可编辑资料字段，绝不触碰 status/source/streamId 及归属关系
        exist.setName(channel.getName());
        exist.setManufacturer(channel.getManufacturer());
        exist.setModel(channel.getModel());
        exist.setOwner(channel.getOwner());
        exist.setCivilCode(channel.getCivilCode());
        exist.setBlock(channel.getBlock());
        exist.setAddress(channel.getAddress());
        exist.setParentId(channel.getParentId());
        exist.setPtzType(channel.getPtzType());
        exist.setLongitude(channel.getLongitude());
        exist.setLatitude(channel.getLatitude());
        updateById(exist);
        log.info("[通道编辑] 更新通道资料: {}", exist.getChannelId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        GbDeviceChannel channel = getById(id);
        if (channel == null) return;
        removeById(id);
        log.info("[通道删除] id={}", id);
        // 删除后若该设备名下已无任何通道，连同设备一起删除
        Long deviceDbId = channel.getDeviceDbId();
        if (deviceDbId != null) {
            boolean hasOther = lambdaQuery().eq(GbDeviceChannel::getDeviceDbId, deviceDbId).exists();
            if (!hasOther) {
                deviceMapper.deleteById(deviceDbId);
                log.info("[通道删除] 设备已无通道，连同删除设备: deviceDbId={}", deviceDbId);
            }
        }
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
                // 保留最初来源：系统新增/导入的通道被 Catalog 同步后仍标记为原来源
                ch.setSource(exist.getSource());
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
