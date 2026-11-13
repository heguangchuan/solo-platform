package com.solo.gb28181.processor.request;

import com.solo.gb28181.auth.DigestServerAuthenticationHelper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.event.device.DeviceOfflineEvent;
import com.solo.gb28181.event.device.DeviceOnlineEvent;
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
 * REGISTER 请求处理器。
 * 处理分支：
 * 1. 没带 Authorization → 401 发挑战（密码不为空才挑战，空密码直接放行）
 * 2. 带了 Authorization 但鉴权失败 → 401 重新挑战
 * 3. 鉴权通过 + Expires=0 → 200 + 注销下线
 * 4. 鉴权通过 + Expires>0 → 200 + 注册上线
 * 注意：
 * - 响应带 GB-Date 头，部分设备靠这个校时
 * - 响应带原 Contact 头（RFC 3261 要求）
 * - 设备地址走 NAT 真实地址，别用 From 头里的内网 IP
 * - 禁用名单（gb_platform.enabled=0）直接静默丢弃，不回响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterRequestProcessor extends SipRequestProcessorBase implements SipRequestProcessor {

    private final Gb28181Properties properties;
    private final GbDeviceService gbDeviceService;
    private final GbPlatformService gbPlatformService;
    private final ApplicationEventPublisher eventPublisher;
    private final DigestServerAuthenticationHelper digestAuthHelper;

    @Override
    public String getMethod() {
        return Request.REGISTER;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SIPRequest request = (SIPRequest) event.getRequest();
        String deviceId = SipUtils.getUserIdFromFromHeader(request);

        // 部分设备没带 Expires 头，取配置的默认值
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

        // 黑名单拦截：
        // gb_platform 现在是"禁用名单"——设备和下级平台默认自由注册，
        // 只有在表里登记且 enabled=0 的国标ID才拦。鉴权走 gb28181.password，跟这表没关系。
        // 命中了直接静默丢弃，啥响应都不回（回 403 反而告诉对方"这个 ID 我们认识"），
        // 也不入库不发事件。对方自己按 SIP 重传去吧，我们这边零状态。
        // MESSAGE 入口也有同样的拦截，防止"先注册后拉黑"的设备继续推目录/心跳。
        if (gbPlatformService.isBlocked(deviceId)) {
            log.warn("[设备注册] 命中禁用名单，静默丢弃 REGISTER: deviceId={}", deviceId);
            return;
        }
        String password = properties.getPassword();
        String title = isUnregister ? "[设备注销]" : "[设备注册]";
        log.info("{} deviceId={} from={}", title, deviceId, requestAddress);

        // 非空密码启用严格 Digest 鉴权。首次请求、错误摘要、伪造或过期 nonce 都重新发 401，
        // 并在查询/修改设备档案、发送上下线事件之前返回，失败请求不能影响现有会话。
        if (password != null && !password.isBlank()) {
            boolean hasAuthorization = request.getHeader(javax.sip.header.AuthorizationHeader.NAME) != null;
            if (!hasAuthorization
                    || !digestAuthHelper.authenticate(request, deviceId, properties.getDomain(), password)) {
                SIPResponse challenge = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.UNAUTHORIZED, request);
                digestAuthHelper.appendChallenge(HEADER_FACTORY, challenge, deviceId, properties.getDomain());
                sipSender.send(request.getLocalAddress().getHostAddress(), challenge);
                if (hasAuthorization) {
                    log.warn("{} Digest 鉴权失败，已重新发起 401 挑战: deviceId={} from={}",
                            title, deviceId, requestAddress);
                } else {
                    log.info("{} deviceId={} 回复 401 挑战", title, deviceId);
                }
                return;
            }
        }

        // 鉴权通过后才允许读取并更新设备档案。
        GbDevice exist = gbDeviceService.lambdaQuery().eq(GbDevice::getDeviceId, deviceId).one();

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
