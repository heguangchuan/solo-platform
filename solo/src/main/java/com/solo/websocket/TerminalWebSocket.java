package com.solo.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.system.constant.SystemConstants;
import com.solo.config.TerminalProperties;
import com.solo.system.entity.SysServer;
import com.solo.system.entity.SysUser;
import com.solo.system.service.SshTerminalService;
import com.solo.system.service.SysServerService;
import com.solo.system.service.SysUserService;
import com.solo.common.utils.JwtUtils;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务器 Web 终端 WebSocket 端点
 * <p>连接地址：ws://localhost:8080/api/ws/terminal/{serverId}?token=xxx
 */
@Slf4j
@Component
@ServerEndpoint("/api/ws/terminal/{serverId}")
public class TerminalWebSocket {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);
    private static final Map<String, Set<Session>> SESSION_MAP = new ConcurrentHashMap<>();

    private static JwtUtils jwtUtils;
    private static SysUserService sysUserService;
    private static SysServerService sysServerService;
    private static SshTerminalService sshTerminalService;
    private static TerminalProperties terminalProperties;

    private String username;
    private Long serverId;
    private SshTerminalService.TerminalSession terminalSession;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        TerminalWebSocket.jwtUtils = jwtUtils;
    }

    @Autowired
    public void setSysUserService(SysUserService sysUserService) {
        TerminalWebSocket.sysUserService = sysUserService;
    }

    @Autowired
    public void setSysServerService(SysServerService sysServerService) {
        TerminalWebSocket.sysServerService = sysServerService;
    }

    @Autowired
    public void setSshTerminalService(SshTerminalService sshTerminalService) {
        TerminalWebSocket.sshTerminalService = sshTerminalService;
    }

    @Autowired
    public void setTerminalProperties(TerminalProperties terminalProperties) {
        TerminalWebSocket.terminalProperties = terminalProperties;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("serverId") String serverIdText) {
        log.info("终端 WebSocket 连接请求：sessionId={}, serverId={}, query={}", session.getId(), serverIdText, session.getQueryString());
        String token = extractToken(session.getQueryString());
        if (token == null || token.isEmpty()) {
            closeSession(session, CloseReason.CloseCodes.CANNOT_ACCEPT, "Token为空");
            return;
        }
        if (!validateToken(token)) {
            closeSession(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Token无效或已过期");
            return;
        }

        try {
            this.username = jwtUtils.getUsernameFromToken(token);
            this.serverId = Long.valueOf(serverIdText);
            SysUser user = sysUserService.getByUsername(username);
            if (user == null || user.getStatus() == SystemConstants.STATUS_DISABLED) {
                closeSession(session, CloseReason.CloseCodes.VIOLATED_POLICY, "用户不存在或已禁用");
                return;
            }
            SysServer server = sysServerService.getById(this.serverId);
            if (server == null) {
                closeSession(session, CloseReason.CloseCodes.CANNOT_ACCEPT, "服务器不存在");
                return;
            }
            if (server.getStatus() == null || server.getStatus() == SystemConstants.STATUS_DISABLED) {
                closeSession(session, CloseReason.CloseCodes.CANNOT_ACCEPT, "服务器已停用");
                return;
            }
            this.terminalSession = sshTerminalService.openShell(
                    server,
                    output -> sendMessage(session, "stdout", output),
                    terminalProperties.getDefaultCols(),
                    terminalProperties.getDefaultRows()
            );
            SESSION_MAP.computeIfAbsent(username, key -> ConcurrentHashMap.newKeySet()).add(session);
            ONLINE_COUNT.incrementAndGet();
            sendSystem(session, "已连接服务器：" + server.getServerName() + "\r\n");
            log.info("终端 WebSocket 连接成功：用户={}, serverId={}, sessionId={}", username, serverId, session.getId());
        } catch (Exception e) {
            log.warn("终端 WebSocket 连接失败：serverId={}, error={}", serverIdText, e.getMessage());
            sendSystem(session, e.getMessage());
            closeSession(session, CloseReason.CloseCodes.CANNOT_ACCEPT, e.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if ("ping".equals(message)) {
            sendText(session, "pong");
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(message);
            String type = node.path("type").asText();
            if ("close".equals(type)) {
                closeSession(session, CloseReason.CloseCodes.NORMAL_CLOSURE, "客户端断开");
                return;
            }
            if (terminalSession == null) {
                sendSystem(session, "终端连接未建立或已断开\r\n");
                return;
            }
            if ("input".equals(type)) {
                String data = node.path("data").asText();
                if (data.getBytes().length > terminalProperties.getInputFrameMaxBytes()) {
                    sendSystem(session, "输入内容过长，已拒绝\r\n");
                    return;
                }
                terminalSession.write(data);
                return;
            }
            if ("resize".equals(type)) {
                int cols = node.path("cols").asInt(terminalProperties.getDefaultCols());
                int rows = node.path("rows").asInt(terminalProperties.getDefaultRows());
                terminalSession.resize(cols, rows);
                return;
            }
            sendSystem(session, "不支持的终端消息类型\r\n");
        } catch (Exception e) {
            log.warn("终端消息处理失败：用户={}, serverId={}, error={}", username, serverId, e.getMessage());
            sendMessage(session, "stderr", e.getMessage() + "\r\n");
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        closeTerminal();
        if (username != null) {
            Set<Session> sessions = SESSION_MAP.get(username);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    SESSION_MAP.remove(username, sessions);
                }
            }
            ONLINE_COUNT.updateAndGet(count -> Math.max(0, count - 1));
            log.info("终端 WebSocket 连接关闭：用户={}, serverId={}, sessionId={}, 原因={}",
                    username, serverId, session.getId(), reason.getReasonPhrase());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("终端 WebSocket 异常：用户={}, serverId={}, 错误={}", username, serverId, error.getMessage(), error);
        closeTerminal();
    }

    private void closeTerminal() {
        if (terminalSession != null) {
            terminalSession.close();
            terminalSession = null;
        }
    }

    private void sendSystem(Session session, String data) {
        sendMessage(session, "system", data);
    }

    private void sendMessage(Session session, String type, String data) {
        try {
            sendText(session, MAPPER.writeValueAsString(Map.of("type", type, "data", data == null ? "" : data)));
        } catch (Exception e) {
            log.warn("终端消息序列化失败：{}", e.getMessage());
        }
    }

    private void sendText(Session session, String message) {
        if (session == null || !session.isOpen()) return;
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.warn("发送终端消息失败：用户={}, serverId={}, error={}", username, serverId, e.getMessage());
            }
        }
    }

    private void closeSession(Session session, CloseReason.CloseCode closeCode, String reason) {
        try {
            session.close(new CloseReason(closeCode, reason));
        } catch (IOException e) {
            log.warn("关闭终端 WebSocket 连接失败", e);
        }
    }

    private String extractToken(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
            if (param.startsWith("Authorization=Bearer ")) {
                return param.substring(22);
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            String username = jwtUtils.getUsernameFromToken(token);
            String cachedToken = jwtUtils.getToken(username);
            return username != null
                    && !username.isEmpty()
                    && !jwtUtils.isTokenExpired(token)
                    && (cachedToken == null || cachedToken.equals(token));
        } catch (ExpiredJwtException e) {
            log.info("终端 WebSocket token 已过期：{}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("终端 WebSocket token 验证失败：{}", e.getMessage());
            return false;
        }
    }
}
