
CREATE DATABASE IF NOT EXISTS solo_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE solo_db;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;



DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user
(
    id                 bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '用户名',
    password           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MD5加密密码',
    nickname           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '昵称',
    email              varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '邮箱',
    phone              varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '手机号',
    avatar             varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '头像URL',
    status             tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    pwd_reset_required tinyint                                                       NOT NULL DEFAULT '0' COMMENT '是否首次登录强制改密: 0否 1是',
    deleted            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY                idx_username (username),
    KEY                idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

INSERT INTO sys_user (id, username, password, nickname, email, phone, avatar, status, pwd_reset_required, deleted,
                      create_time, update_time)
VALUES (1, 'admin', '0192023a7bbd73250516f069df18b500', '管理员', NULL, NULL, NULL, 1, 0, 0, '2026-06-14 22:35:02',
        '2026-06-14 22:35:02');

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
    subscribe_cycle_for_catalog int            DEFAULT 0 COMMENT '目录订阅周期（秒），0=不订阅',
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

DROP TABLE IF EXISTS gb_mobile_position;
CREATE TABLE gb_mobile_position
(
    id            bigint       NOT NULL COMMENT '主键ID',
    device_id     varchar(50)  NOT NULL COMMENT '设备国标ID',
    device_name   varchar(255)          DEFAULT NULL COMMENT '设备名称（落库时冗余）',
    position_time datetime              DEFAULT NULL COMMENT '设备上报的定位时间',
    longitude     double                DEFAULT NULL COMMENT '经度（WGS84）',
    latitude      double                DEFAULT NULL COMMENT '纬度（WGS84）',
    altitude      double                DEFAULT NULL COMMENT '海拔（米）',
    speed         double                DEFAULT NULL COMMENT '速度（设备原值，km/h 或 m/s 视厂商而定）',
    direction     double                DEFAULT NULL COMMENT '方向角 0~359，正北为 0 顺时针',
    receive_time  datetime              DEFAULT NULL COMMENT '平台收到报文时间',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_device_time (device_id, position_time),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备移动位置（GPS 轨迹）';

DROP TABLE IF EXISTS gb_record_file;
CREATE TABLE gb_record_file
(
    id            bigint       NOT NULL COMMENT '主键ID',
    device_id     varchar(50)           DEFAULT NULL COMMENT '设备国标ID',
    channel_id    varchar(50)           DEFAULT NULL COMMENT '通道国标ID',
    stream_id     varchar(100) NOT NULL COMMENT 'ZLM流ID（GbInviteSession.streamId）',
    app           varchar(50)  NOT NULL DEFAULT 'rtp' COMMENT 'ZLM app名',
    server_id     varchar(64)           DEFAULT NULL COMMENT 'ZLM节点ID',
    start_time    datetime              DEFAULT NULL COMMENT '录像段开始时间（优先取ZLM回调start_time）',
    end_time      datetime              DEFAULT NULL COMMENT '录像段结束时间',
    duration      double                DEFAULT NULL COMMENT '时长(秒)',
    file_size     bigint                DEFAULT NULL COMMENT '文件大小(字节)',
    object_name   varchar(500)          DEFAULT NULL COMMENT 'ZLM录像相对路径(record/rtp/...mp4)',
    thumb_object_name varchar(500)        DEFAULT NULL COMMENT '缩略图路径(截图抓拍，预留)',
    status        tinyint      NOT NULL DEFAULT 0 COMMENT '状态 0上传中 1成功 2失败',
    fail_reason   varchar(500)          DEFAULT NULL COMMENT '最近一次失败原因',
    retry_count   int          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    source_url    varchar(1000)         DEFAULT NULL COMMENT 'ZLM回调的MP4下载地址（失败重试要用）',
    hook_key      varchar(500)          DEFAULT NULL COMMENT '幂等键（ZLM回调path），同一切片只落一次',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hook_key (hook_key),
    KEY idx_device_channel (device_id, channel_id),
    KEY idx_start_time (start_time),
    KEY idx_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='云端录像文件元数据';

DROP TABLE IF EXISTS gb_view_record;
CREATE TABLE gb_view_record
(
    id              bigint       NOT NULL COMMENT '主键ID',
    session_id      varchar(64)  NOT NULL COMMENT 'ZLM 播放会话 id（on_play/on_flow_report 的 id），幂等键',
    actor_type      tinyint      NOT NULL COMMENT '观看者类型 1平台用户 2开放API应用',
    actor_id        bigint                DEFAULT NULL COMMENT '观看者 ID（用户 ID 或开放应用 ID）',
    actor_name      varchar(100)          DEFAULT NULL COMMENT '观看者名称快照（用户名/应用名）',
    device_id       varchar(50)           DEFAULT NULL COMMENT '设备国标ID',
    channel_id      varchar(50)           DEFAULT NULL COMMENT '通道国标ID',
    stream_id       varchar(100) NOT NULL COMMENT 'ZLM 流 ID',
    app             varchar(50)  NOT NULL DEFAULT 'rtp' COMMENT 'ZLM app 名',
    stream_schema   varchar(20)           DEFAULT NULL COMMENT '播放协议（rtsp/rtmp/hls/fmp4/rtc）',
    client_ip       varchar(64)           DEFAULT NULL COMMENT '观看端 IP',
    media_server_id varchar(64)           DEFAULT NULL COMMENT 'ZLM 节点 ID',
    start_time      datetime     NOT NULL COMMENT '观看开始时间（on_play 落库时间）',
    end_time        datetime              DEFAULT NULL COMMENT '观看结束时间',
    duration_sec    double                DEFAULT NULL COMMENT '观看时长（秒）',
    total_bytes     bigint                DEFAULT NULL COMMENT '会话总字节（on_flow_report 的 totalBytes）',
    status          tinyint      NOT NULL DEFAULT 0 COMMENT '状态 0观看中 1正常结束 2兜底关闭',
    close_reason    varchar(50)           DEFAULT NULL COMMENT '关闭原因 flow_report/stream_closed/none_reader/sweep',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_stream_id (stream_id),
    KEY idx_actor_start (actor_type, actor_id, start_time),
    KEY idx_status_start (status, start_time),
    KEY idx_device_channel (device_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='观看记录（谁看了哪路流、看了多久）';

-- ============================================================
--  3/5  ONVIF 模块
-- ============================================================

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

-- ============================================================
--  4/5  开放 API 模块（应用 / Webhook / 调用日志 + 权限 143~148、151~153）
-- ============================================================

DROP TABLE IF EXISTS open_app;
CREATE TABLE open_app
(
    id              bigint       NOT NULL COMMENT '主键ID',
    app_key         varchar(64)  NOT NULL COMMENT '应用标识（随请求传输）',
    app_name        varchar(100) NOT NULL COMMENT '应用名称（第三方系统/公司）',
    secret_cipher   varchar(500) NOT NULL COMMENT '当前密钥密文',
    old_secret_cipher varchar(500)        DEFAULT NULL COMMENT '轮换期旧密钥密文',
    old_secret_expire_time datetime        DEFAULT NULL COMMENT '旧密钥失效时间',
    scopes          varchar(1000)         DEFAULT '' COMMENT '授权范围（逗号分隔）',
    ip_whitelist    varchar(500)          DEFAULT NULL COMMENT 'IP白名单（逗号分隔）',
    callback_url    varchar(500)          DEFAULT NULL COMMENT 'Webhook 回调地址',
    callback_secret_cipher varchar(500)   DEFAULT NULL COMMENT '回调签名密钥密文（可独立吊销）',
    event_subscriptions varchar(1000)     DEFAULT '' COMMENT '订阅事件类型（逗号分隔）',
    qps_limit       int          NOT NULL DEFAULT 10 COMMENT 'QPS上限，0=不限',
    daily_limit     bigint       NOT NULL DEFAULT 0 COMMENT '日调用量上限，0=不限',
    status          tinyint      NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
    expire_time     datetime              DEFAULT NULL COMMENT '应用过期时间，空=永不过期',
    remark          varchar(500)          DEFAULT NULL COMMENT '备注',
    deleted         tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放API应用';

DROP TABLE IF EXISTS open_webhook_event;
CREATE TABLE open_webhook_event
(
    id              bigint       NOT NULL COMMENT '主键ID',
    event_id        varchar(40)  NOT NULL COMMENT '事件唯一ID（第三方按此幂等去重）',
    app_id          bigint       NOT NULL COMMENT '目标应用ID',
    app_key         varchar(64)  NOT NULL COMMENT '目标应用标识（冗余，便于排查）',
    event_type      varchar(50)  NOT NULL COMMENT '事件类型',
    payload         mediumtext   NOT NULL COMMENT '事件报文JSON',
    callback_url    varchar(500) NOT NULL COMMENT '投递时使用的回调地址（快照）',
    status          tinyint      NOT NULL DEFAULT 0 COMMENT '状态 0待投递 1成功 2待重试 3放弃',
    retry_count     int          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry       int          NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time datetime     NOT NULL COMMENT '下次投递时间',
    last_error      varchar(500)          DEFAULT NULL COMMENT '最近一次失败原因',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_app (event_id, app_id),
    KEY idx_status_retry (status, next_retry_time),
    KEY idx_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放API Webhook事件';

DROP TABLE IF EXISTS open_api_log;
CREATE TABLE open_api_log
(
    id              bigint       NOT NULL COMMENT '主键ID',
    app_key         varchar(64)           DEFAULT NULL COMMENT '调用方应用标识（鉴权失败也可能为空）',
    request_id      varchar(40)           DEFAULT NULL COMMENT '请求唯一号',
    method          varchar(10)  NOT NULL COMMENT 'HTTP方法',
    path            varchar(300) NOT NULL COMMENT '请求路径',
    query_string    varchar(1000)         DEFAULT NULL COMMENT '查询串',
    client_ip       varchar(64)           DEFAULT NULL COMMENT '客户端IP',
    http_status     int          NOT NULL DEFAULT 0 COMMENT 'HTTP状态码',
    biz_code        int                   DEFAULT NULL COMMENT '业务码',
    cost_ms         bigint       NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    error_msg       varchar(500)          DEFAULT NULL COMMENT '失败原因',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_app_time (app_key, create_time),
    KEY idx_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放API调用日志';

SET FOREIGN_KEY_CHECKS = 1;
