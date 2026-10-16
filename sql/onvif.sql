USE solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS onvif_device;
CREATE TABLE onvif_device
(
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name            varchar(100)          DEFAULT NULL COMMENT '显示名称',
    host            varchar(64)  NOT NULL COMMENT '设备IP',
    port            int          NOT NULL DEFAULT 80 COMMENT 'ONVIF端口',
    username        varchar(64)           DEFAULT NULL COMMENT '鉴权用户名',
    password        varchar(128)          DEFAULT NULL COMMENT '鉴权密码',
    status          tinyint               DEFAULT 0 COMMENT '在线状态 0离线 1在线',
    stream_uri      varchar(500)          DEFAULT NULL COMMENT 'RTSP地址',
    last_check_time datetime              DEFAULT NULL COMMENT '最近探测时间',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_host_port (host, port),
    KEY        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET
FOREIGN_KEY_CHECKS = 1;
