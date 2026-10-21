package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPtzCruise;
import com.solo.gb28181.entity.GbPtzCruisePoint;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPtzCruiseMapper;
import com.solo.gb28181.mapper.GbPtzCruisePointMapper;
import com.solo.gb28181.mapper.GbPtzPresetMapper;
import com.solo.gb28181.service.GbPtzCruiseService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbPtzCruiseServiceImpl extends ServiceImpl<GbPtzCruiseMapper, GbPtzCruise> implements GbPtzCruiseService {

    private final GbPtzCruisePointMapper cruisePointMapper;
    private final GbPtzPresetMapper presetMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final GbDeviceMapper deviceMapper;
    private final SipCommander sipCommander;

    @Override
    public List<GbPtzCruise> listByChannel(Long channelDbId) {
        return this.lambdaQuery()
                .eq(GbPtzCruise::getChannelDbId, channelDbId)
                .orderByAsc(GbPtzCruise::getCruiseId)
                .list();
    }

    @Override
    public GbPtzCruise getDetail(Long id) {
        return this.getById(id);
    }

    public List<GbPtzCruisePoint> listPoints(Long cruiseId) {
        return cruisePointMapper.selectList(
                new LambdaQueryWrapper<GbPtzCruisePoint>()
                        .eq(GbPtzCruisePoint::getCruiseId, cruiseId)
                        .orderByAsc(GbPtzCruisePoint::getSortOrder)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCruise(GbPtzCruise cruise, List<GbPtzCruisePoint> points) {
        // 自动分配 cruiseId：当前通道最大 cruiseId + 1，从 1 开始
        GbPtzCruise maxCruise = this.lambdaQuery()
                .eq(GbPtzCruise::getChannelDbId, cruise.getChannelDbId())
                .orderByDesc(GbPtzCruise::getCruiseId)
                .last("LIMIT 1")
                .one();
        cruise.setCruiseId(maxCruise == null ? 1 : maxCruise.getCruiseId() + 1);

        if (cruise.getSpeed() == null) {
            cruise.setSpeed(50);
        }
        if (cruise.getStaySeconds() == null) {
            cruise.setStaySeconds(10);
        }

        super.save(cruise);

        // 设置巡航速度（路线级）
        sendCruiseCmd(cruise, "[设置巡航速度]",
                SipUtils.buildPtzCruiseSetSpeed(cruise.getCruiseId(), cruise.getSpeed()));
        sleepBetweenCmd();

        // 设置巡航停留（路线级默认）
        sendCruiseCmd(cruise, "[设置巡航停留]",
                SipUtils.buildPtzCruiseSetStay(cruise.getCruiseId(), cruise.getStaySeconds()));
        sleepBetweenCmd();

        // 保存巡航点并逐点下发，点停留(0~15)由 0x84 字节7高4位携带
        if (points != null && !points.isEmpty()) {
            int sort = 0;
            for (GbPtzCruisePoint point : points) {
                point.setCruiseId(cruise.getId());
                point.setSortOrder(sort++);
                fillPresetName(point, cruise.getChannelDbId());
                cruisePointMapper.insert(point);

                sendCruiseCmd(cruise, "[添加巡航点]",
                        SipUtils.buildPtzCruiseAddPoint(
                                cruise.getCruiseId(),
                                point.getPresetId(),
                                point.getStaySeconds() != null ? point.getStaySeconds() : cruise.getStaySeconds()));
                // 连续 SIP MESSAGE 需要间隔，避免设备处理不过来丢指令
                sleepBetweenCmd();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCruise(GbPtzCruise cruise) {
        GbPtzCruise oldCruise = this.getById(cruise.getId());
        if (oldCruise == null) {
            throw new ServiceException("巡航路线不存在");
        }
        this.updateById(cruise);

        // 速度变化时同步设备
        if (cruise.getSpeed() != null && !cruise.getSpeed().equals(oldCruise.getSpeed())) {
            sendCruiseCmd(cruise, "[设置巡航速度]",
                    SipUtils.buildPtzCruiseSetSpeed(cruise.getCruiseId(), cruise.getSpeed()));
        }
        // 停留时间变化时同步设备
        if (cruise.getStaySeconds() != null && !cruise.getStaySeconds().equals(oldCruise.getStaySeconds())) {
            sendCruiseCmd(cruise, "[设置巡航停留]",
                    SipUtils.buildPtzCruiseSetStay(cruise.getCruiseId(), cruise.getStaySeconds()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCruise(Long id) {
        GbPtzCruise cruise = this.getById(id);
        if (cruise == null) {
            throw new ServiceException("巡航路线不存在");
        }

        // 逐个删除设备侧巡航点
        List<GbPtzCruisePoint> points = listPoints(id);
        for (GbPtzCruisePoint point : points) {
            sendCruiseCmd(cruise, "[删除巡航点]",
                    SipUtils.buildPtzCruiseRemovePoint(cruise.getCruiseId(), point.getPresetId()));
            sleepBetweenCmd();
        }

        // 删除数据库
        cruisePointMapper.delete(
                new LambdaQueryWrapper<GbPtzCruisePoint>().eq(GbPtzCruisePoint::getCruiseId, id));
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPoint(Long cruiseId, GbPtzCruisePoint point) {
        GbPtzCruise cruise = this.getById(cruiseId);
        if (cruise == null) {
            throw new ServiceException("巡航路线不存在");
        }

        // 检查预置位是否存在
        GbPtzPreset preset = presetMapper.selectOne(
                new LambdaQueryWrapper<GbPtzPreset>()
                        .eq(GbPtzPreset::getChannelDbId, cruise.getChannelDbId())
                        .eq(GbPtzPreset::getPresetId, point.getPresetId()));
        if (preset == null) {
            throw new ServiceException("预置位不存在: " + point.getPresetId());
        }

        // 计算最大排序号
        GbPtzCruisePoint maxPoint = cruisePointMapper.selectOne(
                new LambdaQueryWrapper<GbPtzCruisePoint>()
                        .eq(GbPtzCruisePoint::getCruiseId, cruiseId)
                        .orderByDesc(GbPtzCruisePoint::getSortOrder)
                        .last("LIMIT 1"));
        point.setCruiseId(cruiseId);
        point.setSortOrder(maxPoint == null ? 0 : maxPoint.getSortOrder() + 1);
        point.setPresetName(preset.getName());
        cruisePointMapper.insert(point);

        // 通知设备添加巡航点，点停留(0~15)由字节7高4位携带
        sendCruiseCmd(cruise, "[添加巡航点]",
                SipUtils.buildPtzCruiseAddPoint(
                        cruise.getCruiseId(),
                        point.getPresetId(),
                        point.getStaySeconds() != null ? point.getStaySeconds() : cruise.getStaySeconds()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePoint(Long cruiseId, Integer presetId) {
        GbPtzCruise cruise = this.getById(cruiseId);
        if (cruise == null) {
            throw new ServiceException("巡航路线不存在");
        }

        GbPtzCruisePoint point = cruisePointMapper.selectOne(
                new LambdaQueryWrapper<GbPtzCruisePoint>()
                        .eq(GbPtzCruisePoint::getCruiseId, cruiseId)
                        .eq(GbPtzCruisePoint::getPresetId, presetId));
        if (point == null) {
            throw new ServiceException("巡航点不存在");
        }

        // 通知设备删除
        sendCruiseCmd(cruise, "[删除巡航点]",
                SipUtils.buildPtzCruiseRemovePoint(cruise.getCruiseId(), presetId));

        cruisePointMapper.deleteById(point.getId());
    }

    @Override
    public void startCruise(Long cruiseId) {
        GbPtzCruise cruise = this.getById(cruiseId);
        if (cruise == null) {
            throw new ServiceException("巡航路线不存在");
        }
        sendCruiseCmd(cruise, "[开始巡航]",
                SipUtils.buildPtzCruiseStart(cruise.getCruiseId()));
    }

    @Override
    public void stopCruise(Long cruiseId) {
        GbPtzCruise cruise = this.getById(cruiseId);
        if (cruise == null) {
            throw new ServiceException("巡航路线不存在");
        }
        sendCruiseCmd(cruise, "[停止巡航]",
                SipUtils.buildPtzCruiseStop(cruise.getCruiseId()));
    }

    private void fillPresetName(GbPtzCruisePoint point, Long channelDbId) {
        if (point.getPresetId() == null) {
            return;
        }
        GbPtzPreset preset = presetMapper.selectOne(
                new LambdaQueryWrapper<GbPtzPreset>()
                        .eq(GbPtzPreset::getChannelDbId, channelDbId)
                        .eq(GbPtzPreset::getPresetId, point.getPresetId()));
        if (preset != null) {
            point.setPresetName(preset.getName());
        }
    }

    /**
     * 两条 SIP 指令之间的间隔，给设备留出处理时间
     */
    private void sleepBetweenCmd() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 向设备发送巡航 PTZ 指令
     */
    private void sendCruiseCmd(GbPtzCruise cruise, String action, String ptzCmd) {
        GbDeviceChannel channel = channelMapper.selectById(cruise.getChannelDbId());
        if (channel == null) {
            throw new ServiceException("通道不存在");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, channel.getDeviceId()));
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        log.info("[云台巡航] {} cruiseId={} channelId={} cmd={}",
                action, cruise.getCruiseId(), channel.getChannelId(), ptzCmd);
        sipCommander.sendPtzCmd(device, channel.getChannelId(), ptzCmd);
    }
}
