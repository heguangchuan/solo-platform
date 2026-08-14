-- =====================================================
-- Solo · GB/T 28181 国标模块数据库初始化
-- 依赖：先执行 sql/solo_db.sql 创建 solo_db 库
-- 范围：所有 gb_ 前缀的表
-- 关联规则：表之间只使用逻辑关联（如 device_id 引用 gb_device.device_id），不建物理外键
-- =====================================================
USE
solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 国标设备表
-- =====================================================
DROP TABLE IF EXISTS gb_device;
CREATE TABLE gb_device
(
    id                     bigint      NOT NULL COMMENT '主键ID（雪花）',
    device_id              varchar(50) NOT NULL COMMENT '设备国标ID（20位）',
    report_name            varchar(100)         DEFAULT NULL COMMENT '设备上报原始名称（DeviceInfo 响应）',
    name                   varchar(100)         DEFAULT NULL COMMENT '用户自定义显示名称（展示优先级高于 report_name）',
    manufacturer           varchar(100)         DEFAULT NULL COMMENT '厂商',
    model                  varchar(100)         DEFAULT NULL COMMENT '型号',
    firmware               varchar(50)          DEFAULT NULL COMMENT '固件版本',
    ip                     varchar(64)          DEFAULT NULL COMMENT '最后注册来源IP',
    recv_ip                varchar(64)          DEFAULT NULL COMMENT '收流IP覆盖；留空使用注册源IP，双栈内网互通时填设备内网IP',
    port                   int                  DEFAULT NULL COMMENT '最后注册来源端口',
    local_ip               varchar(64)          DEFAULT NULL COMMENT '本平台和该设备通信的本机IP',
    host_address           varchar(80)          DEFAULT NULL COMMENT '设备主机地址 ip:port',
    transport              varchar(10)          DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    expires                int                  DEFAULT 3600 COMMENT '注册有效期（秒）',
    charset                varchar(20)          DEFAULT 'GB2312' COMMENT '字符集',
    status                 tinyint              DEFAULT 0 COMMENT '在线状态：0离线 1在线 2未注册（仅由SIP交互改变）',
    source                 tinyint     NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册',
    register_time          datetime             DEFAULT NULL COMMENT '最后注册时间',
    keepalive_time         datetime             DEFAULT NULL COMMENT '最后心跳时间',
    last_catalog_sync_time datetime             DEFAULT NULL COMMENT '最后 Catalog 同步完成时间',
    create_time            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    KEY                    idx_status (status),
    KEY                    idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标设备';

-- =====================================================
-- 国标资源目录项表（设备通道 + 下级平台通道/目录 + 业务分组；见 ADR-023）
-- 通道（parental=0）：device_id 必填，业务唯一键 (device_id, channel_id)
-- 分组（parental=1）：device_id 也必填，标识"该分组由哪台设备/下级平台推送"
-- 数据来源三类：
--   1) NVR / IPC / 单点设备 走 SIP REGISTER 推 Catalog
--   2) 下级平台 视为设备接入本平台（ADR-016），推 Catalog 入这里
--   3) 运营手建 / 表格导入
-- 逻辑关联 gb_device.device_id，不建物理外键
-- =====================================================
DROP TABLE IF EXISTS gb_device_channel;
CREATE TABLE gb_device_channel
(
    id           bigint      NOT NULL COMMENT '主键ID（雪花）',
    device_id    varchar(50) NOT NULL COMMENT '所属设备国标ID（逻辑关联 gb_device.device_id）；通道与分组均必填——分组标识推送源设备',
    channel_id   varchar(50) NOT NULL COMMENT 'catalog item 20 位国标码：分组=215业务分组/216虚拟组织；通道=131摄像机/132IPC 等',
    report_name  varchar(255)         DEFAULT NULL COMMENT 'catalog item 上报原始名称（仅对通道有意义，分组时为 NULL）',
    name         varchar(255)         DEFAULT NULL COMMENT '用户自定义显示名称（展示优先级高于 report_name）',
    manufacturer varchar(100)         DEFAULT NULL COMMENT '厂商（仅通道）',
    model        varchar(100)         DEFAULT NULL COMMENT '型号（仅通道）',
    owner        varchar(50)          DEFAULT NULL COMMENT '设备归属（仅通道）',
    civil_code   varchar(20)          DEFAULT NULL COMMENT '行政区划编码（仅通道）',
    block        varchar(100)         DEFAULT NULL COMMENT '警区（仅通道）',
    address      varchar(500)         DEFAULT NULL COMMENT '安装地址（仅通道）',
    parental     tinyint     NOT NULL DEFAULT 0 COMMENT '0=通道（叶子）/ 1=分组（容器）。无 NULL 语义',
    safety_way   tinyint              DEFAULT 0 COMMENT '信令安全模式（仅通道）',
    register_way tinyint              DEFAULT 1 COMMENT '注册方式（仅通道）',
    cert_num     varchar(50)          DEFAULT NULL COMMENT '证书序列号（仅通道）',
    certifiable  tinyint              DEFAULT 0 COMMENT '证书有效标识（仅通道）',
    err_code     int                  DEFAULT NULL COMMENT '证书无效原因码（仅通道）',
    end_time     datetime             DEFAULT NULL COMMENT '证书终止有效期（仅通道）',
    secrecy      tinyint              DEFAULT 0 COMMENT '保密属性（仅通道）',
    ip_address   varchar(64)          DEFAULT NULL COMMENT '通道IP（仅通道）',
    port         int                  DEFAULT NULL COMMENT '通道端口（仅通道）',
    password     varchar(100)         DEFAULT NULL COMMENT '通道密码（仅通道）',
    status       varchar(20)          DEFAULT 'OFF' COMMENT '在线状态 ON/OFF/UNREGISTERED（仅由SIP交互改变；仅通道）',
    longitude double DEFAULT NULL COMMENT '经度（仅通道）',
    latitude double DEFAULT NULL COMMENT '纬度（仅通道）',
    ptz_type     tinyint              DEFAULT 0 COMMENT '云台类型 0未知 1球机 2半球 3固定枪机 4遥控枪机（仅通道）',
    parent_id    varchar(20)  NOT NULL COMMENT '父 catalog item 的 20 位国标码：通道→所属设备 device_id / 父分组 channel_id；分组→父分组 channel_id 或所属设备 device_id。仅供级联 XML ParentID 与共享树建层级使用，不回前端展示',
    source       tinyint     NOT NULL DEFAULT 3 COMMENT '数据来源：1系统新增 2表格导入 3设备注册',
    create_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 通道（parental=0）：按所属设备内 channel_id 唯一（device_id 是 GB 码字符串）
    UNIQUE KEY uk_device_channel (device_id, channel_id),
    KEY          idx_channel_id (channel_id),
    KEY          idx_parent_id (parent_id),
    KEY          idx_status (status),
    KEY          idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标资源目录项表（通道 + 分组合一，按 parental 区分；见 ADR-023）';

-- =====================================================
-- 云台预置位表
-- 每个通道可以配置多个预置位，保存角度记忆，一键调用
-- =====================================================
DROP TABLE IF EXISTS gb_ptz_preset;
CREATE TABLE gb_ptz_preset
(
    id               bigint       NOT NULL COMMENT '主键ID（雪花）',
    channel_db_id    bigint       NOT NULL COMMENT '通道数据库主键（gb_device_channel.id）',
    preset_id        int          NOT NULL COMMENT '预置位编号（设备侧编号，1~N）',
    name             varchar(100) NOT NULL COMMENT '预置位名称（如"大门入口"）',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY             idx_channel (channel_db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='云台预置位表';

-- =====================================================
-- 国标接入禁用名单（黑名单）
-- 设备与下级平台一律自由注册（注册时机不可控，白名单模式不现实），
-- 只有在本表登记的国标 ID 会被 REGISTER 流程以 403 拒绝。
-- 平台与设备不再区分：接入后统一是 gb_device 行，运行态查那里。
-- =====================================================
DROP TABLE IF EXISTS gb_platform;
CREATE TABLE gb_platform
(
    id          bigint       NOT NULL COMMENT '主键ID（雪花）',
    platform_id varchar(20)  NOT NULL COMMENT '被禁用的国标ID（20位，设备或下级平台通用）',
    name        varchar(255)          DEFAULT NULL COMMENT '备注名（可空；仅便于运维辨识，不参与业务）',
    enabled     tinyint      NOT NULL DEFAULT 0 COMMENT '禁用开关：0禁用（拒绝注册，默认）1临时放行（保留记录但不拦截）',
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_id (platform_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国标接入禁用名单（黑名单）：设备/下级平台默认自由注册，仅本表登记的 ID 被拒绝';

-- =====================================================
-- 流媒体节点表（ZLMediaKit 实例登记）
-- 关联规则：通过 server_id（字符串）与 ZLM config.ini 中 [general].mediaServerId 对齐
-- =====================================================
DROP TABLE IF EXISTS media_server;
CREATE TABLE media_server
(
    id               bigint      NOT NULL COMMENT '主键ID（雪花）',
    server_id        varchar(64) NOT NULL COMMENT 'ZLM 节点唯一标识（与 [general].mediaServerId 一致）',
    name             varchar(100)         DEFAULT NULL COMMENT '节点显示名',
    host_ip          varchar(64) NOT NULL COMMENT 'ZLM 内网/管理面 IP',
    http_port        int         NOT NULL DEFAULT 80 COMMENT 'ZLM HTTP API 端口',
    secret           varchar(100)         DEFAULT NULL COMMENT 'ZLM API secret',
    stream_ip        varchar(64)          DEFAULT NULL COMMENT '对外播放 IP（HTTP-FLV/HLS/WebRTC），留空则同 host_ip',
    stream_http_port int                  DEFAULT 0 COMMENT '对外HTTP/WS-FLV/HLS端口（0=同 http_port）',
    rtsp_port        int                  DEFAULT 0 COMMENT '对外RTSP端口（0=禁用 RTSP URL）',
    rtc_port         int                  DEFAULT 0 COMMENT '对外WebRTC端口（0=禁用 RTC URL）',
    rtp_port_start   int                  DEFAULT 30000 COMMENT 'RTP 端口段起始',
    rtp_port_end     int                  DEFAULT 35000 COMMENT 'RTP 端口段截止',
    status           tinyint              DEFAULT 0 COMMENT '在线状态：0离线 1在线（由 on_server_keepalive 维护）',
    hook_alive_time  datetime             DEFAULT NULL COMMENT '最近一次 on_server_keepalive 时间',
    create_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          tinyint     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_id (server_id),
    KEY              idx_status (status)
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
    id                         bigint        NOT NULL COMMENT '主键ID（雪花）',
    name                       varchar(100)           DEFAULT NULL COMMENT '显示名（用户填）',
    app                        varchar(50)   NOT NULL DEFAULT 'proxy' COMMENT 'ZLM app 名',
    stream                     varchar(100)  NOT NULL COMMENT 'ZLM stream 名',
    src_url                    varchar(1000) NOT NULL COMMENT '源地址：rtsp:// rtmp:// http-flv/hls 等',
    media_server_id            varchar(64)            DEFAULT NULL COMMENT '目标 ZLM 节点 serverId（留空则启动时自动挑一个可用节点）',
    rtp_type                   tinyint       NOT NULL DEFAULT 1 COMMENT 'RTSP 拉流方式：0=TCP+UDP 自适应 1=TCP 2=UDP 3=组播',
    enable_audio               tinyint       NOT NULL DEFAULT 1 COMMENT '是否拉音频 0否 1是',
    enable_mp4                 tinyint       NOT NULL DEFAULT 0 COMMENT '是否录制 MP4 0否 1是',
    enable_disable_none_reader tinyint       NOT NULL DEFAULT 0 COMMENT '无人观看时是否自动停用 0否 1是',
    enabled                    tinyint       NOT NULL DEFAULT 1 COMMENT '业务启用开关 0禁用 1启用（禁用时不推给 ZLM）',
    proxy_key                  varchar(100)           DEFAULT NULL COMMENT 'ZLM addStreamProxy 返回的 key，用于 delStreamProxy',
    status                     tinyint       NOT NULL DEFAULT 0 COMMENT '运行状态 0未启动/离线 1已推给 ZLM',
    last_error                 varchar(500)           DEFAULT NULL COMMENT '最近一次启动失败原因（仅用于展示）',
    remark                     varchar(255)           DEFAULT NULL COMMENT '备注',
    create_time                datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY                        idx_media_server_id (media_server_id),
    KEY                        idx_status (status),
    KEY                        idx_enabled (enabled)
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
    id              bigint       NOT NULL COMMENT '主键ID（雪花）',
    app             varchar(50)  NOT NULL COMMENT 'ZLM app 名',
    stream          varchar(100) NOT NULL COMMENT 'ZLM stream 名',
    media_server_id varchar(64)  NOT NULL COMMENT '所属 ZLM 节点 serverId',
    vhost           varchar(100)          DEFAULT '__defaultVhost__' COMMENT 'ZLM vhost',
    stream_schema   varchar(100)          DEFAULT NULL COMMENT '推流协议（rtsp/rtmp/hls/ts/fmp4，通常只留 rtsp）',
    origin_type     varchar(20)           DEFAULT NULL COMMENT '推流类型：rtmp / rtsp / rtc',
    pushing         tinyint      NOT NULL DEFAULT 0 COMMENT '当前是否在推流：0已断 1推流中',
    alive_second    int                   DEFAULT 0 COMMENT '存活秒数（快照，展示用）',
    push_time       datetime              DEFAULT NULL COMMENT '首次开始推流时间',
    client_id       varchar(64)           DEFAULT NULL COMMENT '推流方标识（关联 stream_push_client.client_id，未开启鉴权则为空）',
    create_time     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_stream (app, stream),
    KEY             idx_media_server_id (media_server_id),
    KEY             idx_pushing (pushing),
    KEY             idx_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推流记录表';

-- =====================================================
-- 推流方鉴权表（stream_push_client）
-- 一条 = 一个推流方。推流时在 RTMP/RTSP URL 上带 ?token=xxx，ZLM on_publish 里查此表。
-- token 明文存储（256 位随机熵），泄漏时管理员点"重置 token"重发。
-- =====================================================
DROP TABLE IF EXISTS stream_push_client;
CREATE TABLE stream_push_client
(
    id          bigint      NOT NULL COMMENT '主键ID（雪花）',
    client_id   varchar(64) NOT NULL COMMENT '推流方标识（用户填，唯一）',
    token       varchar(64) NOT NULL COMMENT '鉴权令牌（SecureRandom 32 字节 Base64URL）',
    enabled     tinyint     NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    remark      varchar(255)         DEFAULT NULL COMMENT '备注',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    username         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP 认证用户名（默认同 gb28181.id）',
    password         varchar(64)  NOT NULL DEFAULT '' COMMENT 'SIP 认证密码',
    expires          int          NOT NULL DEFAULT 3600 COMMENT '注册有效期（秒）',
    keep_timeout     int          NOT NULL DEFAULT 60 COMMENT '心跳周期（秒）',
    transport        varchar(16)  NOT NULL DEFAULT 'UDP' COMMENT '传输协议 UDP/TCP',
    character_set    varchar(16)  NOT NULL DEFAULT 'GB2312' COMMENT '字符集',
    enabled          tinyint      NOT NULL DEFAULT 1 COMMENT '启用状态：0禁用 1启用',
    status           tinyint      NOT NULL DEFAULT 0 COMMENT '在线状态：0离线 1在线（后续注册成功回填）',
    catalog_group    tinyint               DEFAULT NULL COMMENT 'Catalog 响应单包 Item 数：1/2/4/8（ADR-027）。NULL 走全局默认 gb28181.cascade.catalog-page-size',
    create_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_gb_id (server_gb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台配置表';

-- =====================================================
-- 上级平台推送目录分组表（gb_platform_group）：per-platform 的推送树节点
-- 推送树与 catalog（gb_device_channel）解耦，独立的"该平台要推送给上级的目录结构"。
-- parent_id 引用同表 id（雪花），不是国标码——平台内自引用，uk_platform_code 保证国标编码唯一。
-- 一行 = 一个上级平台下的一个推送目录分组，根节点的 parent_id 固定为 0。
-- =====================================================
DROP TABLE IF EXISTS gb_platform_group;
CREATE TABLE gb_platform_group
(
    id          bigint      NOT NULL COMMENT '主键ID（雪花）',
    name        varchar(64) NOT NULL COMMENT '分组名称（运营可读）',
    code        varchar(20) NOT NULL COMMENT '分组国标编码（GB/T 28181-2016 附录 D 20 位数字，前 10 位 domain + 第 11-13 位 业务分类 215=业务分组/216=虚拟组织 + 后 7 位序号，平台内唯一）',
    platform_id bigint      NOT NULL COMMENT '所属上级平台主键（gb_parent_platform.id）',
    parent_id   bigint      NOT NULL COMMENT '父分组主键（引用同表 id；根节点 parent_id = 自身 id 即自指）',
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_code (platform_id, code),
    KEY         idx_platform_parent (platform_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台推送目录分组表（per-platform 推送树节点）';

-- =====================================================
-- 上级平台共享通道表（gb_platform_share）：推送给上级的通道
-- 本平台作为下级向上级共享资源时，选中的通道记录。
-- 一行 = 一个上级平台 × 一个推送分组 × 一个通道。
--
-- resource_id：通道主键（gb_device_channel.id，parental=0 的行）。本表只存通道——分组（parental=1）
--   是本平台内部的目录组织，当前不推送给上级（Catalog XML 只发通道 Item）；若未来要推分组 Item，
--   届时再加类型区分字段。
-- group_id：所属推送分组（gb_platform_group.id）。同一通道在同一平台下只能挂载一次
--   （uk_platform_resource），即只能落在某一个推送分组下。想"移动"到另一组：先卸载旧组再添加到新组。
-- exposed_code：撞码时分配的平台命名空间内唯一 20 位码（ADR-020 决策 4 / ADR-026 决策 1）。
--   撞码判定是「平台级」的（不是分组级）——上级看到的是扁平 DeviceID 空间，跨组同码也会让上级
--   INVITE 认错通道。推送时 DeviceID = exposedCode ?? channel_id。
-- 按组拉已挂载走 idx_platform_group。
-- =====================================================
DROP TABLE IF EXISTS gb_platform_share;
CREATE TABLE gb_platform_share
(
    id           bigint      NOT NULL COMMENT '主键ID（雪花）',
    platform_id  bigint      NOT NULL COMMENT '上级平台主键（gb_parent_platform.id）',
    group_id     bigint      NOT NULL COMMENT '所属推送分组（gb_platform_group.id，UI 强制先选组才能挂载）',
    resource_id  bigint      NOT NULL COMMENT '通道主键（gb_device_channel.id，parental=0）',
    exposed_code varchar(20) DEFAULT NULL COMMENT '对上级暴露的唯一国标码（撞码时分配，平台内唯一；不撞码时 NULL 表示透传 channel_id）',
    create_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_resource (platform_id, resource_id),
    UNIQUE KEY uk_platform_exposed_code (platform_id, exposed_code),
    KEY          idx_platform_group (platform_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上级平台共享通道表（同一通道在同一平台下只能挂载一次，必须先卸再换组）';

SET
FOREIGN_KEY_CHECKS = 1;
