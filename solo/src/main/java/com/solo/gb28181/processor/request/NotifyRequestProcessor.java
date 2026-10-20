package com.solo.gb28181.processor.request;

import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.response.CatalogResponseMessageHandler;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.concurrent.TimeUnit;

/**
 * NOTIFY 请求处理器
 * <p>设备订阅目录变更后推送的通知，目前只处理 Catalog，其他类型回 200 OK 忽略。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private static final String LOG_TAG = "[目录通知]";

    private final CatalogResponseMessageHandler catalogHandler;
    private final GbDeviceService gbDeviceService;
    private final GbPlatformService gbPlatformService;

    @Autowired
    private RedisTemplate<String, Object> deviceProfileRedisTemplate;

    private static final long DEVICE_PROFILE_TTL_MINUTES = 10L;

    @Override
    public String getMethod() {
        return Request.NOTIFY;
    }

    @Override
    public void process(RequestEvent event) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String fromUser = SipUtils.getUserIdFromFromHeader(request);

        if (gbPlatformService.isBlocked(fromUser)) {
            log.warn("{} 命中禁用名单，静默丢弃 NOTIFY: from={}", LOG_TAG, fromUser);
            return;
        }

        Element root = getRootElement(request);
        if (root == null) {
            log.warn("{} 报文体为空或解析失败 from={}", LOG_TAG, fromUser);
            safeResponse(request, Response.BAD_REQUEST, "empty or malformed xml");
            return;
        }

        String cmdType = XmlUtils.childText(root, "CmdType");
        if (cmdType.isEmpty()) {
            log.warn("{} 缺失 CmdType from={}", LOG_TAG, fromUser);
            safeResponse(request, Response.BAD_REQUEST, "missing CmdType");
            return;
        }

        GbDevice device = lookupDevice(fromUser);
        if (device == null) {
            log.debug("{} 未注册设备 NOTIFY，回 200 from={} cmdType={}", LOG_TAG, fromUser, cmdType);
            safeResponse(request, Response.OK, null);
            return;
        }

        if ("Catalog".equalsIgnoreCase(cmdType)) {
            handleCatalog(event, device, root);
        } else {
            log.info("{} 未处理的 CmdType={} from={}，回 200 OK", LOG_TAG, cmdType, fromUser);
            safeResponse(request, Response.OK, null);
        }
    }

    private void handleCatalog(RequestEvent event, GbDevice device, Element root) {
        // 先回 200，避免设备重传
        SIPRequest request = (SIPRequest) event.getRequest();
        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("{} Catalog NOTIFY 回 200 失败 deviceId={}: {}",
                    LOG_TAG, device.getDeviceId(), e.getMessage());
        }

        // NOTIFY Catalog body 和 MESSAGE Response/Catalog 结构一致，直接复用
        log.info("{} 收到 Catalog NOTIFY deviceId={}", LOG_TAG, device.getDeviceId());
        try {
            catalogHandler.handleForDevice(event, device, root);
        } catch (Exception e) {
            log.error("{} Catalog NOTIFY 处理异常 deviceId={}",
                    LOG_TAG, device.getDeviceId(), e);
        }
    }

    private GbDevice lookupDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return null;
        String key = GbRedisKeys.DEVICE_PROFILE_PREFIX + deviceId;
        try {
            Object cached = deviceProfileRedisTemplate.opsForValue().get(key);
            if (cached instanceof GbDevice d) {
                return d;
            }
        } catch (Exception e) {
            log.warn("{} 设备缓存读取异常 deviceId={}: {}", LOG_TAG, deviceId, e.getMessage());
        }
        GbDevice device = gbDeviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, deviceId)
                .one();
        if (device != null) {
            try {
                deviceProfileRedisTemplate.opsForValue()
                        .set(key, device, DEVICE_PROFILE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception ignore) {
                // ignore
            }
        }
        return device;
    }

    private void safeResponse(SIPRequest request, int statusCode, String reason) {
        try {
            responseAck(request, statusCode, reason);
        } catch (Exception e) {
            log.warn("{} 回 {} 失败 reason={} : {}", LOG_TAG, statusCode, reason, e.getMessage());
        }
    }
}
