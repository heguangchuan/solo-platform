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
    id                      bigint       NOT NULL COMMENT '主键ID（雪花）',
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
    id              bigint       NOT NULL COMMENT '主键ID（雪花）',
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
    platform_id     varchar(20)  NOT NULL DEFAULT '0000' COMMENT '所属平台ID：0000=自有平台；其他=上级级联平台ID（与所属设备保持一致）',
    group_id        varchar(20)  NOT NULL DEFAULT '0000' COMMENT '所属资源目录分组（逻辑关联 gb_group.id 业务短码）；默认值 0000 须与 application.yaml gb28181.default-group.id 保持一致',
    source          tinyint      NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_channel (device_db_id, channel_id),
    KEY        idx_device_id (device_id),
    KEY        idx_channel_id (channel_id),
    KEY        idx_platform_id (platform_id),
    KEY        idx_group_id (group_id),
    KEY        idx_status (status),
    KEY        idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备通道表';

-- =====================================================
-- 国标资源目录分组表（业务分组 215 / 虚拟组织 216）
-- 平台侧自建目录树，用于级联时向上级共享通道时组织目录结构；
-- 与下级设备上报的原始 Catalog 目录无关，是本平台人为编排的资源目录。
-- 主键 id 为业务短码（默认分组固定 0000）；group_id 为拼 Catalog 上报上级用的 20 位国标码。
-- 逻辑关联：gb_device_channel.group_id 引用本表 id（业务短码），不建物理外键。
-- =====================================================
DROP TABLE IF EXISTS gb_group;
CREATE TABLE gb_group
(
    id           varchar(20)  NOT NULL COMMENT '数据主键：默认分组固定 0000，其余雪花ID；通道 group_id 引用此列',
    group_id     varchar(20)  NOT NULL COMMENT '分组国标ID（20位；第11-13位 215业务分组 / 216虚拟组织，拼 Catalog 上报上级用）',
    name         varchar(255) NOT NULL COMMENT '分组名称（作为 Catalog <Item><Name> 上报上级）',
    parent_id    varchar(20)  NOT NULL DEFAULT '0' COMMENT '父节点主键：0=根节点',
    platform_id  varchar(20)  NOT NULL DEFAULT '0000' COMMENT '数据来源：0000=自有平台；其他=级联来源平台ID',
    sort         int          NOT NULL DEFAULT 0 COMMENT '排序值（同层从小到大）',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_id (group_id),
    KEY        idx_parent_id (parent_id),
    KEY        idx_platform_id (platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标资源目录分组表';

-- =====================================================
-- 下级级联平台白名单表
-- 本平台作为上级时，下级平台在此登记后可通过 REGISTER 接入。
-- SIP 接入时视为设备（gb_device），平台与设备通过 platform_id 自动关联。
-- 运行态（在线/心跳/通道数）查关联的 gb_device 行，不在本表冗余。
-- =====================================================
DROP TABLE IF EXISTS gb_platform;
CREATE TABLE gb_platform
(
    id           bigint       NOT NULL COMMENT '主键ID（雪花）',
    platform_id  varchar(20)  NOT NULL COMMENT '下级平台国标ID（20位），唯一且等于 gb_device.device_id',
    name         varchar(255) NOT NULL COMMENT '平台名称',
    auth_key     varchar(64)  NOT NULL DEFAULT '' COMMENT '注册鉴权专属密钥（后端自动生成 32 位 UUID）',
    enabled      tinyint      NOT NULL DEFAULT 1 COMMENT '启用状态：0禁用（拒绝注册）1启用',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_id (platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下级级联平台白名单表';

-- =====================================================
-- 流媒体节点表（ZLMediaKit 实例登记）
-- 关联规则：通过 server_id（字符串）与 ZLM config.ini 中 [general].mediaServerId 对齐
-- =====================================================
DROP TABLE IF EXISTS media_server;
CREATE TABLE media_server
(
    id              bigint       NOT NULL COMMENT '主键ID（雪花）',
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
-- 拉流代理表（stream_proxy）
-- 记录管理台建的第三方源拉流代理：RTSP / RTMP / HTTP-FLV / HLS 等。
-- ZLM addStreamProxy 返回的 proxy_key 存 proxy_key，delStreamProxy 用得着。
-- 一条 (app, stream, media_server_id) 三元组代表一路可播放流；unique 保护重名。
-- =====================================================
DROP TABLE IF EXISTS stream_proxy;
CREATE TABLE stream_proxy
(
    id                bigint       NOT NULL COMMENT '主键ID（雪花）',
    name              varchar(100)          DEFAULT NULL COMMENT '显示名（用户填）',
    app               varchar(50)  NOT NULL DEFAULT 'proxy' COMMENT 'ZLM app 名',
    stream            varchar(100) NOT NULL COMMENT 'ZLM stream 名',
    src_url           varchar(1000) NOT NULL COMMENT '源地址：rtsp:// rtmp:// http-flv/hls 等',
    media_server_id   varchar(64)           DEFAULT NULL COMMENT '目标 ZLM 节点 serverId（留空则启动时自动挑一个可用节点）',
    rtp_type          tinyint      NOT NULL DEFAULT 1 COMMENT 'RTSP 拉流方式：0=TCP+UDP 自适应 1=TCP 2=UDP 3=组播',
    enable_audio      tinyint      NOT NULL DEFAULT 1 COMMENT '是否拉音频 0否 1是',
    enable_mp4        tinyint      NOT NULL DEFAULT 0 COMMENT '是否录制 MP4 0否 1是',
    enable_disable_none_reader tinyint NOT NULL DEFAULT 0 COMMENT '无人观看时是否自动停用 0否 1是',
    enabled           tinyint      NOT NULL DEFAULT 1 COMMENT '业务启用开关 0禁用 1启用（禁用时不推给 ZLM）',
    proxy_key         varchar(100)          DEFAULT NULL COMMENT 'ZLM addStreamProxy 返回的 key，用于 delStreamProxy',
    status            tinyint      NOT NULL DEFAULT 0 COMMENT '运行状态 0未启动/离线 1已推给 ZLM',
    last_error        varchar(500)          DEFAULT NULL COMMENT '最近一次启动失败原因（仅用于展示）',
    remark            varchar(255)          DEFAULT NULL COMMENT '备注',
    create_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY        idx_media_server_id (media_server_id),
    KEY        idx_status (status),
    KEY        idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拉流代理表';

-- =====================================================
-- 推流表（stream_push）
-- 记录 ZLM 上第三方推上来的流（RTMP / RTSP / WebRTC push）。
-- 数据由 ZLM Hook 事件驱动：on_stream_changed(regist=true) → upsert，regist=false → 物理删。
-- on_server_started 时兜底：拉 getMediaList，只保留 origin_type∈{rtmp,rtsp,rtc}，与库对齐后删掉库里 ZLM 已无的僵尸行。
-- =====================================================
DROP TABLE IF EXISTS stream_push;
CREATE TABLE stream_push
(
    id                bigint       NOT NULL COMMENT '主键ID（雪花）',
    app               varchar(50)  NOT NULL COMMENT 'ZLM app 名',
    stream            varchar(100) NOT NULL COMMENT 'ZLM stream 名',
    media_server_id   varchar(64)  NOT NULL COMMENT '所属 ZLM 节点 serverId',
    vhost             varchar(100)          DEFAULT '__defaultVhost__' COMMENT 'ZLM vhost',
    stream_schema     varchar(100)          DEFAULT NULL COMMENT '推流协议（rtsp/rtmp/hls/ts/fmp4，通常只留 rtsp）',
    origin_type       varchar(20)           DEFAULT NULL COMMENT '推流类型：rtmp / rtsp / rtc',
    pushing           tinyint      NOT NULL DEFAULT 0 COMMENT '当前是否在推流：0已断 1推流中',
    alive_second      int                   DEFAULT 0 COMMENT '存活秒数（快照，展示用）',
    push_time         datetime              DEFAULT NULL COMMENT '首次开始推流时间',
    client_id         varchar(64)           DEFAULT NULL COMMENT '推流方标识（关联 stream_push_client.client_id，未开启鉴权则为空）',
    create_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY        idx_media_server_id (media_server_id),
    KEY        idx_pushing (pushing),
    KEY        idx_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推流记录表';

-- =====================================================
-- 推流方鉴权表（stream_push_client）
-- 一条 = 一个推流方。推流时在 RTMP/RTSP URL 上带 ?token=xxx，ZLM on_publish 里查此表。
-- token 明文存储（256 位随机熵），泄漏时管理员点"重置 token"重发。
-- =====================================================
DROP TABLE IF EXISTS stream_push_client;
CREATE TABLE stream_push_client
(
    id           bigint       NOT NULL COMMENT '主键ID（雪花）',
    client_id    varchar(64)  NOT NULL COMMENT '推流方标识（用户填，唯一）',
    token        varchar(64)  NOT NULL COMMENT '鉴权令牌（SecureRandom 32 字节 Base64URL）',
    enabled      tinyint      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    remark       varchar(255)          DEFAULT NULL COMMENT '备注',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推流方鉴权表';


-- =====================================================
-- 上级平台配置表（gb_parent_platform）
-- 本平台作为下级向上级平台注册时的配置。一行 = 一个上级平台。
-- 注册成功后 status 回填为 1（在线），注册失败/超时回填 0。
-- =====================================================
DROP TABLE IF EXISTS gb_parent_platform;
CREATE TABLE gb_parent_platform
(
    id               bigint       NOT NULL COMMENT '主键ID（雪花）',
    name             varchar(255) NOT NULL COMMENT '上级平台名称',
    server_gb_id     varchar(20)  NOT NULL COMMENT '上级平台国标编码（SIP 服务器 ID）',
    server_gb_domain varchar(20)  NOT NULL COMMENT '上级平台国标域',
    server_ip        varchar(64)  NOT NULL COMMENT '上级平台 SIP IP',
    server_port      int          NOT NULL DEFAULT 5060 COMMENT '上级平台 SIP 端口',
    device_gb_id     varchar(20)  NOT NULL COMMENT '本平台作为下级的国标编号（向上注册用）',
    device_ip        varchar(64)  NOT NULL DEFAULT '' COMMENT '本平台 SIP IP（留空取 gb28181.ip）',
    device_port      int          NOT NULL DEFAULT 5060 COMMENT '本平台 SIP 端口（留空取 gb28181.port）',
    username         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP 认证用户名（默认同 device_gb_id）',
    password         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP 认证密码',
    expires          int          NOT NULL DEFAULT 3600 COMMENT '注册有效期（秒）',
    keep_timeout     int          NOT NULL DEFAULT 60 COMMENT '心跳周期（秒）',
    transport        varchar(16)  NOT NULL DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    character_set    varchar(16)  NOT NULL DEFAULT 'GB2312' COMMENT '字符集',
    enabled          tinyint      NOT NULL DEFAULT 1 COMMENT '启用状态：0禁用 1启用',
    status           tinyint      NOT NULL DEFAULT 0 COMMENT '在线状态：0离线 1在线（后续注册成功回填）',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_gb_id (server_gb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台配置表';


-- =====================================================
-- 上级平台共享目录表（gb_platform_catalog）
-- 本平台作为下级向上级共享资源目录时，选中的分组（目录）记录。
-- 一行 = 一个上级平台 × 一个共享目录分组，无物理外键（ADR-011）。
-- =====================================================
DROP TABLE IF EXISTS gb_platform_catalog;
CREATE TABLE gb_platform_catalog
(
    id          bigint   NOT NULL COMMENT '主键ID（雪花）',
    platform_id bigint   NOT NULL COMMENT '上级平台主键（gb_parent_platform.id）',
    group_id    varchar(32) NOT NULL COMMENT '分组主键（gb_group.id）',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_group (platform_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台共享目录表';


-- =====================================================
-- 上级平台共享通道表（gb_platform_gb_channel）
-- 本平台作为下级向上级共享通道时，选中的通道记录。
-- 一行 = 一个上级平台 × 一个通道，无物理外键（ADR-011）。
-- =====================================================
DROP TABLE IF EXISTS gb_platform_gb_channel;
CREATE TABLE gb_platform_gb_channel
(
    id          bigint   NOT NULL COMMENT '主键ID（雪花）',
    platform_id bigint   NOT NULL COMMENT '上级平台主键（gb_parent_platform.id）',
    channel_id  bigint   NOT NULL COMMENT '通道主键（gb_device_channel.id）',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_channel (platform_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台共享通道表';


SET
FOREIGN_KEY_CHECKS = 1;
