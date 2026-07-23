-- =====================================================
-- Solo · ONVIF 模块数据库初始化
-- 依赖：先执行 sql/solo_db.sql 创建 solo_db 库
-- 范围：所有 onvif_ 前缀的表
-- 关联规则：表之间只使用逻辑关联，不建物理外键
-- =====================================================
USE solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- ONVIF 设备表
-- 与 gb_device 分开：ONVIF 身份靠 host:port + 账号密码，与国标 20 位 device_id + 平台口令 Digest 完全不同
-- =====================================================
DROP TABLE IF EXISTS onvif_device;
CREATE TABLE onvif_device
(
    id               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name             varchar(100)          DEFAULT NULL COMMENT '显示名（用户填）',
    host             varchar(64)  NOT NULL COMMENT '设备 IP',
    port             int          NOT NULL DEFAULT 80 COMMENT 'ONVIF Web 服务端口',
    username         varchar(64)           DEFAULT NULL COMMENT '鉴权用户名',
    password         varchar(128)          DEFAULT NULL COMMENT '鉴权密码（明文存储，与现有代码风格一致）',
    transport        varchar(10)           DEFAULT 'TCP' COMMENT 'RTSP 拉流传输协议：TCP / UDP，默认 TCP',
    stream_type      tinyint               DEFAULT 0    COMMENT '码流类型：0 主码流 1 子码流',
    manufacturer     varchar(100)          DEFAULT NULL COMMENT '厂商（GetDeviceInformation 探测填充）',
    model            varchar(100)          DEFAULT NULL COMMENT '型号（同上）',
    firmware         varchar(50)           DEFAULT NULL COMMENT '固件版本（同上）',
    serial_number    varchar(100)          DEFAULT NULL COMMENT '设备序列号（同上）',
    profile_token    varchar(100)          DEFAULT NULL COMMENT '当前选定的 profile token（GetProfiles 探测填充）',
    stream_uri       varchar(500)          DEFAULT NULL COMMENT 'GetStreamUri 探测填充的原始 RTSP（不含账密）',
    status           tinyint               DEFAULT 0    COMMENT '在线状态：0 离线 1 在线（由探测任务改）',
    last_check_time  datetime              DEFAULT NULL COMMENT '最近一次探测时间',
    source           tinyint      NOT NULL DEFAULT 1 COMMENT '数据来源：1 系统新增 2 表格导入',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_host_port (host, port),
    KEY        idx_status (status),
    KEY        idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ONVIF 设备表';

-- =====================================================
-- 注：点播运行时会话（stream 记账）走 Redis，Key 布局见
-- com.solo.onvif.service.impl.OnvifPlayServiceImpl —— 与国标 GbInviteSession 迁 Redis 的策略一致。
-- =====================================================

SET
FOREIGN_KEY_CHECKS = 1;
