package com.solo.config;

import com.solo.websocket.SystemMessageWebSocket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 * 用于注册 @ServerEndpoint 注解的端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig {

    /**
     * 自动注册使用 @ServerEndpoint 注解的 WebSocket 端点
     * 注意：如果使用外置 Tomcat，这个 bean 不需要注册（由 Tomcat 自动管理）
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 显式注册 WebSocket 端点（确保单例依赖注入正确）
     */
    @Bean
    public SystemMessageWebSocket systemMessageWebSocket() {
        return new SystemMessageWebSocket();
    }
}
