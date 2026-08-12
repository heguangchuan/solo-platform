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
 * {@code parent_id} 仍由 Catalog 同步写入（供级联回 XML 与共享树建层级使用），但<b>不暴露给前端</b>：
 * 后端不在响应里回填 {@code parentName}（避免名称查表 + N+1），前端也不展示 {@code parentId} 字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDeviceChannelServiceImpl extends ServiceImpl<GbDeviceChannelMapper, GbDeviceChannel>
        implements GbDeviceChannelService {

    private final GbDeviceMapper deviceMapper;
    private final Gb28181Properties properties;

    @Override
    public Page<GbDeviceChannelResponse> pageQuery(GbDeviceChannelPageRequest request) {
        LambdaQueryWrapper<GbDeviceChannel> wrapper = new LambdaQueryWrapper<>();
        // 默认展示全部（通道 + 分组）；request.parental 不为空时按类型过滤
        if (request.getParental() != null) {
            wrapper.eq(GbDeviceChannel::getParental, request.getParental());
        }
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            wrapper.like(GbDeviceChannel::getDeviceId, request.getDeviceId());
        }
        if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
            wrapper.like(GbDeviceChannel::getChannelId, request.getChannelId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbDeviceChannel::getName, request.getName());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            // status 是通道字段，分组固定为 "ON"——按 status 过滤时只看通道行
            wrapper.eq(GbDeviceChannel::getParental, 0)
                    .eq(GbDeviceChannel::getStatus, request.getStatus());
        }
        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            // 分组编码：LIKE 包含匹配（'215' 匹配 '...215...'），按 GB 码任意段模糊
            wrapper.like(GbDeviceChannel::getParentId, request.getParentId());
        }
        if (request.getSource() != null) {
            wrapper.eq(GbDeviceChannel::getSource, request.getSource());
        }
        wrapper.orderByAsc(GbDeviceChannel::getParental)  // 分组（1）排在通道（0）之前
                .orderByAsc(GbDeviceChannel::getDeviceId)
                .orderByAsc(GbDeviceChannel::getChannelId);
        Page<GbDeviceChannel> raw = page(new Page<>(request.getPage(), request.getPageSize()), wrapper);

        // 转 response + 直接透传 entity 字段（不回填任何冗余字段）
        List<GbDeviceChannelResponse> rows = raw.getRecords().stream()
                .map(GbDeviceChannelServiceImpl::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));

        Page<GbDeviceChannelResponse> resp = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        resp.setRecords(rows);
        return resp;
    }

    private static GbDeviceChannelResponse toResponse(GbDeviceChannel src) {
        GbDeviceChannelResponse vo = new GbDeviceChannelResponse();
        BeanUtils.copyProperties(src, vo);
        return vo;
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
        // parentId = 所属设备 GB 码：与 GB/T 28181 语义一致（NVR/IPC 推 Catalog 时通道
        // <ParentID> 就是设备自己的 DeviceID），也与 CatalogSyncSupport 孤儿回落规则统一
        channel.setParentId(device.getDeviceId());
        save(channel);
        log.info("[通道新增] 系统手动新增通道: device={} channel={}", device.getDeviceId(), channel.getChannelId());
    }

    @Override
    public void updateByManual(GbDeviceChannel channel) {
        GbDeviceChannel exist = getById(channel.getId());
        if (exist == null) {
            throw new ServiceException("通道不存在");
        }
        // 只更新可编辑资料字段，绝不触碰 status/source 及设备归属关系
        // parentId 不接受外部修改——手动新增时已绑定设备 GB 码，运行期不变
        exist.setName(channel.getName());
        exist.setManufacturer(channel.getManufacturer());
        exist.setModel(channel.getModel());
        exist.setOwner(channel.getOwner());
        exist.setCivilCode(channel.getCivilCode());
        exist.setBlock(channel.getBlock());
        exist.setAddress(channel.getAddress());
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

    }

    @Override
    public void saveOrUpdateBatch(List<GbDeviceChannel> channels) {
        if (channels == null || channels.isEmpty()) return;
        // 业务预处理：原始名/默认名 + 预填雪花 id
        // parentId 不在此处兜底——保持 null（手动新增）或 Catalog 同步传入的值（XML <ParentID>）
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
        }
        // 走 mapper 的 INSERT ... ON DUPLICATE KEY UPDATE：单 SQL 多行 upsert
        // 业务唯一键 uk_device_channel (device_id, channel_id) 同时覆盖通道和分组：
        //   - 通道：设备内 channel_id 唯一
        //   - 分组：device_id 标识推送源设备，同设备同 GB 码的分组自动 upsert
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
        // 批量删除
        removeByIds(ids);
        log.info("[通道批量删除] 删除 {} 条通道", ids.size());
    }

}
