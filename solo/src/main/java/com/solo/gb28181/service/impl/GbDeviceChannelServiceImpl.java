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
import com.solo.gb28181.response.GbDeviceChannelTreeNodeResponse;
import com.solo.gb28181.service.GbDeviceChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
        wrapper.orderByDesc(GbDeviceChannel::getCreateTime);
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
        // 更新设备通道计数
        long count = lambdaQuery().eq(GbDeviceChannel::getDeviceDbId, device.getId()).count();
        device.setChannelCount((int) count);
        deviceMapper.updateById(device);
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
        Long deviceDbId = channel.getDeviceDbId();
        removeById(id);
        log.info("[通道删除] id={}", id);
        // 更新设备通道计数
        if (deviceDbId != null) {
            long count = lambdaQuery().eq(GbDeviceChannel::getDeviceDbId, deviceDbId).count();
            GbDevice device = deviceMapper.selectById(deviceDbId);
            if (device != null) {
                device.setChannelCount((int) count);
                deviceMapper.updateById(device);
            }
            // 删除后若该设备名下已无任何通道，连同设备一起删除（这个逻辑保留）
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
            // Catalog 同步上来：原始名称放 reportName，name 如果为空就填原始名称
            String reportedName = ch.getName();
            if (reportedName != null && !reportedName.isBlank()) {
                ch.setReportName(reportedName);
                if (ch.getName() == null || ch.getName().isBlank()) {
                    ch.setName(reportedName);
                }
            }
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

    @Override
    public List<GbDeviceChannelTreeNodeResponse> getDeviceChannelTree() {
        // 查询所有设备（包括离线）
        List<GbDevice> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<GbDevice>()
                        .orderByAsc(GbDevice::getName)
        );

        if (devices.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询所有在线设备下的通道
        List<String> deviceIds = devices.stream()
                .map(GbDevice::getDeviceId)
                .collect(Collectors.toList());
        List<GbDeviceChannel> channels = this.lambdaQuery()
                .in(GbDeviceChannel::getDeviceId, deviceIds)
                .orderByAsc(GbDeviceChannel::getChannelId)
                .list();

        // 按 deviceId 分组通道
        Map<String, List<GbDeviceChannel>> channelsByDeviceId = channels.stream()
                .collect(Collectors.groupingBy(GbDeviceChannel::getDeviceId));

        // 组装树：每个设备作为父节点，通道作为子节点
        List<GbDeviceChannelTreeNodeResponse> result = new ArrayList<>();
        for (GbDevice device : devices) {
            GbDeviceChannelTreeNodeResponse deviceNode = new GbDeviceChannelTreeNodeResponse();
            deviceNode.setId("device_" + device.getId());
            deviceNode.setLabel(device.getName() + " (" + device.getDeviceId() + ")");
            deviceNode.setDeviceId(device.getDeviceId());
            deviceNode.setIsDevice(true);
            deviceNode.setStatus(device.getStatus());

            // 获取该设备下的通道
            List<GbDeviceChannel> deviceChannels = channelsByDeviceId.getOrDefault(device.getDeviceId(), new ArrayList<>());
            if (!deviceChannels.isEmpty()) {
                List<GbDeviceChannelTreeNodeResponse> children = deviceChannels.stream()
                        .map(ch -> {
                            GbDeviceChannelTreeNodeResponse channelNode = new GbDeviceChannelTreeNodeResponse();
                            channelNode.setId(device.getId() + "_" + ch.getId());
                            channelNode.setLabel(ch.getName() + " (" + ch.getChannelId() + ")");
                            channelNode.setDeviceId(device.getDeviceId());
                            channelNode.setChannelId(ch.getChannelId());
                            channelNode.setIsDevice(false);
                            channelNode.setChannelStatus(ch.getStatus());
                            return channelNode;
                        })
                        .collect(Collectors.toList());
                deviceNode.setChildren(children);
            }

            result.add(deviceNode);
        }

        return result;
    }
}
