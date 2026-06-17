package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.entity.SysMessage;
import com.solo.mapper.SysMessageMapper;
import com.solo.websocket.SystemMessageWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统消息服务
 */
@Slf4j
@Service
public class SysMessageService extends ServiceImpl<SysMessageMapper, SysMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 发送消息给指定用户
     *
     * @param username 目标用户名
     * @param title 消息标题
     * @param content 消息内容
     * @param type 消息类型
     * @param level 消息级别
     * @param route 跳转路由（可选）
     * @return true=在线并推送成功，false=离线仅持久化
     */
    public boolean sendMessage(String username, String title, String content,
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

        return pushed;
    }

    /**
     * 发送简化版消息（默认 INFO 级别）
     */
    public boolean sendMessage(String username, String title, String content) {
        return sendMessage(username, title, content, "SYSTEM", "INFO", null);
    }

    /**
     * 广播消息给所有在线用户
     *
     * @param title 消息标题
     * @param content 消息内容
     * @param level 消息级别
     * @return 成功推送的用户数
     */
    public int broadcast(String title, String content, String level) {
        // 广播消息不持久化（或根据需求持久化给所有用户）
        Map<String, Object> msg = new HashMap<>();
        msg.put("id", System.currentTimeMillis());
        msg.put("title", title);
        msg.put("content", content);
        msg.put("type", "BROADCAST");
        msg.put("level", level);
        msg.put("createTime", LocalDateTime.now().toString());
        msg.put("readFlag", 0);

        try {
            String jsonMessage = MAPPER.writeValueAsString(msg);
            int count = SystemMessageWebSocket.broadcast(jsonMessage);
            log.info("广播系统消息：标题={}, 推送人数={}", title, count);
            return count;
        } catch (JsonProcessingException e) {
            log.error("广播消息序列化失败", e);
            return 0;
        }
    }

    /**
     * 获取当前用户未读消息数
     */
    public long getUnreadCount(String username) {
        return lambdaQuery()
                .eq(SysMessage::getUsername, username)
                .eq(SysMessage::getReadFlag, 0)
                .count();
    }

    /**
     * 获取当前用户消息列表
     */
    public List<SysMessage> getUserMessages(String username, int limit) {
        return lambdaQuery()
                .eq(SysMessage::getUsername, username)
                .orderByDesc(SysMessage::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 标记消息为已读
     */
    public boolean markRead(Long id, String username) {
        return baseMapper.markRead(id, username) > 0;
    }

    /**
     * 标记所有消息为已读
     */
    public boolean markAllRead(String username) {
        return baseMapper.markAllRead(username) > 0;
    }

    /**
     * 构建 WebSocket JSON 消息
     */
    private String buildJsonMessage(SysMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // 降级：简单 JSON
            return "{\"id\":" + message.getId() + ",\"title\":\"" + message.getTitle() + "\"}";
        }
    }
}
