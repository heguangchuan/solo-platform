package com.solo.config;

import com.solo.websocket.SystemMessageWebSocket;
import com.solo.websocket.TerminalWebSocket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
@EnableWebSocket
public class WebSocketConfig {

    /**
     * 外置 Tomcat 不需要注册这个 bean（由容器自动管理 @ServerEndpoint）
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 显式注册端点 bean，确保 @ServerEndpoint 多例端点的依赖注入正确
     */
    @Bean
    public SystemMessageWebSocket systemMessageWebSocket() {
        return new SystemMessageWebSocket();
    }

    @Bean
    public TerminalWebSocket terminalWebSocket() {
        return new TerminalWebSocket();
    }
}
