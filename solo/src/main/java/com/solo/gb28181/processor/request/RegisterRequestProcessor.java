package com.solo.gb28181.processor.request;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.processor.SipRequestProcessor;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.SipDigestUtils;
import com.solo.gb28181.utils.SipUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.AddressFactory;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.DateHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * REGISTER 请求处理器
 * <p>流程：首次 REGISTER 无 Authorization → 401 + nonce 挑战；设备带 Authorization 再发上来 → 校验 Digest → 200 OK</p>
 * <p>Expires=0 视为注销</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterRequestProcessor implements SipRequestProcessor {

    private final Gb28181Properties properties;
    private final GbDeviceService gbDeviceService;

    @Override
    public String getMethod() {
        return Request.REGISTER;
    }

    @Override
    public void process(RequestEvent event) throws Exception {
        SipProvider provider = (SipProvider) event.getSource();
        Request request = event.getRequest();
        String deviceId = SipUtils.getUserFromFromHeader(request);
        String remoteIp = SipUtils.getRemoteIp(request);
        Integer remotePort = SipUtils.getRemotePort(request);
        String transport = SipUtils.getTransport(request);

        ServerTransaction serverTransaction = event.getServerTransaction();
        if (serverTransaction == null) {
            serverTransaction = provider.getNewServerTransaction(request);
        }

        HeaderFactory headerFactory = SipFactoryHolder.headerFactory();
        MessageFactory messageFactory = SipFactoryHolder.messageFactory();
        AddressFactory addressFactory = SipFactoryHolder.addressFactory();

        AuthorizationHeader authHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        if (authHeader == null) {
            // 第一阶段：发起 401 挑战
            Response challenge = messageFactory.createResponse(Response.UNAUTHORIZED, request);
            String nonce = SipDigestUtils.genNonce();
            WWWAuthenticateHeader www = headerFactory.createWWWAuthenticateHeader("Digest");
            www.setParameter("realm", properties.getDomain());
            www.setParameter("qop", "auth");
            www.setParameter("nonce", nonce);
            www.setParameter("algorithm", "MD5");
            challenge.setHeader(www);
            serverTransaction.sendResponse(challenge);
            log.info("[GB28181] REGISTER 挑战 401: deviceId={} {}:{} nonce={}", deviceId, remoteIp, remotePort, nonce);
            return;
        }

        // 第二阶段：校验 Digest
        String username = authHeader.getUsername();
        String realm = authHeader.getRealm();
        String nonce = authHeader.getNonce();
        String uri = authHeader.getURI().toString();
        String response = authHeader.getResponse();
        String expected = SipDigestUtils.md5Digest(username, realm, properties.getPassword(),
                request.getMethod(), uri, nonce);
        if (!expected.equalsIgnoreCase(response)) {
            Response forbidden = messageFactory.createResponse(Response.FORBIDDEN, request);
            serverTransaction.sendResponse(forbidden);
            log.warn("[GB28181] REGISTER 鉴权失败: deviceId={} expected={} actual={}", deviceId, expected, response);
            return;
        }

        // 鉴权通过 → 200 OK
        Response ok = messageFactory.createResponse(Response.OK, request);

        // 回写 Date（部分设备依赖此头校时）
        DateHeader dateHeader = headerFactory.createDateHeader(java.util.Calendar.getInstance(TimeZone.getDefault()));
        ok.setHeader(dateHeader);

        // 回写 Expires
        ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        int expires;
        if (expiresHeader != null) {
            expires = expiresHeader.getExpires();
        } else {
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            expires = contact == null ? properties.getRegisterExpires() : contact.getExpires();
            if (expires < 0) expires = properties.getRegisterExpires();
        }
        ok.setHeader(headerFactory.createExpiresHeader(expires));

        serverTransaction.sendResponse(ok);

        if (expires == 0) {
            gbDeviceService.onUnregister(deviceId);
        } else {
            gbDeviceService.onRegister(deviceId, remoteIp,
                    remotePort == null ? 0 : remotePort, transport, expires);
        }
    }

    /** ISO8601 时间格式化器（GB28181 设备时间同步用，Date 头未启用时备用） */
    @SuppressWarnings("unused")
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    static {
        DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @SuppressWarnings("unused")
    private static String formatDate(Date date) {
        return DATE_FORMATTER.format(date);
    }
}
