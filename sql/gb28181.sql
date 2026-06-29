-- =====================================================
-- Solo · GB/T 28181 国标模块数据库初始化
-- 依赖：先执行 sql/solo_db.sql 创建 solo_db 库
-- 范围：所有 gb_ 前缀的表
-- 关联规则：表之间只使用逻辑关联（如 device_db_id 引用 gb_device.id），不建物理外键
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
    id                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_id               varchar(50)  NOT NULL COMMENT '设备国标ID（20位）',
    name                    varchar(100)          DEFAULT NULL COMMENT '设备名称（DeviceInfo 上报）',
    manufacturer            varchar(100)          DEFAULT NULL COMMENT '厂商',
    model                   varchar(100)          DEFAULT NULL COMMENT '型号',
    firmware                varchar(50)           DEFAULT NULL COMMENT '固件版本',
    sip_domain              varchar(50)           DEFAULT NULL COMMENT 'SIP域',
    ip                      varchar(64)           DEFAULT NULL COMMENT '最后注册来源IP',
    port                    int                   DEFAULT NULL COMMENT '最后注册来源端口',
    local_ip                varchar(64)           DEFAULT NULL COMMENT '本平台和该设备通信的本机IP',
    host_address            varchar(80)           DEFAULT NULL COMMENT '设备主机地址 ip:port',
    transport               varchar(10)           DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    expires                 int                   DEFAULT 3600 COMMENT '注册有效期（秒）',
    charset                 varchar(20)           DEFAULT 'GB2312' COMMENT '字符集',
    status                  tinyint               DEFAULT 0 COMMENT '在线状态：0离线 1在线',
    channel_count           int                   DEFAULT 0 COMMENT '通道总数（Catalog 同步后回填）',
    register_time           datetime              DEFAULT NULL COMMENT '最后注册时间',
    keepalive_time          datetime              DEFAULT NULL COMMENT '最后心跳时间',
    last_catalog_sync_time  datetime              DEFAULT NULL COMMENT '最后 Catalog 同步完成时间',
    create_time             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    KEY        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备表';

-- =====================================================
-- 国标设备通道表（逻辑关联 gb_device.id，不建物理外键）
-- =====================================================
DROP TABLE IF EXISTS gb_device_channel;
CREATE TABLE gb_device_channel
(
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    device_db_id    bigint       NOT NULL COMMENT '所属设备数据库主键（逻辑关联 gb_device.id）',
    device_id       varchar(50)  NOT NULL COMMENT '所属设备国标ID（冗余存储便于查询）',
    channel_id      varchar(50)  NOT NULL COMMENT '通道国标ID（20位）',
    name            varchar(255)          DEFAULT NULL COMMENT '通道名称',
    manufacturer    varchar(100)          DEFAULT NULL COMMENT '厂商',
    model           varchar(100)          DEFAULT NULL COMMENT '型号',
    owner           varchar(50)           DEFAULT NULL COMMENT '设备归属',
    civil_code      varchar(20)           DEFAULT NULL COMMENT '行政区划编码',
    block           varchar(100)          DEFAULT NULL COMMENT '警区',
    address         varchar(500)          DEFAULT NULL COMMENT '安装地址',
    parental        tinyint               DEFAULT 0    COMMENT '是否有子设备 0否 1是',
    parent_id       varchar(50)           DEFAULT NULL COMMENT '父节点ID（虚拟目录用）',
    safety_way      tinyint               DEFAULT 0    COMMENT '信令安全模式',
    register_way    tinyint               DEFAULT 1    COMMENT '注册方式',
    cert_num        varchar(50)           DEFAULT NULL COMMENT '证书序列号',
    certifiable     tinyint               DEFAULT 0    COMMENT '证书有效标识',
    err_code        int                   DEFAULT NULL COMMENT '证书无效原因码',
    end_time        datetime              DEFAULT NULL COMMENT '证书终止有效期',
    secrecy         tinyint               DEFAULT 0    COMMENT '保密属性',
    ip_address      varchar(64)           DEFAULT NULL COMMENT '通道IP',
    port            int                   DEFAULT NULL COMMENT '通道端口',
    password        varchar(100)          DEFAULT NULL COMMENT '通道密码',
    status          varchar(20)           DEFAULT 'OFF' COMMENT '在线状态 ON/OFF',
    longitude       double                DEFAULT NULL COMMENT '经度',
    latitude        double                DEFAULT NULL COMMENT '纬度',
    ptz_type        tinyint               DEFAULT 0    COMMENT '云台类型 0未知 1球机 2半球 3固定枪机 4遥控枪机',
    stream_id       varchar(100)          DEFAULT NULL COMMENT '运行时点播 stream id',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         tinyint      NOT NULL DEFAULT 0    COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_channel (device_db_id, channel_id),
    KEY        idx_device_id (device_id),
    KEY        idx_channel_id (channel_id),
    KEY        idx_parent_id (parent_id),
    KEY        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备通道表';

SET
FOREIGN_KEY_CHECKS = 1;
