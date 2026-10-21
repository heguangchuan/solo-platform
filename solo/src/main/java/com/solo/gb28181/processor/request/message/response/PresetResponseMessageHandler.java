package com.solo.gb28181.processor.request.message.response;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbPtzPreset;
import com.solo.gb28181.mapper.GbPtzPresetMapper;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.support.PresetQueryHolder;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sip.RequestEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理设备返回的 Response/Preset 报文（预置位查询响应）。
 *
 * <p>直接注入 mapper 避免循环依赖，同步逻辑内聚在此处。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresetResponseMessageHandler implements MessageHandler {

    private final GbPtzPresetMapper presetMapper;

    @Override
    public String getRootType() { return "Response"; }

    @Override
    public String getCmdType() { return "Preset"; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        int sn = parseInt(XmlUtils.childText(root, "SN"), -1);
        if (sn < 0) {
            log.warn("[预置位查询] 缺失 SN，丢弃 deviceId={}", device.getDeviceId());
            return;
        }

        Long channelDbId = PresetQueryHolder.consume(sn);
        if (channelDbId == null) {
            log.warn("[预置位查询] 未找到对应上下文，可能已超时或非本平台发起 deviceId={} sn={}",
                    device.getDeviceId(), sn);
            return;
        }

        List<PresetItem> items = parsePresetList(root);
        log.info("[预置位查询] 收到响应 deviceId={} channelDbId={} sn={} count={}",
                device.getDeviceId(), channelDbId, sn, items.size());

        applyPresets(channelDbId, items);
    }

    private List<PresetItem> parsePresetList(Element root) {
        List<PresetItem> result = new ArrayList<>();
        Element presetList = root.element("PresetList");
        if (presetList == null) {
            presetList = root;
        }
        Iterator<Element> it = presetList.elementIterator("Item");
        while (it.hasNext()) {
            Element item = it.next();
            int presetId = parseInt(XmlUtils.childText(item, "PresetID"), -1);
            if (presetId < 0) {
                presetId = parseInt(XmlUtils.childText(item, "PresetIndex"), -1);
            }
            if (presetId < 0) {
                presetId = parseInt(XmlUtils.childText(item, "Index"), -1);
            }
            if (presetId <= 0) {
                continue;
            }
            String name = XmlUtils.childText(item, "PresetName");
            if (name == null || name.isBlank()) {
                name = XmlUtils.childText(item, "Name");
            }
            result.add(new PresetItem(presetId, name));
        }
        return result;
    }

    private void applyPresets(Long channelDbId, List<PresetItem> items) {
        List<GbPtzPreset> existing = presetMapper.selectList(
                new LambdaQueryWrapper<GbPtzPreset>()
                        .eq(GbPtzPreset::getChannelDbId, channelDbId));
        Map<Integer, GbPtzPreset> existingMap = existing.stream()
                .collect(Collectors.toMap(GbPtzPreset::getPresetId, p -> p));

        Set<Integer> deviceIds = new HashSet<>();
        int added = 0, updated = 0;
        for (PresetItem item : items) {
            deviceIds.add(item.presetId());
            GbPtzPreset old = existingMap.get(item.presetId());
            String name = (item.name() != null && !item.name().isBlank())
                    ? item.name() : "预置点" + item.presetId();
            if (old == null) {
                GbPtzPreset entity = new GbPtzPreset();
                entity.setChannelDbId(channelDbId);
                entity.setPresetId(item.presetId());
                entity.setName(name);
                presetMapper.insert(entity);
                added++;
            } else if (!name.equals(old.getName())) {
                old.setName(name);
                presetMapper.updateById(old);
                updated++;
            }
        }

        // 删除设备端已不存在的
        List<Long> toDelete = existing.stream()
                .filter(p -> !deviceIds.contains(p.getPresetId()))
                .map(GbPtzPreset::getId)
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            presetMapper.deleteByIds(toDelete);
        }

        log.info("[预置位查询] 同步完成 channelDbId={} 设备端={} 新增={} 删除={} 更新名称={}",
                channelDbId, items.size(), added, toDelete.size(), updated);
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public record PresetItem(int presetId, String name) {}
}
