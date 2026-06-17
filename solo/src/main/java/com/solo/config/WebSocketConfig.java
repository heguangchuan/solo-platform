package com.solo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 * <p>支持两种模式：
 * <ul>
 *   <li>@ServerEndpoint 注解模式（连接管理更灵活，支持用户维度会话）</li>
 *   <li>WebSocketHandler 模式（适合简单消息转发）</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * 扫描 @ServerEndpoint 注解，让 Spring 托管 WebSocket 端点
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 当前使用 @ServerEndpoint 模式，这里留空
        // 如果需要 Handler 模式，在这里注册：
        // registry.addHandler(myWebSocketHandler, "/ws/message").setAllowedOrigins("*");
    }
}
