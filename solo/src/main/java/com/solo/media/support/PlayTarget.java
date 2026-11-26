package com.solo.media.support;

import java.util.Locale;

// FLV 的 schema 都是 rtmp，还得靠 protocol 分清 HTTP 和 WS。
public record PlayTarget(String mediaServerId, String app, String streamId, String schema, String protocol) {

    public static final String SCHEMA_RTMP = "rtmp";
    public static final String SCHEMA_HLS = "hls";
    public static final String SCHEMA_RTC = "rtc";
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_WS = "ws";

    public PlayTarget {
        mediaServerId = requireText(mediaServerId, "mediaServerId");
        app = requireText(app, "app");
        streamId = requireText(streamId, "streamId");
        schema = requireText(schema, "schema").toLowerCase(Locale.ROOT);
        protocol = normalizeProtocol(schema, protocol);
    }

    public PlayTarget(String mediaServerId, String app, String streamId, String schema) {
        this(mediaServerId, app, streamId, schema, "");
    }

    public static PlayTarget httpFlv(String mediaServerId, String app, String streamId) {
        return new PlayTarget(mediaServerId, app, streamId, SCHEMA_RTMP, PROTOCOL_HTTP);
    }

    public static PlayTarget wsFlv(String mediaServerId, String app, String streamId) {
        return new PlayTarget(mediaServerId, app, streamId, SCHEMA_RTMP, PROTOCOL_WS);
    }

    public static PlayTarget hls(String mediaServerId, String app, String streamId) {
        return new PlayTarget(mediaServerId, app, streamId, SCHEMA_HLS, "");
    }

    public static PlayTarget rtc(String mediaServerId, String app, String streamId) {
        return new PlayTarget(mediaServerId, app, streamId, SCHEMA_RTC, "");
    }

    private static String normalizeProtocol(String schema, String protocol) {
        if (!SCHEMA_RTMP.equals(schema)) {
            return "";
        }
        String normalized = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        // https/wss 和 http/ws 算同一种播放方式，别换个 TLS 就验签失败
        if ("https".equals(normalized)) {
            normalized = PROTOCOL_HTTP;
        } else if ("wss".equals(normalized)) {
            normalized = PROTOCOL_WS;
        }
        if (!PROTOCOL_HTTP.equals(normalized) && !PROTOCOL_WS.equals(normalized)) {
            throw new IllegalArgumentException("FLV 播放令牌目标缺少有效的 protocol(http/ws)");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("播放令牌目标缺少 " + field);
        }
        return value.trim();
    }
}
