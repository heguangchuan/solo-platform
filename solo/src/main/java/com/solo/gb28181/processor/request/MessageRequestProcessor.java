package com.solo.gb28181.processor.request;

import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.utils.SipUtils;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MESSAGE 请求总入口
 * <p>两级分发：根元素名（Notify/Query/Control/Response）→ CmdType（Keepalive/Catalog/DeviceInfo/...）</p>
 * <p>所有 {@link MessageHandler} 实现会被 Spring 自动注入，{@code @PostConstruct} 中注册到映射表。</p>
 * <p>发送方身份识别：先按 fromUser 查 {@link GbDevice}（下级设备/下级级联平台）；未命中再查
 * {@link GbParentPlatform#getServerGbId()}（本平台作为下级挂上级场景，上级发来的 Query/Notify）。</p>
 *
 * <h3>未注册来源处理策略</h3>
 * <ul>
 *   <li>有设备：调 {@link MessageHandler#handleForDevice}，handler 内部按需打日志。</li>
 *   <li>有平台：调 {@link MessageHandler#handleForPlatform}，handler 内部按需打日志。</li>
 *   <li>设备/平台都不在库，按 cmdType 分支：</li>
 *   <li>　<b>Catalog</b>：完全静默——不打日志、不发 SIP 响应。NVR 每 3-4s 一个分片，
 *       持续发会刷屏；GB 协议未规定"未注册"目录订阅的应答，设备按自身重传机制处理。</li>
 *   <li>　<b>Keepalive</b>：回 403 逼设备重新 REGISTER，实现删除后快速自愈，打 WARN 留痕。</li>
 *   <li>　<b>其他</b>：回 200 OK 避免设备重传，打 INFO 留痕。</li>
 *   <li>报文解析失败：记 WARN + 回 400（语法层错误要告诉对方）。</li>
 *   <li>未注册的根元素/CmdType（无 handler）：回 200 OK 让设备别重传。</li>
 * </ul>
 */
@Slf4j
@Component
public class MessageRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    @Autowired(required = false)
    private List<MessageHandler> handlerBeans;

    @Autowired
    private GbDeviceService gbDeviceService;

    @Autowired
    private GbParentPlatformService gbParentPlatformService;

    /** 接入禁用名单：SIP 入口拦截"先注册后拉黑"的设备，走 Redis Set 缓存不打 SQL */
    @Autowired
    private GbPlatformService gbPlatformService;

    /**
     * 设备档案缓存。catalog 同步期间 4w+ 条 MESSAGE 都按 {@code deviceId} 查设备，
     * 走缓存避免每条都打 SQL。TTL 10 min，写路径（online/offline/updateByManual/deleteBatch/
     * CatalogSyncSupport final flush）已主动失效。
     */
    @Autowired
    private RedisTemplate<String, Object> deviceProfileRedisTemplate;

    /** 设备档案缓存 TTL：10 分钟。catalog 同步期间只需首条 miss 走 DB。 */
    private static final long DEVICE_PROFILE_TTL_MINUTES = 10L;

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
        String fromUser = SipUtils.getUserIdFromFromHeader(request);

        // 黑名单拦截（在解析报文体之前，省掉被拉黑设备的 XML 解析开销）：
        // 挡住"先注册过、后被拉黑"的设备——gb_device 里还留着行，不拦就会继续被当合法设备
        // 处理 Catalog / Keepalive。静默丢弃，不回 SIP、不落库，与 REGISTER 入口一致。
        if (gbPlatformService.isBlocked(fromUser)) {
            log.warn("[消息分发] 命中禁用名单，静默丢弃 MESSAGE: from={}", fromUser);
            return;
        }

        Element root = XmlUtils.parseRoot(request.getRawContent());
        if (root == null) {
            log.warn("[消息分发] MESSAGE 报文体为空或解析失败 from={}", fromUser);
            responseAck(request, Response.BAD_REQUEST, "empty or malformed xml");
            return;
        }

        String rootName = root.getName();
        String cmdType = XmlUtils.childText(root, "CmdType");

        Map<String, MessageHandler> byCmd = handlerMap.get(rootName);
        MessageHandler handler = byCmd == null ? null : byCmd.get(cmdType);

        if (handler == null) {
            // 未注册的子命令：回 200 避免设备重传，不打日志
            responseAck(request, Response.OK);
            return;
        }

        // 先按 fromUser 查设备
        GbDevice device = lookupDeviceCached(fromUser);
        if (device != null) {
            try {
                handler.handleForDevice(event, device, root);
            } catch (Exception e) {
                log.error("[消息分发] MESSAGE handler 异常: rootType={} cmdType={} from={}",
                        rootName, cmdType, fromUser, e);
            }
            return;
        }

        // 设备未命中：按 fromUser 反查上级平台（本平台作为下级挂上级时，上级发来的 Query/Notify 走这条分支）
        GbParentPlatform platform = gbParentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, fromUser)
                .eq(GbParentPlatform::getEnabled, 1)
                .one();
        if (platform != null) {
            try {
                handler.handleForPlatform(event, platform, root);
            } catch (Exception e) {
                log.error("[消息分发] MESSAGE handler 异常: rootType={} cmdType={} from={}",
                        rootName, cmdType, fromUser, e);
            }
            return;
        }

        // 设备和平台都不在库：按 cmdType 分支处理
        if ("Catalog".equals(cmdType)) {
            // 目录订阅未注册：完全静默——不打日志、不发响应。
            // NVR 每 3-4s 一个分片，未注册时持续刷屏；GB/T 28181 未规定"未注册"目录订阅的应答，
            // 平台不响应是合法选择，设备按 SIP 自身重传机制处理。
            return;
        }
        if ("Keepalive".equals(cmdType)) {
            // 心跳未注册：回 403 逼设备重新 REGISTER，实现删除后快速自愈
            responseAck(request, Response.FORBIDDEN, "device not registered");
            log.warn("[消息分发] 未知设备心跳，回 403 触发重注册: from={}", fromUser);
            return;
        }
        // 其他子命令：回 200 避免设备重传，打 INFO 留痕
        log.info("[消息分发] 未注册来源发送 MESSAGE: from={} cmd={}", fromUser, cmdType);
        responseAck(request, Response.OK);
    }

    /**
     * 按 {@code deviceId} 查设备，优先走 {@link GbRedisKeys#DEVICE_PROFILE_PREFIX} 缓存。
     * <p>catalog 同步期间 4w+ 条 MESSAGE 都按 {@code fromUser} 查设备，缓存命中后零 SQL。
     * 缓存 miss 才走 DB，写回缓存。Redis 不可用时静默回退到 DB（不影响 SIP 接收）。</p>
     */
    private GbDevice lookupDeviceCached(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return null;
        String key = GbRedisKeys.DEVICE_PROFILE_PREFIX + deviceId;
        // 1) 查缓存
        try {
            Object cached = deviceProfileRedisTemplate.opsForValue().get(key);
            if (cached instanceof GbDevice hit) {
                return hit;
            }
        } catch (Exception e) {
            log.warn("[消息分发] 设备档案缓存读取失败，回退到 DB deviceId={} err={}", deviceId, e.getMessage());
        }
        // 2) miss / 异常 → 查 DB
        GbDevice device = gbDeviceService.lambdaQuery()
                .eq(GbDevice::getDeviceId, deviceId)
                .one();
        // 3) 回写缓存（仅命中时写；查不到不写负缓存，避免对端未注册时反复打 SQL）
        if (device != null) {
            try {
                deviceProfileRedisTemplate.opsForValue().set(key, device, DEVICE_PROFILE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("[消息分发] 设备档案缓存写入失败 deviceId={} err={}", deviceId, e.getMessage());
            }
        }
        return device;
    }
}
