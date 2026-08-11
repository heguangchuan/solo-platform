package com.solo.gb28181.processor.request.message.notify;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keepalive 心跳处理
 * <p>大规模设备同时心跳会打爆 DB，所以采用<b>队列削峰 + 定时批处理</b>模式：
 * 收到心跳时直接入队（200 OK 已由 {@code MessageRequestProcessor} 在分发前回复），
 * {@link com.solo.gb28181.task.GbScheduledTasks} 每 100ms 调 {@link #drain()} 把队列里的批量处理。</p>
 *
 * <p>海康兼容：海康某些设备 Keepalive 报文 From 字段是通道 ID 而不是设备 ID。
 * 后续 Sprint 加入 SsrcTransaction 后可以反查；当前阶段直接以 deviceId 查不到时按设备 ID 长度兜底
 * （20 位是设备 ID，否则跳过本次心跳）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeepaliveNotifyMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private static final int MAX_QUEUE_SIZE = 50_000;

    private final GbDeviceService gbDeviceService;
    private final Gb28181Properties properties;

    private final ConcurrentLinkedQueue<KeepaliveTask> queue = new ConcurrentLinkedQueue<>();

    @Override
    public String getRootType() { return "Notify"; }

    @Override
    public String getCmdType() { return "Keepalive"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        if (queue.size() >= MAX_QUEUE_SIZE) {
            log.warn("[设备心跳] 队列已满（{}），丢弃当前心跳: device={}", MAX_QUEUE_SIZE, device.getDeviceId());
            return;
        }
        queue.offer(new KeepaliveTask(event, device));
    }

    public void drain() {
        if (queue.isEmpty()) return;
        List<KeepaliveTask> batch = new ArrayList<>(Math.min(queue.size(), 200));
        for (int i = 0; i < 200; i++) {
            KeepaliveTask t = queue.poll();
            if (t == null) break;
            batch.add(t);
        }
        if (batch.isEmpty()) return;

        for (KeepaliveTask task : batch) {
            try {
                processOne(task);
            } catch (Exception e) {
                log.error("[设备心跳] 处理异常 deviceId={}", task.device.getDeviceId(), e);
            }
        }
    }

    private void processOne(KeepaliveTask task) throws Exception {
        SIPRequest request = (SIPRequest) task.event.getRequest();
        // 200 OK 已由 MessageRequestProcessor 在分发前统一回复，这里不重复回

        GbDevice device = task.device;
        SipUtils.RemoteAddressInfo remote = SipUtils.getRemoteAddress(request,
                properties.isUseSourceIpAsRemoteAddress());

        // 设备地址有变化（NAT 重新打洞 / 切换网络）→ 同步更新
        if (!remote.ip().equalsIgnoreCase(device.getIp())
                || device.getPort() == null || remote.port() != device.getPort()) {
            log.info("[设备地址] 变化: {} {}:{} -> {}:{}",
                    device.getDeviceId(),
                    device.getIp(), device.getPort(),
                    remote.ip(), remote.port());
            device.setIp(remote.ip());
            device.setPort(remote.port());
            device.setHostAddress(remote.hostAddress());
            device.setLocalIp(request.getLocalAddress().getHostAddress());
        }

        gbDeviceService.keepalive(device);
    }

    /** 入队任务结构 */
    private record KeepaliveTask(RequestEvent event, GbDevice device) { }
}
