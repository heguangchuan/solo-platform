package com.solo.gb28181.processor.request;

import com.solo.gb28181.auth.DigestServerAuthenticationHelper;
import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.event.DeviceOnlineEvent;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.service.GbDeviceService;
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
        String title = isUnregister ? "[设备注销]" : "[设备注册]";
        log.info("{} deviceId={} from={}", title, deviceId, requestAddress);

        // 取设备已存在记录 + 设备级密码
        GbDevice exist = gbDeviceService.getByDeviceId(deviceId);
        String password = properties.getPassword(); // 当前模型暂不支持设备级密码字段，预留扩展

        // 校验 Digest
        if (request.getHeader(javax.sip.header.AuthorizationHeader.NAME) == null && password != null && !password.isBlank()) {
            // 分支 1：发起 401 挑战
            SIPResponse challenge = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.UNAUTHORIZED, request);
            new DigestServerAuthenticationHelper().appendChallenge(HEADER_FACTORY, challenge, properties.getDomain());
            sipSender.send(request.getLocalAddress().getHostAddress(), challenge);
            log.info("{} deviceId={} 回复 401 挑战", title, deviceId);
            return;
        }

        boolean pwdOk = password == null || password.isBlank()
                || new DigestServerAuthenticationHelper().authenticate(request, password);
        if (!pwdOk) {
            // 分支 2：鉴权失败
            SIPResponse forbidden = (SIPResponse) MESSAGE_FACTORY.createResponse(Response.FORBIDDEN, request);
            forbidden.setReasonPhrase("wrong password");
            sipSender.send(request.getLocalAddress().getHostAddress(), forbidden);
            log.warn("{} deviceId={} 鉴权失败", title, deviceId);
            return;
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
            return;
        }

        // 分支 4：注册成功 → 入库 / 上线
        boolean firstTime = (exist == null);
        GbDevice device = exist != null ? exist : new GbDevice();
        device.setDeviceId(deviceId);
        device.setSipDomain(properties.getDomain());
        device.setIp(remote.ip());
        device.setPort(remote.port());
        device.setHostAddress(remote.hostAddress());
        device.setLocalIp(request.getLocalAddress().getHostAddress());
        device.setExpires(expires);

        ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        device.setTransport(via != null && "TCP".equalsIgnoreCase(via.getTransport()) ? "TCP" : "UDP");

        if (device.getCharset() == null || device.getCharset().isBlank()) {
            device.setCharset("GB2312");
        }
        gbDeviceService.online(device);

        // 发布上线事件 → DeviceOnlineListener 异步拉取 DeviceInfo + Catalog
        eventPublisher.publishEvent(new DeviceOnlineEvent(this, deviceId, firstTime));
    }
}
