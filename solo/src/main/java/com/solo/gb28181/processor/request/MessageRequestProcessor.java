package com.solo.gb28181.processor.request;

import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * MESSAGE 请求处理器
 * <p>GB28181 用 SIP MESSAGE 承载业务子命令，CmdType 决定分发：
 * Keepalive / Catalog / DeviceInfo / DeviceStatus / RecordInfo / Alarm / MobilePosition / Broadcast / MediaStatus …</p>
 * <p>MVP 阶段先处理 Keepalive，其余统一回 200 OK + 打印日志，后续 Sprint 逐步补齐</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRequestProcessor implements SipRequestProcessor {

    private final GbDeviceService gbDeviceService;

    @Override
    public String getMethod() {
        return Request.MESSAGE;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SipProvider provider = (SipProvider) event.getSource();
        Request request = event.getRequest();
        ServerTransaction tx = event.getServerTransaction();
        if (tx == null) {
            tx = provider.getNewServerTransaction(request);
        }

        // 先无条件回 200 OK，避免设备超时重传（XML 业务在异步逻辑里处理）
        MessageFactory messageFactory = SipFactoryHolder.messageFactory();
        Response ok = messageFactory.createResponse(Response.OK, request);
        tx.sendResponse(ok);

        // 解析 XML
        byte[] content = request.getRawContent();
        Document document = XmlUtils.parse(content);
        if (document == null) {
            return;
        }
        Element root = document.getRootElement();
        String cmdType = XmlUtils.getChildText(root, "CmdType");
        String deviceId = SipUtils.getUserFromFromHeader(request);

        switch (cmdType) {
            case "Keepalive" -> gbDeviceService.onKeepalive(deviceId);
            case "Catalog", "DeviceInfo", "DeviceStatus", "RecordInfo",
                    "Alarm", "MobilePosition", "Broadcast", "MediaStatus" -> {
                log.info("[GB28181] 收到 {} 报文（暂未处理）: deviceId={}", cmdType, deviceId);
            }
            default -> log.info("[GB28181] 收到未知 MESSAGE CmdType={}: deviceId={}", cmdType, deviceId);
        }
    }
}
