package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.constant.GbSource;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbGroupMapper;
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
    private final GbGroupMapper groupMapper;
    private final Gb28181Properties properties;

    /**
     * 解析通道所属分组：入参已指定则用入参，否则回落到配置的默认分组。
     * 通道注册 / 导入 / 新增三条入库路径统一走此兜底。
     */
    private Long resolveGroupId(Long input) {
        if (input != null) {
            return input;
        }
        return properties.getDefaultGroup().getId();
    }

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
        if (request.getGroupId() != null) {
            wrapper.eq(GbDeviceChannel::getGroupId, request.getGroupId());
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
        fillGroupName(rows);

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

    /**
     * 按 groupId 批量查目录名，回填到当前页所有 response。一次性 IN 查询，避免 N+1。
     */
    private void fillGroupName(List<GbDeviceChannelResponse> rows) {
        if (rows.isEmpty()) return;
        Set<Long> groupIds = rows.stream()
                .map(GbDeviceChannelResponse::getGroupId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (groupIds.isEmpty()) return;
        Map<Long, String> nameMap = groupMapper.selectList(
                        new LambdaQueryWrapper<GbGroup>().in(GbGroup::getId, groupIds))
                .stream()
                .collect(Collectors.toMap(GbGroup::getId,
                        g -> g.getName() == null ? "" : g.getName(),
                        (a, b) -> a));
        rows.forEach(r -> r.setGroupName(nameMap.get(r.getGroupId())));
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
        // 通道国标ID在同一所属设备下唯一（跟 uk_device_channel 对齐）。GB/T 28181 语义上
        // 通道号由所属物理设备自身分配，不同厂商 NVR 出现相同末位序号是正常情况。
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
        channel.setPlatformId(device.getPlatformId());
        channel.setStatus(GbSource.CHANNEL_STATUS_UNREGISTERED);
        channel.setSource(GbSource.SYSTEM);
        channel.setGroupId(resolveGroupId(channel.getGroupId()));
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
        // 只更新可编辑资料字段，绝不触碰 status/source 及设备归属关系
        exist.setName(channel.getName());
        exist.setManufacturer(channel.getManufacturer());
        exist.setModel(channel.getModel());
        exist.setOwner(channel.getOwner());
        exist.setCivilCode(channel.getCivilCode());
        exist.setBlock(channel.getBlock());
        exist.setAddress(channel.getAddress());
        exist.setGroupId(resolveGroupId(channel.getGroupId()));
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
    public void saveOrUpdateBatch(List<GbDeviceChannel> channels) {
        if (channels == null || channels.isEmpty()) return;
        // 业务预处理：原始名/默认名 + 预填雪花 id（让 mapper 的 ON DUPLICATE KEY UPDATE 走通，
        // 同时 id 也用于后续引用）。
        for (GbDeviceChannel ch : channels) {
            String reportedName = ch.getName();
            if (reportedName != null && !reportedName.isBlank()) {
                ch.setReportName(reportedName);
                if (ch.getName() == null || ch.getName().isBlank()) {
                    ch.setName(reportedName);
                }
            }
            if (ch.getId() == null) {
                ch.setId(IdWorker.getId());
            }
            if (ch.getGroupId() == null) {
                ch.setGroupId(resolveGroupId(null));
            }
        }
        // 走 mapper 的 INSERT ... ON DUPLICATE KEY UPDATE：单 SQL 多行 upsert，按业务唯一键
        // (device_db_id, channel_id) 判存在性，比 MP 的 saveOrUpdateBatch 更适合本表
        // （MP 按主键 id 判定会走 INSERT 然后撞业务唯一键）。
        int batchSize = Math.max(1, properties.getCatalog().getBatchSize());
        int total = channels.size();
        long start = System.currentTimeMillis();
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            baseMapper.batchUpsert(channels.subList(i, end));
        }
        long cost = System.currentTimeMillis() - start;
        log.info("[通道目录] 批量 upsert 完成 device={} rows={} batches={} costMs={}",
                channels.getFirst().getDeviceId(), total, (total + batchSize - 1) / batchSize, cost);
    }

    @Override
    @Transactional
    public void deleteByDeviceDbId(Long deviceDbId) {
        lambdaUpdate().eq(GbDeviceChannel::getDeviceDbId, deviceDbId).remove();
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 收集所有待删除通道所属的 deviceDbId
        List<GbDeviceChannel> channels = listByIds(ids);
        if (channels.isEmpty()) {
            return;
        }
        // 按 deviceDbId 分组，方便后续更新计数
        var channelsByDeviceDbId = channels.stream()
                .filter(ch -> ch.getDeviceDbId() != null)
                .collect(Collectors.groupingBy(GbDeviceChannel::getDeviceDbId));
        // 批量删除
        removeByIds(ids);
        log.info("[通道批量删除] 删除 {} 条通道", ids.size());
        // 更新每个设备的通道计数，若设备已无通道则删除设备
        for (var entry : channelsByDeviceDbId.entrySet()) {
            Long deviceDbId = entry.getKey();
            long count = lambdaQuery().eq(GbDeviceChannel::getDeviceDbId, deviceDbId).count();
            GbDevice device = deviceMapper.selectById(deviceDbId);
            if (device != null) {
                device.setChannelCount((int) count);
                deviceMapper.updateById(device);
                // 若该设备名下已无任何通道，连同设备一起删除
                if (count == 0) {
                    deviceMapper.deleteById(deviceDbId);
                    log.info("[通道批量删除] 设备已无通道，连同删除设备: deviceDbId={}", deviceDbId);
                }
            }
        }
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
