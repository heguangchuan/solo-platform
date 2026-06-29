-- =====================================================
-- Solo · GB/T 28181 国标模块数据库初始化
-- 依赖：先执行 sql/solo_db.sql 创建 solo_db 库
-- 范围：所有 gb_ 前缀的表
-- =====================================================
USE solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 国标设备表
-- =====================================================
DROP TABLE IF EXISTS gb_device;
CREATE TABLE gb_device
(
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_id       varchar(50)  NOT NULL COMMENT '设备国标ID（20位）',
    name            varchar(100)          DEFAULT NULL COMMENT '设备名称',
    manufacturer    varchar(100)          DEFAULT NULL COMMENT '厂商',
    model           varchar(100)          DEFAULT NULL COMMENT '型号',
    firmware        varchar(50)           DEFAULT NULL COMMENT '固件版本',
    sip_domain      varchar(50)           DEFAULT NULL COMMENT 'SIP域',
    ip              varchar(64)           DEFAULT NULL COMMENT '最后注册来源IP',
    port            int                   DEFAULT NULL COMMENT '最后注册来源端口',
    local_ip        varchar(64)           DEFAULT NULL COMMENT '本平台和该设备通信的本机IP',
    host_address    varchar(80)           DEFAULT NULL COMMENT '设备主机地址 ip:port',
    transport       varchar(10)           DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    expires         int                   DEFAULT 3600 COMMENT '注册有效期（秒）',
    charset         varchar(20)           DEFAULT 'GB2312' COMMENT '字符集',
    status          tinyint               DEFAULT 0 COMMENT '在线状态：0离线 1在线',
    channel_count   int                   DEFAULT 0 COMMENT '通道总数',
    register_time   datetime              DEFAULT NULL COMMENT '最后注册时间',
    keepalive_time  datetime              DEFAULT NULL COMMENT '最后心跳时间',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    KEY        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备表';

SET
FOREIGN_KEY_CHECKS = 1;
