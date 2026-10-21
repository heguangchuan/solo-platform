package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.mapper.GbDeviceChannelMapper;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPtzPresetMapper;
import com.solo.gb28181.service.GbPtzPresetService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbPtzPresetServiceImpl extends ServiceImpl<GbPtzPresetMapper, GbPtzPreset> implements GbPtzPresetService {

    private final GbDeviceMapper deviceMapper;
    private final GbDeviceChannelMapper channelMapper;
    private final SipCommander sipCommander;

    @Override
    public List<GbPtzPreset> listByChannel(Long channelDbId) {
        return this.lambdaQuery()
                .eq(GbPtzPreset::getChannelDbId, channelDbId)
                .orderByAsc(GbPtzPreset::getPresetId)
                .list();
    }

    @Override
    public void savePreset(GbPtzPreset preset) {
        int presetId = preset.getPresetId();
        if (presetId < 1 || presetId > 10) {
            throw new com.solo.common.ServiceException("预置位编号范围为 1-10");
        }
        // 同通道同编号已存在：仅重新通知设备覆盖该位置，不重复入库
        GbPtzPreset existing = this.lambdaQuery()
                .eq(GbPtzPreset::getChannelDbId, preset.getChannelDbId())
                .eq(GbPtzPreset::getPresetId, presetId)
                .one();
        if (existing == null) {
            // 名称由后端统一生成，前端不可修改
            preset.setName("预置点" + presetId);
            super.save(preset);
        } else {
            // 已存在：复用数据库记录，仅重新通知设备覆盖该位置
            preset = existing;
        }
        // 通知设备将当前位置保存到该预置位槽
        sendPresetCmd(preset, "[预置点设置]", SipUtils.buildPtzSetPreset(presetId));
    }

    @Override
    public void deletePreset(Long id) {
        GbPtzPreset preset = this.getById(id);
        if (preset == null) {
            throw new com.solo.common.ServiceException("预置位不存在");
        }
        // 通知设备删除该预置位
        sendPresetCmd(preset, "[预置点删除]", SipUtils.buildPtzDeletePreset(preset.getPresetId()));
        this.removeById(id);
    }

    @Override
    public void call(Long id) {
        GbPtzPreset preset = this.getById(id);
        if (preset == null) {
            throw new com.solo.common.ServiceException("预置位不存在");
        }
        sendPresetCmd(preset, "[预置点调用]", SipUtils.buildPtzCallPreset(preset.getPresetId()));
    }

    @Override
    public void syncFromDevice(Long channelDbId) {
        GbDeviceChannel channel = channelMapper.selectById(channelDbId);
        if (channel == null) {
            throw new com.solo.common.ServiceException("通道不存在");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, channel.getDeviceId()));
        if (device == null) {
            throw new com.solo.common.ServiceException("设备不存在");
        }
        if (device.getStatus() == null || device.getStatus() != 1) {
            throw new com.solo.common.ServiceException("设备离线，无法同步预置位");
        }
        int sn = sipCommander.queryPreset(device, channel.getChannelId(), channelDbId);
        log.info("[云台预置位] 同步查询已下发 deviceId={} channelId={} sn={}",
                device.getDeviceId(), channel.getChannelId(), sn);
    }

    /**
     * 向设备发送预置位 PTZ 指令
     */
    private void sendPresetCmd(GbPtzPreset preset, String action, String ptzCmd) {
        GbDeviceChannel channel = channelMapper.selectById(preset.getChannelDbId());
        if (channel == null) {
            throw new com.solo.common.ServiceException("通道不存在");
        }
        GbDevice device = deviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, channel.getDeviceId()));
        if (device == null) {
            throw new com.solo.common.ServiceException("设备不存在");
        }
        log.info("[云台预置位] {}预置位 deviceId={} channelId={} presetId={} cmd={}",
                action, device.getDeviceId(), channel.getChannelId(), preset.getPresetId(), ptzCmd);
        sipCommander.sendPtzCmd(device, channel.getChannelId(), ptzCmd);
    }
}
