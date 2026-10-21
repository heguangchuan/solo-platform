package com.solo.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.system.entity.SysMessage;
import com.solo.system.mapper.SysMessageMapper;
import com.solo.websocket.SystemMessageWebSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageService extends ServiceImpl<SysMessageMapper, SysMessage> {


    private final ObjectMapper objectMapper;

    public void sendMessage(String username, String title, String content,
                            String type, String level, String route) {
        // 1. 持久化到数据库
        SysMessage message = new SysMessage();
        message.setUsername(username);
        message.setTitle(title);
        message.setContent(content);
        message.setType(type);
        message.setLevel(level);
        message.setRoute(route);
        message.setReadFlag(0);
        message.setCreateTime(LocalDateTime.now());
        save(message);

        // 2. 构建 WebSocket 消息
        String jsonMessage = buildJsonMessage(message);

        // 3. 尝试在线推送
        boolean pushed = SystemMessageWebSocket.sendToUser(username, jsonMessage);
        log.info("发送系统消息：用户={}, 标题={}, 在线推送={}", username, title, pushed);

    }

    /** 广播消息给所有在线用户，不落库；返回成功推送的用户数 */
    public int broadcast(String title, String content, String level) {
        // 广播消息不持久化（或根据需求持久化给所有用户）
        Map<String, Object> msg = new HashMap<>();
        msg.put("id", System.currentTimeMillis());
        msg.put("title", title);
        msg.put("content", content);
        msg.put("type", "BROADCAST");
        msg.put("level", level);
        msg.put("createTime", LocalDateTime.now());
        msg.put("readFlag", 0);

        try {
            String jsonMessage = objectMapper.writeValueAsString(msg);
            int count = SystemMessageWebSocket.broadcast(jsonMessage);
            log.info("广播系统消息：标题={}, 推送人数={}", title, count);
            return count;
        } catch (JacksonException e) {
            log.error("广播消息序列化失败", e);
            return 0;
        }
    }

    public long getUnreadCount(String username) {
        return lambdaQuery()
                .eq(SysMessage::getUsername, username)
                .eq(SysMessage::getReadFlag, 0)
                .count();
    }

    public List<SysMessage> getUserMessages(String username, int limit) {
        return lambdaQuery()
                .eq(SysMessage::getUsername, username)
                .orderByDesc(SysMessage::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    public void markRead(Long id, String username) {
        baseMapper.markRead(id, username);
    }

    public void markAllRead(String username) {
        baseMapper.markAllRead(username);
    }

    /** 构建 WebSocket JSON 消息；序列化失败时抛出而非静默降级，避免前端漏更新红点 */
    private String buildJsonMessage(SysMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new IllegalStateException("系统消息序列化失败：id=" + message.getId(), e);
        }
    }
}
