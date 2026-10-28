USE
solo_db;

-- ============================================================
--  第三方开放 API（/open-api/v1/**）
--  与平台用户/角色体系完全解耦：第三方以"应用"身份接入，
--  凭 appKey/appSecret 做 HMAC 签名鉴权，按 scope 授权。
-- ============================================================

DROP TABLE IF EXISTS open_app;
CREATE TABLE open_app
(
    id              bigint       NOT NULL COMMENT '主键ID',
    app_key         varchar(64)  NOT NULL COMMENT '应用标识（随请求传输）',
    app_name        varchar(100) NOT NULL COMMENT '应用名称（第三方系统/公司）',
    -- 当前生效密钥（AES-GCM 加密存储，创建/换钥时明文只展示一次）
    secret_cipher   varchar(500) NOT NULL COMMENT '当前密钥密文',
    -- 双钥轮换：换钥过渡期内旧密钥仍可用，过渡期结束置空
    old_secret_cipher varchar(500)        DEFAULT NULL COMMENT '轮换期旧密钥密文',
    old_secret_expire_time datetime        DEFAULT NULL COMMENT '旧密钥失效时间',
    -- 逗号分隔的 scope 编码，如 gb:device:read,gb:play:ctrl
    scopes          varchar(1000)         DEFAULT '' COMMENT '授权范围（逗号分隔）',
    -- 逗号分隔的 IP/CIDR 白名单，为空不校验
    ip_whitelist    varchar(500)          DEFAULT NULL COMMENT 'IP白名单（逗号分隔）',
    callback_url    varchar(500)          DEFAULT NULL COMMENT 'Webhook 回调地址',
    callback_secret_cipher varchar(500)   DEFAULT NULL COMMENT '回调签名密钥密文（可独立吊销）',
    -- 逗号分隔的事件类型，空=不推送
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

-- 事件出队记录：一个事件对每个订阅应用 fan-out 一行，重试/对账都靠它
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

-- 调用审计：排障/对账用，保留周期由运维清理
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

-- ============================================================
--  管理端权限点（接在 sys_permission 现有最大 id 142 之后）
--  三级结构，跟其他模块保持一致：
--    143 开放平台      一级目录 type=1 url=NULL parent=0
--    144 应用管理      二级菜单 type=1 url=/openapi/app parent=143 （前端约定式路由靠这个 url）
--    145~148 增/查/改/换钥 按钮 type=2 parent=144
--  脚本可重复执行：先清旧的错误数据再插入。
-- ============================================================
DELETE FROM sys_role_permission WHERE permission_id BETWEEN 143 AND 148;
DELETE FROM sys_permission          WHERE id BETWEEN 143 AND 148;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (143, '开放平台', 'openapi',        NULL,            1, 0,   10, 'IconLock', 1, 0, NOW(), NOW()),
 (144, '应用管理', 'openapi:app',    '/openapi/app',  1, 143, 1,  'IconLock', 1, 0, NOW(), NOW()),
 (145, '应用查询', 'openapi:app:list',   NULL, 2, 144, 1, NULL, 1, 0, NOW(), NOW()),
 (146, '应用新增', 'openapi:app:add',    NULL, 2, 144, 2, NULL, 1, 0, NOW(), NOW()),
 (147, '应用编辑', 'openapi:app:edit',   NULL, 2, 144, 3, NULL, 1, 0, NOW(), NOW()),
 (148, '应用换钥', 'openapi:app:rotate', NULL, 2, 144, 4, NULL, 1, 0, NOW(), NOW());

-- 顺手把这几个权限挂给超级管理员（role_id=1）。其他角色在"角色管理"里自己勾。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 143 AND 148
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);

-- ============================================================
--  调用日志菜单（149~150 已被 broadcast.sql 的语音播报占用，从 151 开始）
--    151 调用日志  二级菜单 type=1 url=/openapi/log parent=143
--    152 日志查询 / 153 日志导出 按钮 type=2 parent=151
--  脚本可重复执行。
-- ============================================================
DELETE FROM sys_role_permission WHERE permission_id BETWEEN 151 AND 153;
DELETE FROM sys_permission          WHERE id BETWEEN 151 AND 153;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (151, '调用日志', 'openapi:log',       '/openapi/log', 1, 143, 2, 'IconLock', 1, 0, NOW(), NOW()),
 (152, '日志查询', 'openapi:log:list',  NULL,           2, 151, 1, NULL,       1, 0, NOW(), NOW()),
 (153, '日志导出', 'openapi:log:export', NULL,          2, 151, 2, NULL,       1, 0, NOW(), NOW());

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 151 AND 153
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);
