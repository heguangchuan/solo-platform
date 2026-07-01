package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Web 终端配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "terminal")
public class TerminalProperties {

    /** SSH 连接超时时间，单位毫秒 */
    private int connectTimeoutMs = 10000;

    /** 终端空闲超时时间，单位毫秒 */
    private long idleTimeoutMs = 600000L;

    /** 单次输入帧最大字节数 */
    private int inputFrameMaxBytes = 2048;

    /** 默认终端列数 */
    private int defaultCols = 80;

    /** 默认终端行数 */
    private int defaultRows = 24;

    /** SSH 输出读取缓冲大小 */
    private int outputBufferSize = 8192;
}
