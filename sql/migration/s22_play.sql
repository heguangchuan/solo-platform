-- =====================================================
-- Solo · S2.2 实时点播迁移
-- 为 gb_media_server 追加对外播放端口字段；不影响既有数据
-- =====================================================
USE solo_db;

ALTER TABLE gb_media_server
    ADD COLUMN stream_http_port int DEFAULT 0 COMMENT '对外HTTP/WS-FLV/HLS端口（0=同 http_port）' AFTER stream_ip,
    ADD COLUMN rtsp_port        int DEFAULT 0 COMMENT '对外RTSP端口（0=禁用 RTSP URL）'           AFTER stream_http_port,
    ADD COLUMN rtc_port         int DEFAULT 0 COMMENT '对外WebRTC端口（0=禁用 RTC URL）'          AFTER rtsp_port;
