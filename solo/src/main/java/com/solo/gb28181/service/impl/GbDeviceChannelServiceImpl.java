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
 * 国标资源目录项服务实现（ADR-023 合并版：通道 + 分组合一，靠 parental 区分）。
 *
 * <p>合并后去 {@code device_db_id} 雪花主键引用，关系仅由 GB 码 {@code device_id} 维系。
 * 需要按设备查 channel 时用 {@code deviceId}（GB 码字符串）做关联；要操作设备行时
 * 先 {@code deviceMapper.selectOne(... deviceId)} 取到 snowflake id。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceChannelServiceImpl extends ServiceImpl<GbDeviceChannelMapper, GbDeviceChannel>
        implements GbDeviceChannelService {

    /** 根哨兵：未指定父项时统一填 "0"（parentId GB 码 = "0" 表根） */
    private static final String ROOT_PARENT = "0";

    private final GbDeviceMapper deviceMapper;
    private final Gb28181Properties properties;

    /**
     * 解析 catalog item 所属父项：入参已指定则用入参，否则回落根哨兵 "0"。
     * <p>通道注册 / 导入 / 新增三条入库路径统一走此兜底。
     * ADR-023：完全去掉"默认分组"概念，不再有 id=0 的特殊行；parentId="0" 即根。</p>
     */
    private String resolveParentId(String input) {
        if (input != null && !input.isBlank()) {
            return input;
        }
        return ROOT_PARENT;
    }

    @Override
    public Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request) {
        LambdaQueryWrapper<GbDeviceChannel> wrapper = new LambdaQueryWrapper<>();
        // 默认只查通道（parental=0），分组不进通道列表
        wrapper.eq(GbDeviceChannel::getParental, 0);
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

        // 转 response + 回填 deviceName / parentName
        List<GbDeviceChannelResponse> rows = raw.getRecords().stream()
                .map(GbDeviceChannelServiceImpl::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
        fillDeviceName(rows);
        fillParentName(rows);

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
     * 按 parentId 批量查父 catalog item 名，回填到当前页所有 response。
     * <p>parentId 是父 item 的 20 位 GB 码（与父行 channel_id 对齐）。一次性 IN 查询，避免 N+1。
     * parentId="0" 或父项已删 → parentName 留空。</p>
     */
    private void fillParentName(List<GbDeviceChannelResponse> rows) {
        if (rows.isEmpty()) return;
        Set<String> parentIds = rows.stream()
                .map(GbDeviceChannelResponse::getParentId)
                .filter(id -> id != null && !id.isBlank() && !ROOT_PARENT.equals(id))
                .collect(Collectors.toCollection(HashSet::new));
        if (parentIds.isEmpty()) return;
        Map<String, String> nameMap = lambdaQuery()
                        .in(GbDeviceChannel::getChannelId, parentIds)
                        .eq(GbDeviceChannel::getParental, 1)
                        .list()
                .stream()
                .collect(Collectors.toMap(GbDeviceChannel::getChannelId,
                        p -> p.getName() == null ? "" : p.getName(),
                        (a, b) -> a));
        rows.forEach(r -> r.setParentName(nameMap.get(r.getParentId())));
    }

    @Override
    public void createByManual(GbDeviceChannel channel) {
        if (channel.getDeviceId() == null || channel.getDeviceId().isBlank()) {
            throw new ServiceException("所属设备不能为空");
        }
        if (channel.getChannelId() == null || channel.getChannelId().isBlank()) {
            throw new ServiceException("通道国标ID不能为空");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, channel.getDeviceId()));
        if (device == null) {
            throw new ServiceException("所属设备不存在");
        }
        // 通道国标ID在同一所属设备下唯一（跟 uk_device_channel 对齐）。GB/T 28181 语义上
        // 通道号由所属物理设备自身分配，不同厂商 NVR 出现相同末位序号是正常情况。
        boolean dup = lambdaQuery()
                .eq(GbDeviceChannel::getDeviceId, channel.getDeviceId())
                .eq(GbDeviceChannel::getChannelId, channel.getChannelId())
                .exists();
        if (dup) {
            throw new ServiceException("通道国标ID已存在：" + channel.getChannelId());
        }
        // 回填冗余字段，状态与来源由系统决定
        channel.setId(null);
        channel.setParental(0);  // 手动新增一定是通道
        channel.setStatus(GbSource.CHANNEL_STATUS_UNREGISTERED);
        channel.setSource(GbSource.SYSTEM);
        channel.setParentId(resolveParentId(channel.getParentId()));
        save(channel);
        // 更新设备通道计数
        long count = lambdaQuery()
                .eq(GbDeviceChannel::getDeviceId, device.getDeviceId())
                .eq(GbDeviceChannel::getParental, 0)
                .count();
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
        exist.setParentId(resolveParentId(channel.getParentId()));
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
        String deviceId = channel.getDeviceId();
        removeById(id);
        log.info("[通道删除] id={}", id);
        // 更新设备通道计数
        if (deviceId != null) {
            long count = lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceId, deviceId)
                    .eq(GbDeviceChannel::getParental, 0)
                    .count();
            GbDevice device = deviceMapper.selectOne(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
            if (device != null) {
                device.setChannelCount((int) count);
                deviceMapper.updateById(device);
            }
            // 删除后若该设备名下已无任何通道，连同设备一起删除（这个逻辑保留）
            boolean hasOther = lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceId, deviceId)
                    .eq(GbDeviceChannel::getParental, 0)
                    .exists();
            if (!hasOther) {
                deviceMapper.deleteById(device.getId());
                log.info("[通道删除] 设备已无通道，连同删除设备: deviceId={}", deviceId);
            }
        }
    }

    @Override
    public void saveOrUpdateBatch(List<GbDeviceChannel> channels) {
        if (channels == null || channels.isEmpty()) return;
        // 业务预处理：原始名/默认名 + 预填雪花 id + 分组应用层查重（ADR-023 删 uk_group_code 后由这里兜底）
        Set<String> groupChannelIdsToLookup = null;
        for (GbDeviceChannel ch : channels) {
            String reportedName = ch.getName();
            if (reportedName != null && !reportedName.isBlank()) {
                ch.setReportName(reportedName);
                if (ch.getName() == null || ch.getName().isBlank()) {
                    ch.setName(reportedName);
                }
            }
            if (ch.getParentId() == null || ch.getParentId().isBlank()) {
                ch.setParentId(resolveParentId(null));
            }
        }
        // 分组（parental=1）批量前先按 channel_id 批量查重：已存在的行复用其 id（upsert 语义），
        // 新行才预填雪花 id。无 DB 唯一键保护时的应用层兜底，量级小（通常几十个分组）开销可接受。
        List<GbDeviceChannel> groupItems = channels.stream()
                .filter(ch -> ch.getParental() != null && ch.getParental() == 1)
                .filter(ch -> ch.getChannelId() != null)
                .toList();
        if (!groupItems.isEmpty()) {
            Set<String> groupCodes = groupItems.stream()
                    .map(GbDeviceChannel::getChannelId)
                    .collect(Collectors.toSet());
            Map<String, Long> existingIdByCode = lambdaQuery()
                    .eq(GbDeviceChannel::getParental, 1)
                    .in(GbDeviceChannel::getChannelId, groupCodes)
                    .list()
                    .stream()
                    .collect(Collectors.toMap(GbDeviceChannel::getChannelId, GbDeviceChannel::getId, (a, b) -> a));
            for (GbDeviceChannel g : groupItems) {
                Long existingId = existingIdByCode.get(g.getChannelId());
                g.setId(existingId != null ? existingId : IdWorker.getId());
            }
        }
        // 通道（parental=0）：无 id 时预填雪花，让 batchUpsert 走 uk_device_channel
        for (GbDeviceChannel ch : channels) {
            if (ch.getParental() == null || ch.getParental() == 0) {
                if (ch.getId() == null) ch.setId(IdWorker.getId());
            }
        }
        // 走 mapper 的 INSERT ... ON DUPLICATE KEY UPDATE：单 SQL 多行 upsert，按业务唯一键
        // uk_device_channel (device_id, channel_id) 判存在性，分组靠上面的 id 复用走 ON DUPLICATE。
        int batchSize = Math.max(1, properties.getCatalog().getBatchSize());
        int total = channels.size();
        long start = System.currentTimeMillis();
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            baseMapper.batchUpsert(channels.subList(i, end));
        }
        long cost = System.currentTimeMillis() - start;
        log.info("[通道目录] 批量 upsert 完成 device={} rows={} batches={} costMs={}",
                channels.isEmpty() ? "?" : channels.getFirst().getDeviceId(),
                total, (total + batchSize - 1) / batchSize, cost);
    }

    @Override
    @Transactional
    public void deleteByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return;
        lambdaUpdate()
                .eq(GbDeviceChannel::getDeviceId, deviceId)
                .remove();
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        // 收集所有待删除通道所属的 deviceId
        List<GbDeviceChannel> channels = listByIds(ids);
        if (channels.isEmpty()) {
            return;
        }
        // 按 deviceId 分组，方便后续更新计数
        var channelsByDeviceId = channels.stream()
                .filter(ch -> ch.getDeviceId() != null)
                .collect(Collectors.groupingBy(GbDeviceChannel::getDeviceId));
        // 批量删除
        removeByIds(ids);
        log.info("[通道批量删除] 删除 {} 条通道", ids.size());
        // 更新每个设备的通道计数，若设备已无通道则删除设备
        for (var entry : channelsByDeviceId.entrySet()) {
            String deviceId = entry.getKey();
            long count = lambdaQuery()
                    .eq(GbDeviceChannel::getDeviceId, deviceId)
                    .eq(GbDeviceChannel::getParental, 0)
                    .count();
            GbDevice device = deviceMapper.selectOne(
                    new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, deviceId));
            if (device != null) {
                device.setChannelCount((int) count);
                deviceMapper.updateById(device);
                // 若该设备名下已无任何通道，连同设备一起删除
                if (count == 0) {
                    deviceMapper.deleteById(device.getId());
                    log.info("[通道批量删除] 设备已无通道，连同删除设备: deviceId={}", deviceId);
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

        // 查询所有在线设备下的通道（仅 parental=0）
        List<String> deviceIds = devices.stream()
                .map(GbDevice::getDeviceId)
                .collect(Collectors.toList());
        List<GbDeviceChannel> channels = this.lambdaQuery()
                .in(GbDeviceChannel::getDeviceId, deviceIds)
                .eq(GbDeviceChannel::getParental, 0)
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
