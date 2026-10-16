USE
solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS gb_device;
CREATE TABLE gb_device
(
    id                     bigint      NOT NULL COMMENT '主键ID',
    device_id              varchar(50) NOT NULL COMMENT '设备国标ID',
    report_name            varchar(100)         DEFAULT NULL COMMENT '上报原始名称',
    name                   varchar(100)         DEFAULT NULL COMMENT '自定义显示名称',
    manufacturer           varchar(100)         DEFAULT NULL COMMENT '厂商',
    model                  varchar(100)         DEFAULT NULL COMMENT '型号',
    firmware               varchar(50)          DEFAULT NULL COMMENT '固件版本',
    owner                  varchar(50)          DEFAULT NULL COMMENT '设备归属',
    civil_code             varchar(20)          DEFAULT NULL COMMENT '行政区划编码',
    address                varchar(500)         DEFAULT NULL COMMENT '安装地址',
    ip                     varchar(64)          DEFAULT NULL COMMENT '注册来源IP',
    recv_ip                varchar(64)          DEFAULT NULL COMMENT '收流IP覆盖',
    port                   int                  DEFAULT NULL COMMENT '注册来源端口',
    local_ip               varchar(64)          DEFAULT NULL COMMENT '本机通信IP',
    host_address           varchar(80)          DEFAULT NULL COMMENT '主机地址 ip:port',
    transport              varchar(10)          DEFAULT 'UDP' COMMENT '传输协议',
    expires                int                  DEFAULT 3600 COMMENT '注册有效期（秒）',
    charset                varchar(20)          DEFAULT 'GB2312' COMMENT '字符集',
    status                 tinyint              DEFAULT 0 COMMENT '在线状态 0离线 1在线 2未注册',
    source                 tinyint     NOT NULL DEFAULT 3 COMMENT '数据来源 1新增 2导入 3注册',
    register_time          datetime             DEFAULT NULL COMMENT '最后注册时间',
    keepalive_time         datetime             DEFAULT NULL COMMENT '最后心跳时间',
    last_catalog_sync_time datetime             DEFAULT NULL COMMENT '最后Catalog同步时间',
    create_time            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    KEY                    idx_status (status),
    KEY                    idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_device_channel;
CREATE TABLE gb_device_channel
(
    id           bigint      NOT NULL COMMENT '主键ID',
    device_id    varchar(50) NOT NULL COMMENT '所属设备国标ID',
    channel_id   varchar(50) NOT NULL COMMENT '通道/分组国标码',
    report_name  varchar(255)         DEFAULT NULL COMMENT '上报原始名称',
    name         varchar(255)         DEFAULT NULL COMMENT '自定义显示名称',
    manufacturer varchar(100)         DEFAULT NULL COMMENT '厂商',
    model        varchar(100)         DEFAULT NULL COMMENT '型号',
    owner        varchar(50)          DEFAULT NULL COMMENT '设备归属',
    civil_code   varchar(20)          DEFAULT NULL COMMENT '行政区划编码',
    block        varchar(100)         DEFAULT NULL COMMENT '警区',
    address      varchar(500)         DEFAULT NULL COMMENT '安装地址',
    parental     tinyint     NOT NULL DEFAULT 0 COMMENT '0通道 1分组',
    safety_way   tinyint              DEFAULT 0 COMMENT '信令安全模式',
    register_way tinyint              DEFAULT 1 COMMENT '注册方式',
    cert_num     varchar(50)          DEFAULT NULL COMMENT '证书序列号',
    certifiable  tinyint              DEFAULT 0 COMMENT '证书有效标识',
    err_code     int                  DEFAULT NULL COMMENT '证书无效原因码',
    end_time     datetime             DEFAULT NULL COMMENT '证书终止有效期',
    secrecy      tinyint              DEFAULT 0 COMMENT '保密属性',
    ip_address   varchar(64)          DEFAULT NULL COMMENT '通道IP',
    port         int                  DEFAULT NULL COMMENT '通道端口',
    password     varchar(100)         DEFAULT NULL COMMENT '通道密码',
    status       varchar(20)          DEFAULT 'OFF' COMMENT '在线状态 ON/OFF/UNREGISTERED',
    longitude    double               DEFAULT NULL COMMENT '经度',
    latitude     double               DEFAULT NULL COMMENT '纬度',
    ptz_type     tinyint              DEFAULT 0 COMMENT '云台类型 0未知 1球机 2半球 3枪机 4遥控枪机',
    parent_id    varchar(20) NOT NULL COMMENT '父节点国标码',
    source       tinyint     NOT NULL DEFAULT 3 COMMENT '数据来源 1新增 2导入 3注册',
    create_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_channel (device_id, channel_id),
    KEY          idx_channel_id (channel_id),
    KEY          idx_parent_id (parent_id),
    KEY          idx_status (status),
    KEY          idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_ptz_preset;
CREATE TABLE gb_ptz_preset
(
    id            bigint       NOT NULL COMMENT '主键ID',
    channel_db_id bigint       NOT NULL COMMENT '通道主键',
    preset_id     int          NOT NULL COMMENT '预置位编号',
    name          varchar(100) NOT NULL COMMENT '预置位名称',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_preset (channel_db_id, preset_id),
    KEY           idx_channel (channel_db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_ptz_cruise;
CREATE TABLE gb_ptz_cruise
(
    id            bigint       NOT NULL COMMENT '主键ID',
    channel_db_id bigint       NOT NULL COMMENT '通道主键',
    cruise_id     int          NOT NULL COMMENT '巡航路线编号',
    name          varchar(100) NOT NULL COMMENT '巡航路线名称',
    speed         int          NOT NULL DEFAULT 50 COMMENT '巡航速度（1~255）',
    stay_seconds  int          NOT NULL DEFAULT 10 COMMENT '预置位停留秒数',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_cruise (channel_db_id, cruise_id),
    KEY           idx_channel (channel_db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_ptz_cruise_point;
CREATE TABLE gb_ptz_cruise_point
(
    id           bigint       NOT NULL COMMENT '主键ID',
    cruise_id    bigint       NOT NULL COMMENT '巡航路线ID',
    preset_id    int          NOT NULL COMMENT '预置位编号',
    preset_name  varchar(100)          DEFAULT NULL COMMENT '预置位名称',
    stay_seconds int                   DEFAULT NULL COMMENT '停留秒数',
    sort_order   int          NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cruise_preset (cruise_id, preset_id),
    KEY          idx_cruise (cruise_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_platform;
CREATE TABLE gb_platform
(
    id          bigint      NOT NULL COMMENT '主键ID',
    platform_id varchar(20) NOT NULL COMMENT '国标ID',
    name        varchar(255)         DEFAULT NULL COMMENT '备注名',
    enabled     tinyint     NOT NULL DEFAULT 0 COMMENT '0禁用 1放行',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_id (platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS media_server;
CREATE TABLE media_server
(
    id               bigint      NOT NULL COMMENT '主键ID',
    server_id        varchar(64) NOT NULL COMMENT 'ZLM节点标识',
    name             varchar(100)         DEFAULT NULL COMMENT '节点名',
    host_ip          varchar(64) NOT NULL COMMENT '管理面IP',
    http_port        int         NOT NULL DEFAULT 80 COMMENT 'HTTP API端口',
    secret           varchar(100)         DEFAULT NULL COMMENT 'API secret',
    stream_ip        varchar(64)          DEFAULT NULL COMMENT '对外播放IP',
    stream_http_port int                  DEFAULT 0 COMMENT '对外HTTP端口',
    rtsp_port        int                  DEFAULT 0 COMMENT '对外RTSP端口',
    rtc_port         int                  DEFAULT 0 COMMENT '对外WebRTC端口',
    rtp_port_start   int                  DEFAULT 30000 COMMENT 'RTP端口起始',
    rtp_port_end     int                  DEFAULT 35000 COMMENT 'RTP端口截止',
    status           tinyint              DEFAULT 0 COMMENT '在线状态 0离线 1在线',
    hook_alive_time  datetime             DEFAULT NULL COMMENT '最近keepalive时间',
    create_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_id (server_id),
    KEY              idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS stream_proxy;
CREATE TABLE stream_proxy
(
    id              bigint        NOT NULL COMMENT '主键ID',
    app             varchar(50)   NOT NULL DEFAULT 'proxy' COMMENT 'app名',
    stream          varchar(100)  NOT NULL COMMENT 'stream名',
    src_url         varchar(1000) NOT NULL COMMENT '源地址',
    media_server_id varchar(64)            DEFAULT NULL COMMENT 'ZLM节点ID',
    rtp_type        tinyint       NOT NULL DEFAULT 0 COMMENT '拉流方式 0自适应 1TCP 2UDP 3组播',
    proxy_key       varchar(100)           DEFAULT NULL COMMENT 'ZLM代理key',
    status          tinyint       NOT NULL DEFAULT 0 COMMENT '0未启动 1已启动',
    last_error      varchar(500)           DEFAULT NULL COMMENT '失败原因',
    remark          varchar(255)           DEFAULT NULL COMMENT '备注',
    create_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY             idx_media_server_id (media_server_id),
    KEY             idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS stream_push;
CREATE TABLE stream_push
(
    id              bigint       NOT NULL COMMENT '主键ID',
    app             varchar(50)  NOT NULL COMMENT 'app名',
    stream          varchar(100) NOT NULL COMMENT 'stream名',
    media_server_id varchar(64)  NOT NULL COMMENT 'ZLM节点ID',
    vhost           varchar(100)          DEFAULT '__defaultVhost__' COMMENT 'vhost',
    stream_schema   varchar(100)          DEFAULT NULL COMMENT '推流协议',
    origin_type     varchar(20)           DEFAULT NULL COMMENT '推流类型 rtmp/rtsp/rtc',
    alive_second    int                   DEFAULT 0 COMMENT '存活秒数',
    push_time       datetime              DEFAULT NULL COMMENT '首次推流时间',
    client_id       varchar(64)           DEFAULT NULL COMMENT '推流方标识',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY             idx_media_server_id (media_server_id),
    KEY             idx_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS stream_push_client;
CREATE TABLE stream_push_client
(
    id          bigint      NOT NULL COMMENT '主键ID',
    client_id   varchar(64) NOT NULL COMMENT '推流方标识',
    token       varchar(64) NOT NULL COMMENT '鉴权令牌',
    enabled     tinyint     NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    remark      varchar(255)         DEFAULT NULL COMMENT '备注',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_parent_platform;
CREATE TABLE gb_parent_platform
(
    id               bigint       NOT NULL COMMENT '主键ID',
    name             varchar(255) NOT NULL COMMENT '上级平台名称',
    server_gb_id     varchar(20)  NOT NULL COMMENT '上级平台国标编码',
    server_gb_domain varchar(20)  NOT NULL COMMENT '上级平台国标域',
    server_ip        varchar(64)  NOT NULL COMMENT 'SIP IP',
    server_port      int          NOT NULL DEFAULT 5060 COMMENT 'SIP端口',
    username         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP用户名',
    password         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP密码',
    expires          int          NOT NULL DEFAULT 3600 COMMENT '注册有效期（秒）',
    keep_timeout     int          NOT NULL DEFAULT 60 COMMENT '心跳周期（秒）',
    transport        varchar(16)  NOT NULL DEFAULT 'UDP' COMMENT '传输协议',
    character_set    varchar(16)  NOT NULL DEFAULT 'GB2312' COMMENT '字符集',
    enabled          tinyint      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    status           tinyint      NOT NULL DEFAULT 0 COMMENT '在线状态 0离线 1在线',
    catalog_group    tinyint               DEFAULT NULL COMMENT 'Catalog单包Item数',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_gb_id (server_gb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_platform_group;
CREATE TABLE gb_platform_group
(
    id          bigint      NOT NULL COMMENT '主键ID',
    name        varchar(64) NOT NULL COMMENT '分组名称',
    code        varchar(20) NOT NULL COMMENT '分组国标编码',
    platform_id bigint      NOT NULL COMMENT '上级平台主键',
    parent_id   bigint      NOT NULL COMMENT '父分组主键',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_code (platform_id, code),
    KEY         idx_platform_parent (platform_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_platform_share;
CREATE TABLE gb_platform_share
(
    id           bigint   NOT NULL COMMENT '主键ID',
    platform_id  bigint   NOT NULL COMMENT '上级平台主键',
    group_id     bigint   NOT NULL COMMENT '推送分组主键',
    resource_id  bigint   NOT NULL COMMENT '通道主键',
    exposed_code varchar(20)       DEFAULT NULL COMMENT '对上级暴露国标码',
    create_time  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_resource (platform_id, resource_id),
    UNIQUE KEY uk_platform_exposed_code (platform_id, exposed_code),
    KEY          idx_platform_group (platform_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS gb_device_alarm;
CREATE TABLE gb_device_alarm
(
    id                bigint       NOT NULL COMMENT '主键ID',
    device_id         varchar(50)  NOT NULL COMMENT '设备国标ID',
    channel_id        varchar(50)           DEFAULT NULL COMMENT '通道国标ID',
    channel_name      varchar(255)          DEFAULT NULL COMMENT '通道名称',
    alarm_priority    tinyint               DEFAULT NULL COMMENT '报警级别 1~4',
    alarm_method      varchar(20)           DEFAULT NULL COMMENT '报警方式码',
    alarm_method_desc varchar(255)          DEFAULT NULL COMMENT '报警方式描述',
    alarm_type        varchar(10)           DEFAULT NULL COMMENT '报警类型码',
    alarm_time        datetime              DEFAULT NULL COMMENT '报警发生时间',
    alarm_description varchar(1000)         DEFAULT NULL COMMENT '报警内容',
    longitude         double                DEFAULT NULL COMMENT '经度',
    latitude          double                DEFAULT NULL COMMENT '纬度',
    handle_status     tinyint      NOT NULL DEFAULT 0 COMMENT '处置状态 0未处理 1已确认 2已处置 3误报',
    handle_remark     varchar(500)          DEFAULT NULL COMMENT '处置备注',
    handle_user       varchar(64)           DEFAULT NULL COMMENT '处置人',
    handle_time       datetime              DEFAULT NULL COMMENT '处置时间',
    receive_time      datetime              DEFAULT NULL COMMENT '平台收到时间',
    create_time       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_alarm_time (alarm_time),
    KEY idx_handle_status (handle_status),
    KEY idx_device (device_id),
    KEY idx_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET
FOREIGN_KEY_CHECKS = 1;
