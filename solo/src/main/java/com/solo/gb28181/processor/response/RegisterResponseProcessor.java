package com.solo.gb28181.processor.response;

import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipResponseProcessor;
import com.solo.gb28181.service.GbParentPlatformService;
import com.solo.gb28181.service.PlatformRegisterService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.PlatformRegisterState;
import com.solo.gb28181.transmit.cmd.PlatformRegisterTx;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Response;

/**
 * <p>SIP REGISTER 响应按方法名分发到本处理器：</p>
 * <ul>
 *   <li><b>401</b> — 收到 WWW-Authenticate 挑战，带 Digest 应答重发 REGISTER；</li>
 *   <li><b>200</b> — 注册成功，回填在线状态、启动/确认心跳任务；</li>
 *   <li><b>其他</b> — 注册失败，标记离线等待下次重试。</li>
 * </ul>
 */
@Slf4j
@Component
public class RegisterResponseProcessor implements SipResponseProcessor {

    @Autowired
    @Lazy
    private PlatformCommander platformCommander;

    @Autowired
    @Lazy
    private PlatformRegisterService platformRegisterService;

    @Autowired
    private PlatformRegisterState registerState;

    @Autowired
    private GbParentPlatformService parentPlatformService;

    @Override
    public String getMethod() {
        return "REGISTER";
    }

    @Override
    public void process(ResponseEvent event) {
        SIPResponse response = (SIPResponse) event.getResponse();
        String callId = response.getCallIdHeader().getCallId();
        int statusCode = response.getStatusCode();

        // 通过 callId 回查注册时关联的上级平台
        PlatformRegisterTx tx = registerState.txByCallId(callId);
        if (tx == null) {
            log.warn("[级联注册] callId={} 无对应注册事务，忽略响应 status={}", callId, statusCode);
            return;
        }
        String serverGbId = tx.serverGbId();

        GbParentPlatform platform = parentPlatformService.lambdaQuery()
                .eq(GbParentPlatform::getServerGbId, serverGbId)
                .one();
        if (platform == null) {
            log.warn("[级联注册] serverGbId={} 已不存在，忽略响应", serverGbId);
            registerState.clearRegistering(callId);
            return;
        }

        if (statusCode == Response.UNAUTHORIZED) {
            // 401 → 带 Digest 认证重发（注册/注销都可能被挑战）
            WWWAuthenticateHeader www = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
            if (www == null) {
                log.warn("[级联注册] 401 无 WWW-Authenticate 头，放弃 -> {}", serverGbId);
                return;
            }
            // 复用响应回显的 Call-Id / from-tag
            FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
            String fromTag = fromHeader != null ? fromHeader.getTag() : null;
            int expires = tx.register() ? (platform.getExpires() != null ? platform.getExpires() : 3600) : 0;
            platformCommander.registerWithAuth(platform, expires, www,
                    response.getCallIdHeader(), fromTag);
            // 注意：401 后保留 callId→tx 映射，等 200 到来时清

        } else if (statusCode == Response.OK) {
            // 200 → 注册/注销成功
            if (tx.register()) {
                platformRegisterService.online(platform);
                log.info("[级联注册] 注册成功 -> {}", serverGbId);
            } else {
                platformRegisterService.offline(platform);
                log.info("[级联注册] 注销成功 -> {}", serverGbId);
            }
            registerState.clearRegistering(callId);

        } else {
            // 403 等 → 失败
            if (tx.register()) {
                platformRegisterService.offline(platform);
            }
            registerState.clearRegistering(callId);
            log.warn("[级联注册] {} 失败 status={} -> {}",
                    tx.register() ? "注册" : "注销", statusCode, serverGbId);
        }
    }
}