package com.solo.gb28181.processor.request;

import com.solo.gb28181.auth.DigestServerAuthenticationHelper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.event.DeviceOfflineEvent;
import com.solo.gb28181.event.DeviceOnlineEvent;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.service.GbPlatformService;
import com.solo.gb28181.sip.bean.GbSipDate;
import com.solo.gb28181.utils.SipUtils;
import gov.nist.javax.sip.header.SIPDateHeader;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.header.ContactHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Calendar;
import java.util.Locale;

/**
 * REGISTER 请求处理器
 *
 * <p>分支：</p>
 * <ol>
 *   <li><b>无 Authorization</b> → 401 + 挑战</li>
 *   <li><b>有 Authorization & 鉴权失败</b> → 403</li>
 *   <li><b>Expires=0 & 鉴权通过</b> → 200 + offline</li>
 *   <li><b>Expires>0 & 鉴权通过</b> → 200 + online</li>
 *   <li><b>同 Call-Id 续订（如果将来支持持久化 SipTransactionInfo）</b> → 跳过挑战直接 200</li>
 * </ol>
 *
 * <p>响应要点：</p>
 * <ul>
 *   <li>带 GB-Date 头（ISO8601，部分设备依此校时）</li>
 *   <li>带原 Contact 头（按 RFC 3261）</li>
 *   <li>带原 Expires 头</li>
 *   <li>设备地址走 NAT 真实地址（Via.received/rport 兜底 socket 源）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private final Gb28181Properties properties;
    private final GbDeviceService gbDeviceService;
    private final GbPlatformService gbPlatformService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getMethod() {
        return Request.REGISTER;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        String deviceId = SipUtils.getUserIdFromFromHeader(request);

        // 取 Expires，部分设备没带 Expires 头时用配置默认值
        int expires = properties.getRegisterExpires();
        if (request.getExpires() != null) {
            expires = request.getExpires().getExpires();
        } else {
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            if (contact != null && contact.getExpires() >= 0) expires = contact.getExpires();
        }
        boolean isUnregister = (expires == 0);

        // 取 NAT 真实地址
        SipUtils.RemoteAddressInfo remote = SipUtils.getRemoteAddress(request, properties.isUseSourceIpAsRemoteAddress());
        String requestAddress = remote.hostAddress();

        // 黑名单拦截：gb_platform 现在是"禁用名单"——设备与下级平台默认自由注册，
        // 只有在本表登记且 enabled=0 的国标 ID 才被拦。鉴权与本表无关（走 gb28181.password）。
        // 命中直接静默丢弃：不回任何 SIP 响应（403 反而确认了"这个 ID 被我们识别"），
        // 不入库、不发事件。对端按自身 SIP 重传机制处理，本平台零状态。
        // MESSAGE 入口有同样的拦截，挡住"先注册后拉黑"的设备继续推目录/心跳。
        if (gbPlatformService.isBlocked(deviceId)) {
            log.warn("[设备注册] 命中禁用名单，静默丢弃 REGISTER: deviceId={}", deviceId);
            return;
        }
        // 鉴权密码统一取 gb28181.password（见 ADR-022）
        String password = properties.getPassword();
        String title = isUnregister ? "[设备注销]" : "[设备注册]";
        log.info("{} deviceId={} from={}", title, deviceId, requestAddress);
        GbDevice exist = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();

        // 401 挑战：仅当设备没带 Authorization 且全局密码非空时发出。空密码 = 开放模式，零挑战。
        if (request.getHeader(javax.sip.header.AuthorizationHeader.NAME) == null && password != null && !password.isBlank()) {
            SIPResponse challenge = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.UNAUTHORIZED, request);
            new DigestServerAuthenticationHelper().appendChallenge(HEADER_FACTORY, challenge, properties.getDomain());
            sipSender.send(request.getLocalAddress().getHostAddress(), challenge);
            log.info("{} deviceId={} 回复 401 挑战", title, deviceId);
            return;
        }

        // 不再校验 Digest 摘要 — "不管下级或者设备传什么 我方都应当放行"（见 ADR-022）。
        // 设备带 Authorization 过来即视为鉴权通过；空密码场景下连 401 都不会发。
        if (request.getHeader(javax.sip.header.AuthorizationHeader.NAME) != null) {
            log.debug("{} deviceId={} 接受 Authorization 头，跳过 Digest 校验", title, deviceId);
        }

        // 鉴权通过 → 构造 200 OK
        SIPResponse response = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.OK, request);
        // 加 GB-Date 头
        SIPDateHeader dateHeader = new SIPDateHeader();
        dateHeader.setDate(new GbSipDate(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis()));
        response.addHeader(dateHeader);
        // 回写 Contact / Expires（按 RFC 3261）
        ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
        if (contact != null) response.addHeader(contact);
        if (request.getExpires() != null) response.addHeader(request.getExpires());

        sipSender.send(request.getLocalAddress().getHostAddress(), response);

        if (isUnregister) {
            // 分支 3：注销
            gbDeviceService.offline(deviceId, "主动注销");
            eventPublisher.publishEvent(new DeviceOfflineEvent(this, deviceId, "主动注销"));
            return;
        }

        // 分支 4：注册成功 → 入库 / 上线
        boolean firstTime = (exist == null);
        GbDevice device = exist != null ? exist : new GbDevice();
        device.setDeviceId(deviceId);

        // 如果设备已经预先配置了收流IP覆盖（双栈内网场景），强制使用配置的 IP 而不是注册源 IP
        String targetIp = device.getRecvIp() != null && !device.getRecvIp().isBlank()
                ? device.getRecvIp()
                : remote.ip();
        if (device.getRecvIp() != null && !device.getRecvIp().isBlank()) {
            log.info("[设备注册] 设备 {} 配置了收流IP覆盖：原地址(注册源) = {}，新地址(覆盖) = {}",
                    deviceId, remote.ip(), device.getRecvIp());
        }
        device.setIp(targetIp);
        device.setPort(remote.port());

        // 重建 hostAddress：ip:port 格式
        String hostAddress = targetIp + ":" + remote.port();
        device.setHostAddress(hostAddress);

        device.setLocalIp(request.getLocalAddress().getHostAddress());
        device.setExpires(expires);

        ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        device.setTransport(via != null && "TCP".equalsIgnoreCase(via.getTransport()) ? "TCP" : "UDP");

        if (device.getCharset() == null || device.getCharset().isBlank()) {
            device.setCharset("GB2312");
        }
        // 设备与下级平台不再区分：platform_id 已从 gb_device 移除，source 统一 REGISTER。
        // name 不在此处兜底——REGISTER 报文不带设备名，由 DeviceOnlineListener 异步查
        // DeviceInfo 后用 reportName 回填（见 DeviceInfoResponseMessageHandler）。
        gbDeviceService.online(device);

        // 发布上线事件 → DeviceOnlineListener 异步拉取 DeviceInfo + Catalog
        eventPublisher.publishEvent(new DeviceOnlineEvent(this, deviceId, firstTime));
    }
}
