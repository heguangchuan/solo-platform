package com.solo.gb28181.processor.request.message.notify;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.impl.GbPlatformBroadcastService;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;


@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastNotifyMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private final GbPlatformBroadcastService platformBroadcastService;

    @Override
    public String getRootType() {
        return "Notify";
    }

    @Override
    public String getCmdType() {
        return "Broadcast";
    }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 设备不会主动给平台发喊话通知，忽略
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        String channelId = XmlUtils.childText(root, "DeviceID");
        String snText = XmlUtils.childText(root, "SN");
        int sn = 0;
        if (snText != null && !snText.isBlank()) {
            try {
                sn = Integer.parseInt(snText.trim());
            } catch (NumberFormatException e) {
                log.warn("[喊话通知] SN 解析失败 上级:{} SN:{}", platform.getServerGbId(), snText);
            }
        }
        if (channelId == null || channelId.isBlank()) {
            log.warn("[喊话通知] 缺少 DeviceID，忽略 上级:{}", platform.getServerGbId());
            return;
        }
        platformBroadcastService.onBroadcastNotify(platform, channelId, sn);
    }
}
