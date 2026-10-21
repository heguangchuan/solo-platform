package com.solo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "terminal")
public class TerminalProperties {

    /** SSH 连接超时时间，单位毫秒 */
    private int connectTimeoutMs = 10000;

    /** 终端空闲超时时间，单位毫秒 */
    private long idleTimeoutMs = 600000L;

    private int inputFrameMaxBytes = 2048;

    private int defaultCols = 80;

    private int defaultRows = 24;

    private int outputBufferSize = 8192;
}
