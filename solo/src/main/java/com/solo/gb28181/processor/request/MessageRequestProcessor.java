package com.solo.gb28181.processor.request;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MESSAGE 请求总入口
 * <p>两级分发：根元素名（Notify/Query/Control/Response）→ CmdType（Keepalive/Catalog/DeviceInfo/...）</p>
 * <p>所有 {@link MessageHandler} 实现会被 Spring 自动注入，{@code @PostConstruct} 中注册到映射表。</p>
 */
@Slf4j
@Component
public class MessageRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired(required = false)
    private List<MessageHandler> handlerBeans;

    @Autowired
    private GbDeviceService gbDeviceService;

    /** rootType -> cmdType -> handler */
    private final Map<String, Map<String, MessageHandler>> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        if (handlerBeans == null) return;
        for (MessageHandler h : handlerBeans) {
            handlerMap.computeIfAbsent(h.getRootType(), k -> new HashMap<>())
                    .put(h.getCmdType(), h);
            log.info("[消息分发] 注册 MESSAGE handler: {}/{} -> {}",
                    h.getRootType(), h.getCmdType(), h.getClass().getSimpleName());
        }
    }

    @Override
    public String getMethod() {
        return Request.MESSAGE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        String deviceId = SipUtils.getUserIdFromFromHeader(request);

        Element root = XmlUtils.parseRoot(request.getRawContent());
        if (root == null) {
            log.warn("[消息分发] MESSAGE 报文体为空或解析失败 deviceId={}", deviceId);
            responseAck(request, Response.BAD_REQUEST, "empty or malformed xml");
            return;
        }

        String rootName = root.getName();
        String cmdType = XmlUtils.childText(root, "CmdType");

        GbDevice device = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();

        Map<String, MessageHandler> byCmd = handlerMap.get(rootName);
        MessageHandler handler = byCmd == null ? null : byCmd.get(cmdType);

        if (handler == null) {
            log.info("[消息分发] 未注册的 MESSAGE 子命令: rootType={} cmdType={} deviceId={}", rootName, cmdType, deviceId);
            // 即使不处理也要回 200，避免设备无穷重传
            responseAck(request, Response.OK);
            return;
        }

        try {
            if (device != null) {
                handler.handleForDevice(event, device, root);
            } else if ("Keepalive".equals(cmdType)) {
                // 平台不认识该设备（如已被删除）→ 对心跳回 403，逼设备重新 REGISTER，实现删除后快速自愈
                responseAck(request, Response.FORBIDDEN, "device not registered");
                log.warn("[消息分发] 未知设备心跳，回 403 触发重注册: deviceId={}", deviceId);
            } else {
                // 其他子命令：设备未注册，先回 200 再丢弃，避免设备无穷重传
                log.warn("[消息分发] 未注册设备发送 MESSAGE: deviceId={} cmd={}", deviceId, cmdType);
                responseAck(request, Response.OK);
            }
        } catch (Exception e) {
            log.error("[消息分发] MESSAGE handler 异常: rootType={} cmdType={} deviceId={}",
                    rootName, cmdType, deviceId, e);
        }
    }
}
