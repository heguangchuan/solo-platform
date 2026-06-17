package com.solo.websocket;

import com.solo.utils.JwtUtils;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 系统消息 WebSocket 端点
 * <p>连接地址：ws://localhost:8080/api/ws/message?token=xxx
 * <p>消息格式：JSON，包含类型、标题、内容、时间等
 */
@Slf4j
@Component
@ServerEndpoint("/ws/message")
public class SystemMessageWebSocket {

    /**
     * 全局在线连接数
     */
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    /**
     * 会话存储：username -> Session
     * 注意：@ServerEndpoint 是多例的，必须用 static 集合共享会话
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 当前连接的用户名
     */
    private String username;

    private static JwtUtils jwtUtils;

    @Autowired
    public void setJwtUtils(JwtUtils jwtUtils) {
        SystemMessageWebSocket.jwtUtils = jwtUtils;
    }

    /**
     * 连接建立成功
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        // 从 query 参数解析 token 并验证
        String query = session.getQueryString();
        String token = extractToken(query);

        if (!validateToken(token)) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Token 无效"));
            } catch (IOException e) {
                log.warn("关闭无效连接失败", e);
            }
            return;
        }

        this.username = jwtUtils.getUsernameFromToken(token);

        // 如果用户已连接，先关闭旧连接（避免多端重复连接）
        if (SESSION_MAP.containsKey(username)) {
            Session oldSession = SESSION_MAP.get(username);
            try {
                oldSession.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "新连接建立，旧连接断开"));
            } catch (IOException e) {
                log.warn("关闭旧连接失败", e);
            }
        }

        SESSION_MAP.put(username, session);
        ONLINE_COUNT.incrementAndGet();
        log.info("WebSocket 连接建立：用户={}, 当前在线={}", username, ONLINE_COUNT.get());
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        if (username != null) {
            SESSION_MAP.remove(username);
            ONLINE_COUNT.decrementAndGet();
            log.info("WebSocket 连接关闭：用户={}, 原因={}, 当前在线={}",
                    username, reason.getReasonPhrase(), ONLINE_COUNT.get());
        }
    }

    /**
     * 收到客户端消息（通常系统消息是服务端主动推送，客户端消息用于心跳保活）
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        // 心跳响应：收到 ping 回复 pong
        if ("ping".equals(message)) {
            try {
                session.getBasicRemote().sendText("pong");
            } catch (IOException e) {
                log.warn("发送心跳响应失败", e);
            }
        }
    }

    /**
     * 异常处理
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 异常：用户={}, 错误={}", username, error.getMessage(), error);
    }

    /**
     * 发送消息给指定用户
     *
     * @param targetUsername 目标用户名
     * @param message 消息内容（JSON 字符串）
     * @return true=发送成功，false=用户不在线
     */
    public static boolean sendToUser(String targetUsername, String message) {
        Session session = SESSION_MAP.get(targetUsername);
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            session.getBasicRemote().sendText(message);
            return true;
        } catch (IOException e) {
            log.warn("发送 WebSocket 消息失败：用户={}, 错误={}", targetUsername, e.getMessage());
            return false;
        }
    }

    /**
     * 广播消息给所有在线用户
     *
     * @param message 消息内容（JSON 字符串）
     * @return 成功发送的用户数
     */
    public static int broadcast(String message) {
        int successCount = 0;
        for (Map.Entry<String, Session> entry : SESSION_MAP.entrySet()) {
            Session session = entry.getValue();
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                    successCount++;
                } catch (IOException e) {
                    log.warn("广播消息失败：用户={}, 错误={}", entry.getKey(), e.getMessage());
                }
            }
        }
        return successCount;
    }

    /**
     * 获取当前在线用户数
     */
    public static int getOnlineCount() {
        return ONLINE_COUNT.get();
    }

    /**
     * 检查指定用户是否在线
     */
    public static boolean isOnline(String username) {
        Session session = SESSION_MAP.get(username);
        return session != null && session.isOpen();
    }

    /**
     * 从 query 字符串提取 token（兼容 token=xxx 和 Authorization=Bearer xxx 两种格式）
     */
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

    /**
     * 验证 token 有效性
     */
    private boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            String username = jwtUtils.getUsernameFromToken(token);
            return username != null && !username.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
