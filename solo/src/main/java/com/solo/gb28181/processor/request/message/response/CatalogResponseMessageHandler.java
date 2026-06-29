package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceChannelService;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Catalog 响应处理（<Response><CmdType>Catalog</CmdType>...）
 * <p>设备目录会分片送回，本类负责解析单片 Item 列表，交给 {@link CatalogDataCache} 累加；
 * 达到 SumNum 时一次性 upsert 入库并回填 {@link GbDevice#getLastCatalogSyncTime()}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogResponseMessageHandler implements MessageHandler {

    private final CatalogDataCache cache;
    private final GbDeviceChannelService channelService;
    private final GbDeviceService deviceService;

    @Override
    public String getRootType() { return "Response"; }

    @Override
    public String getCmdType() { return "Catalog"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        int sn = parseInt(XmlUtils.childText(root, "SN"), -1);
        int sumNum = parseInt(XmlUtils.childText(root, "SumNum"), 0);
        if (sn < 0) {
            log.warn("[通道目录] 缺失 SN，丢弃 deviceId={}", device.getDeviceId());
            return;
        }

        if (sumNum == 0) {
            log.info("[通道目录] 设备无通道 deviceId={}", device.getDeviceId());
            channelService.deleteByDeviceDbId(device.getId());
            markSyncDone(device, 0);
            return;
        }

        Element deviceList = root.element("DeviceList");
        if (deviceList == null) {
            log.warn("[通道目录] 报文无 DeviceList 节点 deviceId={}", device.getDeviceId());
            return;
        }

        List<GbDeviceChannel> chunk = new ArrayList<>();
        Iterator<Element> it = deviceList.elementIterator("Item");
        while (it.hasNext()) {
            GbDeviceChannel channel = parseItem(it.next(), device);
            if (channel != null) chunk.add(channel);
        }

        CatalogDataCache.Batch batch = cache.accumulate(device.getDeviceId(), sn, sumNum, chunk);
        if (batch != null && batch.done) {
            channelService.saveOrUpdateBatch(batch.channels);
            markSyncDone(device, batch.channels.size());
            log.info("[通道目录] 同步完成 deviceId={} total={}", device.getDeviceId(), batch.channels.size());
        }
    }

    // ---------------- 内部 ----------------

    private GbDeviceChannel parseItem(Element item, GbDevice device) {
        String channelId = XmlUtils.childText(item, "DeviceID");
        if (channelId == null || channelId.isBlank()) return null;

        GbDeviceChannel ch = new GbDeviceChannel();
        ch.setDeviceDbId(device.getId());
        ch.setDeviceId(device.getDeviceId());
        ch.setChannelId(channelId);
        ch.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        ch.setManufacturer(emptyToNull(XmlUtils.childText(item, "Manufacturer")));
        ch.setModel(emptyToNull(XmlUtils.childText(item, "Model")));
        ch.setOwner(emptyToNull(XmlUtils.childText(item, "Owner")));
        ch.setCivilCode(emptyToNull(XmlUtils.childText(item, "CivilCode")));
        ch.setBlock(emptyToNull(XmlUtils.childText(item, "Block")));
        ch.setAddress(emptyToNull(XmlUtils.childText(item, "Address")));
        ch.setParental(parseInt(XmlUtils.childText(item, "Parental"), 0));
        ch.setParentId(emptyToNull(XmlUtils.childText(item, "ParentID")));
        ch.setSafetyWay(parseInt(XmlUtils.childText(item, "SafetyWay"), 0));
        ch.setRegisterWay(parseInt(XmlUtils.childText(item, "RegisterWay"), 1));
        ch.setCertNum(emptyToNull(XmlUtils.childText(item, "CertNum")));
        ch.setCertifiable(parseInt(XmlUtils.childText(item, "Certifiable"), 0));
        ch.setErrCode(parseIntOrNull(XmlUtils.childText(item, "ErrCode")));
        // EndTime 国标格式可能很乱，先存原值/暂不解析
        ch.setSecrecy(parseInt(XmlUtils.childText(item, "Secrecy"), 0));
        ch.setIpAddress(emptyToNull(XmlUtils.childText(item, "IPAddress")));
        ch.setPort(parseIntOrNull(XmlUtils.childText(item, "Port")));
        ch.setPassword(emptyToNull(XmlUtils.childText(item, "Password")));
        String status = XmlUtils.childText(item, "Status");
        ch.setStatus(status == null || status.isBlank() ? "OFF" : status.toUpperCase());
        ch.setLongitude(parseDouble(XmlUtils.childText(item, "Longitude")));
        ch.setLatitude(parseDouble(XmlUtils.childText(item, "Latitude")));

        // PTZType 在 Info 子节点里（GB28181-2016 9.5.2）
        Element info = item.element("Info");
        if (info != null) {
            ch.setPtzType(parseInt(XmlUtils.childText(info, "PTZType"), 0));
        }
        return ch;
    }

    private void markSyncDone(GbDevice device, int count) {
        device.setLastCatalogSyncTime(LocalDateTime.now());
        device.setChannelCount(count);
        deviceService.updateById(device);
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
