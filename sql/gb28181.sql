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
    report_name             varchar(100)          DEFAULT NULL COMMENT '设备上报原始名称（DeviceInfo 响应）',
    name                    varchar(100)          DEFAULT NULL COMMENT '用户自定义显示名称（展示优先级高于 report_name）',
    manufacturer            varchar(100)          DEFAULT NULL COMMENT '厂商',
    model                   varchar(100)          DEFAULT NULL COMMENT '型号',
    firmware                varchar(50)           DEFAULT NULL COMMENT '固件版本',
    ip                      varchar(64)           DEFAULT NULL COMMENT '最后注册来源IP',
    port                    int                   DEFAULT NULL COMMENT '最后注册来源端口',
    local_ip                varchar(64)           DEFAULT NULL COMMENT '本平台和该设备通信的本机IP',
    host_address            varchar(80)           DEFAULT NULL COMMENT '设备主机地址 ip:port',
    transport               varchar(10)           DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    expires                 int                   DEFAULT 3600 COMMENT '注册有效期（秒）',
    charset                 varchar(20)           DEFAULT 'GB2312' COMMENT '字符集',
    status                  tinyint               DEFAULT 0 COMMENT '在线状态：0离线 1在线 2未注册（仅由SIP交互改变）',
    source                  tinyint      NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册',
    channel_count           int                   DEFAULT 0 COMMENT '通道总数（Catalog 同步后回填）',
    register_time           datetime              DEFAULT NULL COMMENT '最后注册时间',
    keepalive_time          datetime              DEFAULT NULL COMMENT '最后心跳时间',
    last_catalog_sync_time  datetime              DEFAULT NULL COMMENT '最后 Catalog 同步完成时间',
    platform_id             varchar(20)  NOT NULL DEFAULT '0000' COMMENT '所属平台ID：0000=自有平台；其他=上级级联平台ID（用于区分设备来源）',
    create_time             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    KEY        idx_status (status),
    KEY        idx_platform_id (platform_id),
    KEY        idx_source (source)
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
    report_name    varchar(255)          DEFAULT NULL COMMENT '通道上报原始名称（Catalog 目录同步）',
    name            varchar(255)          DEFAULT NULL COMMENT '用户自定义显示名称（展示优先级高于 report_name）',
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
    status          varchar(20)           DEFAULT 'OFF' COMMENT '在线状态 ON/OFF/UNREGISTERED（仅由SIP交互改变）',
    longitude       double                DEFAULT NULL COMMENT '经度',
    latitude        double                DEFAULT NULL COMMENT '纬度',
    ptz_type        tinyint               DEFAULT 0    COMMENT '云台类型 0未知 1球机 2半球 3固定枪机 4遥控枪机',
    stream_id       varchar(100)          DEFAULT NULL COMMENT '运行时点播 stream id',
    platform_id     varchar(20)  NOT NULL DEFAULT '0000' COMMENT '所属平台ID：0000=自有平台；其他=上级级联平台ID（与所属设备保持一致）',
    source          tinyint      NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_channel (device_db_id, channel_id),
    KEY        idx_device_id (device_id),
    KEY        idx_channel_id (channel_id),
    KEY        idx_parent_id (parent_id),
    KEY        idx_platform_id (platform_id),
    KEY        idx_status (status),
    KEY        idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备通道表';

-- =====================================================
-- 流媒体节点表（ZLMediaKit 实例登记）
-- 关联规则：通过 server_id（字符串）与 ZLM config.ini 中 [general].mediaServerId 对齐
-- =====================================================
DROP TABLE IF EXISTS media_server;
CREATE TABLE media_server
(
    id              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    server_id       varchar(64)  NOT NULL COMMENT 'ZLM 节点唯一标识（与 [general].mediaServerId 一致）',
    name            varchar(100)          DEFAULT NULL COMMENT '节点显示名',
    host_ip         varchar(64)  NOT NULL COMMENT 'ZLM 内网/管理面 IP',
    http_port       int          NOT NULL DEFAULT 80 COMMENT 'ZLM HTTP API 端口',
    secret          varchar(100)          DEFAULT NULL COMMENT 'ZLM API secret',
    stream_ip       varchar(64)           DEFAULT NULL COMMENT '对外播放 IP（HTTP-FLV/HLS/WebRTC），留空则同 host_ip',
    stream_http_port int                  DEFAULT 0 COMMENT '对外HTTP/WS-FLV/HLS端口（0=同 http_port）',
    rtsp_port       int                   DEFAULT 0 COMMENT '对外RTSP端口（0=禁用 RTSP URL）',
    rtc_port        int                   DEFAULT 0 COMMENT '对外WebRTC端口（0=禁用 RTC URL）',
    rtp_port_start  int                   DEFAULT 30000 COMMENT 'RTP 端口段起始',
    rtp_port_end    int                   DEFAULT 35000 COMMENT 'RTP 端口段截止',
    status          tinyint               DEFAULT 0 COMMENT '在线状态：0离线 1在线（由 on_server_keepalive 维护）',
    hook_alive_time datetime              DEFAULT NULL COMMENT '最近一次 on_server_keepalive 时间',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_id (server_id),
    KEY        idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流媒体节点表';

-- =====================================================
-- 注：点播 / 回放 / 下载会话（gb_invite_session）已迁移至 Redis（运行时状态表，
-- 高频读写、按 callId/ssrc/streamId 反查、会话结束物理删除）。
-- Key 布局见 com.solo.gb28181.service.impl.GbInviteSessionServiceImpl。
-- 真要做"播放历史 / 审计"请另建 gb_play_history 表，schema 不同。
-- =====================================================

-- =====================================================
-- 存量库升级：
-- 1) 为已存在的 gb_device / gb_device_channel 补 source 列
-- 2) 设备/通道改为物理删除，移除 deleted 列（清理前请先删掉历史 deleted=1 的垃圾行）
-- （全新执行本脚本时上面 CREATE 已是最新结构，以下 ALTER 会因列已存在/不存在而报错，可忽略）
-- =====================================================
-- ALTER TABLE gb_device        ADD COLUMN source tinyint NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册' AFTER status;
-- ALTER TABLE gb_device        ADD KEY idx_source (source);
-- ALTER TABLE gb_device_channel ADD COLUMN source tinyint NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册' AFTER platform_id;
-- ALTER TABLE gb_device_channel ADD KEY idx_source (source);
-- DELETE FROM gb_device         WHERE deleted = 1;
-- DELETE FROM gb_device_channel WHERE deleted = 1;
-- ALTER TABLE gb_device         DROP COLUMN deleted;
-- ALTER TABLE gb_device_channel DROP COLUMN deleted;

SET
FOREIGN_KEY_CHECKS = 1;
