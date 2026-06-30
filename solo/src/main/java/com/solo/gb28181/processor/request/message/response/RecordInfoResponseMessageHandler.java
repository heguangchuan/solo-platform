package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.response.RecordItemResponse;
import com.solo.gb28181.service.GbPlaybackService;
import com.solo.gb28181.utils.XmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * RecordInfo 响应处理（{@code <Response><CmdType>RecordInfo</CmdType>...}）
 *
 * <p>设备录像列表会分片送回（每片若干 Item，分多个 MESSAGE），本类负责：</p>
 * <ol>
 *   <li>解析单片 Item 列表（{@code <RecordList><Item>...} 节点）</li>
 *   <li>交给 {@link RecordInfoDataCache} 按 {@code (deviceId, sn)} 累积</li>
 *   <li>达到 SumNum 时回调 {@link GbPlaybackService#onRecordInfoReceived}，完成等待中的 future</li>
 * </ol>
 *
 * <p>对齐 wvp {@code RecordInfoResponseMessageHandler} (event/record + RedisHash 实现)；
 * 本平台用 in-memory cache 替代 Redis，足够 — 单条 RecordInfo 查询生命周期通常只有几秒。</p>
 */
@Slf4j
@Component
public class RecordInfoResponseMessageHandler implements MessageHandler {

    @Autowired
    private RecordInfoDataCache cache;

    /**
     * 用 @Lazy 避免循环依赖：
     * RecordInfoResponseMessageHandler → GbPlaybackService → SipCommander → SipSender → SipLayer
     * → SipProcessorObserver → List&lt;MessageHandler&gt; → RecordInfoResponseMessageHandler
     */
    @Autowired
    @Lazy
    private GbPlaybackService playbackService;

    @Override
    public String getRootType() { return "Response"; }

    @Override
    public String getCmdType() { return "RecordInfo"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        int sn = parseInt(XmlUtils.childText(root, "SN"), -1);
        int sumNum = parseInt(XmlUtils.childText(root, "SumNum"), 0);
        if (sn < 0) {
            log.warn("[录像查询] 缺失 SN，丢弃 deviceId={}", device.getDeviceId());
            return;
        }

        if (sumNum == 0) {
            log.info("[录像查询] 该时段无录像 deviceId={} sn={}", device.getDeviceId(), sn);
            playbackService.onRecordInfoReceived(device.getDeviceId(), sn, new ArrayList<>());
            return;
        }

        Element recordList = root.element("RecordList");
        if (recordList == null) {
            log.warn("[录像查询] 报文无 RecordList 节点 deviceId={} sn={}", device.getDeviceId(), sn);
            return;
        }

        List<RecordItemResponse> chunk = new ArrayList<>();
        Iterator<Element> it = recordList.elementIterator("Item");
        while (it.hasNext()) {
            RecordItemResponse item = parseItem(it.next());
            if (item != null) chunk.add(item);
        }

        RecordInfoDataCache.Batch batch = cache.accumulate(device.getDeviceId(), sn, sumNum, chunk);
        if (batch != null && batch.done) {
            List<RecordItemResponse> items = batch.items();
            log.info("[录像查询] 分片合并完成 deviceId={} sn={} total={}", device.getDeviceId(), sn, items.size());
            playbackService.onRecordInfoReceived(device.getDeviceId(), sn, items);
        }
    }

    private RecordItemResponse parseItem(Element item) {
        String channelId = XmlUtils.childText(item, "DeviceID");
        if (channelId == null || channelId.isBlank()) return null;

        RecordItemResponse it = new RecordItemResponse();
        it.setChannelId(channelId);
        it.setName(emptyToNull(XmlUtils.childText(item, "Name")));
        it.setFilePath(emptyToNull(XmlUtils.childText(item, "FilePath")));
        it.setAddress(emptyToNull(XmlUtils.childText(item, "Address")));
        it.setStartTime(emptyToNull(XmlUtils.childText(item, "StartTime")));
        it.setEndTime(emptyToNull(XmlUtils.childText(item, "EndTime")));
        it.setFileSize(parseLongOrNull(XmlUtils.childText(item, "FileSize")));
        it.setType(emptyToNull(XmlUtils.childText(item, "Type")));
        it.setRecorderId(emptyToNull(XmlUtils.childText(item, "RecorderID")));
        it.setSecrecy(parseInt(XmlUtils.childText(item, "Secrecy"), 0));
        return it;
    }

    private static int parseInt(String v, int defaultVal) {
        if (v == null || v.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static Long parseLongOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
