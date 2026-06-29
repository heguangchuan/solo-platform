package com.solo.gb28181.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GB/T 28181 国标平台配置
 * 对应 application.yaml 中 gb28181 段
 */
@Data
@Component
@ConfigurationProperties(prefix = "gb28181")
public class Gb28181Properties {

    /**
     * 是否启用国标服务
     */
    private boolean enabled = false;

    /**
     * SIP 监听 IP；留空表示监听所有网卡
     */
    private String ip;

    /**
     * SIP 监听端口
     */
    private int port = 5060;

    /**
     * 本平台 SIP ID（20 位）
     */
    private String id;

    /**
     * 本平台 SIP 域
     */
    private String domain;

    /**
     * 设备注册鉴权密码
     */
    private String password;

    /**
     * 设备注册有效期（秒）
     */
    private int registerExpires = 3600;

    /**
     * 心跳超时倍数（multiplier × Keepalive 周期未收到心跳即判定离线）
     */
    private int keepaliveTimeoutMultiplier = 3;
}
