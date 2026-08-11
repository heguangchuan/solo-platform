package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.mapper.GbPtzPresetMapper;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbPtzPresetService;
import com.solo.gb28181.transmit.cmd.SipCommander;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 云台预置位服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPtzPresetServiceImpl extends ServiceImpl<GbPtzPresetMapper, GbPtzPreset> implements GbPtzPresetService {

    private final GbDeviceService deviceService;
    private final GbDeviceChannelService channelService;
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
        // 自动计算 presetId：当前通道最大 presetId + 1，从 1 开始
        GbPtzPreset maxPreset = this.lambdaQuery()
                .eq(GbPtzPreset::getChannelDbId, preset.getChannelDbId())
                .orderByDesc(GbPtzPreset::getPresetId)
                .last("LIMIT 1")
                .one();
        preset.setPresetId(maxPreset == null ? 1 : maxPreset.getPresetId() + 1);

        super.save(preset);
        // 通知设备将当前位置保存到该预置位槽
        sendPresetCmd(preset, "[预置点设置]", SipUtils.buildPtzSetPreset(preset.getPresetId()));
    }

    @Override
    public void updatePreset(GbPtzPreset preset) {
        // 仅修改名称，presetId 不变，无需通知设备
        this.updateById(preset);
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

    /**
     * 向设备发送预置位 PTZ 指令
     */
    private void sendPresetCmd(GbPtzPreset preset, String action, String ptzCmd) {
        GbDeviceChannel channel = channelService.getById(preset.getChannelDbId());
        if (channel == null) {
            throw new com.solo.common.ServiceException("通道不存在");
        }
        GbDevice device = deviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, channel.getDeviceId()).one();
        if (device == null) {
            throw new com.solo.common.ServiceException("设备不存在");
        }
        log.info("[云台预置位] {}预置位 deviceId={} channelId={} presetId={} cmd={}",
                action, device.getDeviceId(), channel.getChannelId(), preset.getPresetId(), ptzCmd);
        sipCommander.sendPtzCmd(device, channel.getChannelId(), ptzCmd);
    }
}
