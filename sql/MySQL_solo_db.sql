-- ============================================================
--  Solo Platform 首次部署脚本（合并版）
--  MySQL 8+ · 一次执行完成全部初始化
--  系统底座 + 国标 GB28181 + ONVIF + 开放 API + 语音播报权限
--
--  ⚠ 仅用于全新部署：脚本会 DROP 并重建全部表，已有数据会被清空。
--  脚本为全量重建，不含增量迁移；已部署环境升级请先备份数据。
--
--  执行方式：mysql -uroot -p < sql/MySQL_solo_db.sql
--  数据库：solo_db（自动创建，utf8mb4）
-- ============================================================

CREATE DATABASE IF NOT EXISTS solo_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE solo_db;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
--  1/5  系统模块（RBAC / 字典 / 参数 / 文件 / 日志 / 消息）
-- ============================================================

DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config
(
    id           bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '参数ID',
    config_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数名称',
    config_key   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数键',
    config_value varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值',
    remark       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted      tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    KEY          idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数表';

DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
    dict_type   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
    dict_label  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典标签',
    dict_value  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键值',
    sort        int                                                           NOT NULL DEFAULT '1' COMMENT '排序',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY         idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, deleted, create_time, update_time)
VALUES (2066820557811949570, 'sys_file_types', 'mp4', '1', 2, 1, 0, '2026-06-16 17:49:55', '2026-06-16 17:49:55'),
       (2066820617408815105, 'sys_file_types', 'png', '2', 1, 1, 0, '2026-06-16 17:49:10', '2026-06-16 17:50:10');

DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
    dict_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
    dict_type   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (dict_type),
    KEY         idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file
(
    id            bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    original_name varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传时的原始文件名',
    storage_name  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径名（含哈希，避免重名）',
    url           varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可访问URL（相对路径或绝对URL）',
    ext           varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '扩展名（小写，不带点）',
    mime_type     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT 'Content-Type',
    size          bigint                                                        NOT NULL COMMENT '字节数',
    uploader_id   bigint                                                                 DEFAULT NULL COMMENT '上传人ID',
    uploader_name varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '上传人账号',
    deleted       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY           idx_uploader (uploader_id),
    KEY           idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件管理表';

DROP TABLE IF EXISTS sys_server;
CREATE TABLE sys_server
(
    id                 bigint                                                        NOT NULL COMMENT '服务器ID',
    server_name        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务器名称',
    host               varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主机地址，IP或域名',
    port               int                                                           NOT NULL DEFAULT 22 COMMENT 'SSH端口',
    username           varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
    auth_type          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'PASSWORD' COMMENT '认证方式: PASSWORD密码 PRIVATE_KEY密钥',
    password_secret    varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '密码密文',
    private_key_secret text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '私钥密文',
    os_type            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '操作系统类型',
    status             tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0停用 1启用',
    description        varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '描述',
    deleted            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_name (server_name),
    KEY                idx_host (host),
    KEY                idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器管理表';

DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log
(
    id              bigint                                                       NOT NULL COMMENT '日志ID',
    module          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块名',
    business_type   varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型: ADD/EDIT/DELETE/EXPORT/IMPORT/LOGIN/LOGOUT/OTHER',
    description     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '操作描述',
    request_method  varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT 'HTTP方法',
    request_url     varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT '请求URL',
    request_param   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '请求参数JSON',
    response_status tinyint                                                      NOT NULL DEFAULT '1' COMMENT '响应状态: 0失败 1成功',
    error_msg       varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        DEFAULT NULL COMMENT '错误信息',
    cost_ms         bigint                                                                DEFAULT NULL COMMENT '耗时(毫秒)',
    operator_id     bigint                                                                DEFAULT NULL COMMENT '操作人ID',
    operator_name   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '操作人用户名',
    client_source   varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '客户端来源: web/app/...',
    client_ip       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '客户端IP',
    user_agent      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         DEFAULT NULL COMMENT 'User-Agent',
    create_time     datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY             idx_create_time (create_time),
    KEY             idx_operator_name (operator_name),
    KEY             idx_module_type (module, business_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

DROP TABLE IF EXISTS sys_message;
CREATE TABLE sys_message
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    username    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '接收用户名',
    title       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息标题',
    content     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
    type        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT 'SYSTEM' COMMENT '消息类型: SYSTEM系统通知,NOTICE公告,REMINDER提醒',
    level       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT 'INFO' COMMENT '消息级别: INFO普通,WARNING警告,ERROR错误,IMPORTANT重要',
    read_flag   tinyint                                                       NOT NULL DEFAULT '0' COMMENT '是否已读: 0未读 1已读',
    route       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '跳转路由',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY         idx_username (username),
    KEY         idx_read_flag (read_flag),
    KEY         idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';

DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission
(
    id              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    permission_name varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
    permission_code varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
    url             varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '关联URL',
    type            tinyint                                                       NOT NULL COMMENT '类型: 1菜单 2按钮',
    parent_id       bigint                                                        NOT NULL DEFAULT '0' COMMENT '父级ID',
    sort            int                                                           NOT NULL DEFAULT '0' COMMENT '排序',
    icon            varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci           DEFAULT NULL COMMENT '图标',
    status          tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted         tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY             idx_permission_code (permission_code),
    KEY             idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (1, '系统管理', 'system', NULL, 1, 0, 1, 'IconSettings', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (7, '用户管理', 'system:user', '/system/user', 1, 1, 1, 'IconUsers', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (8, '用户查询', 'system:user:list', NULL, 2, 7, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (9, '用户新增', 'system:user:add', NULL, 2, 7, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (10, '用户编辑', 'system:user:edit', NULL, 2, 7, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (11, '用户删除', 'system:user:delete', NULL, 2, 7, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (12, '用户启停', 'system:user:switch', NULL, 2, 7, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (13, '用户授权', 'system:user:r2u', NULL, 2, 7, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (14, '用户导入', 'system:user:import', NULL, 2, 7, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (15, '用户导出', 'system:user:export', NULL, 2, 7, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (16, '用户分页列表', 'system:user:page', NULL, 2, 7, 9, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:24');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (17, '获取用户信息', 'user/info', NULL, 2, 7, 10, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:23');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (18, '角色管理', 'system:role', '/system/role', 1, 1, 2, 'IconUserCog', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (19, '角色查询', 'system:role:list', NULL, 2, 18, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (20, '角色新增', 'system:role:add', NULL, 2, 18, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (21, '角色编辑', 'system:role:edit', NULL, 2, 18, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (22, '角色删除', 'system:role:delete', NULL, 2, 18, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (23, '角色授权', 'system:role:p2r', NULL, 2, 18, 5, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (24, '角色导入', 'system:role:import', NULL, 2, 18, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (25, '角色导出', 'system:role:export', NULL, 2, 18, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (26, '角色分页列表', 'system:role:page', NULL, 2, 18, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:25');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (27, '权限管理', 'system:permission', '/system/permission', 1, 1, 3, 'IconLock', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (28, '权限查询', 'system:permission:list', NULL, 2, 27, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:28');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (29, '权限新增', 'system:permission:add', NULL, 2, 27, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (30, '权限编辑', 'system:permission:edit', NULL, 2, 27, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:03');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (31, '权限删除', 'system:permission:delete', NULL, 2, 27, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:04');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (32, '日志管理', 'system:log', '/system/log', 1, 1, 4, 'IconFileText', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (33, '日志查询', 'system:log:list', NULL, 2, 32, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (34, '日志删除', 'system:log:delete', NULL, 2, 32, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (35, '日志导出', 'system:log:export', NULL, 2, 32, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (36, '在线用户', 'system:online', '/system/online', 1, 1, 5, 'IconPlugConnected', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (37, '在线查询', 'system:online:list', NULL, 2, 36, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (38, '强制下线', 'system:online:kick', NULL, 2, 36, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (39, '文件管理', 'system:file', '/system/file', 1, 1, 6, 'IconFolder', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (40, '文件查询', 'system:file:list', NULL, 2, 39, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (41, '文件上传', 'system:file:upload', NULL, 2, 39, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (42, '文件删除', 'system:file:delete', NULL, 2, 39, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (43, '服务器管理', 'server', '/system/server', 1, 1, 7, 'IconServer', 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (44, '服务器查询', 'server:list', NULL, 2, 43, 1, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (45, '服务器新增', 'server:add', NULL, 2, 43, 2, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (46, '服务器编辑', 'server:edit', NULL, 2, 43, 3, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (47, '服务器删除', 'server:delete', NULL, 2, 43, 4, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (48, 'Web终端', 'server:terminal', NULL, 2, 43, 5, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (49, '文件浏览', 'server:file:list', NULL, 2, 43, 6, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (50, '文件上传', 'server:file:upload', NULL, 2, 43, 7, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (51, '文件下载', 'server:file:download', NULL, 2, 43, 8, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (52, '文件删除', 'server:file:delete', NULL, 2, 43, 9, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (2, '系统设置', 'setting', NULL, 1, 0, 2, 'IconTool', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (53, '参数管理', 'setting:config', '/setting/config', 1, 2, 1, 'IconAdjustments', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (54, '参数查询', 'setting:config:list', NULL, 2, 53, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (55, '参数新增', 'setting:config:add', NULL, 2, 53, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (56, '参数编辑', 'setting:config:edit', NULL, 2, 53, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (57, '参数删除', 'setting:config:delete', NULL, 2, 53, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (58, '参数导出', 'setting:config:export', NULL, 2, 53, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (59, '字典管理', 'setting:dict', '/setting/dict', 1, 2, 2, 'IconBook2', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (60, '字典类型查询', 'setting:dict:type:list', NULL, 2, 59, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (61, '字典类型新增', 'setting:dict:type:add', NULL, 2, 59, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (62, '字典类型编辑', 'setting:dict:type:edit', NULL, 2, 59, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (63, '字典类型删除', 'setting:dict:type:delete', NULL, 2, 59, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (64, '字典数据查询', 'setting:dict:data:list', NULL, 2, 59, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (65, '字典数据新增', 'setting:dict:data:add', NULL, 2, 59, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (66, '字典数据编辑', 'setting:dict:data:edit', NULL, 2, 59, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (67, '字典数据删除', 'setting:dict:data:delete', NULL, 2, 59, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (3, '测试管理', 'test:manage', NULL, 1, 0, 3, 'IconFlask', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (68, '组件测试', 'test:component', '/test/component', 1, 3, 1, 'IconTool', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (69, 'AOP测试', 'test:aop', '/test/aop', 1, 3, 2, 'IconChartHistogram', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (4, '媒体管理', 'media', NULL, 1, 0, 4, 'IconCpu', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (70, '节点管理', 'media:server', '/media/server', 1, 4, 1, 'IconServer2', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (71, '节点列表查询', 'media:server:list', NULL, 2, 70, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (72, '节点保存', 'media:server:save', NULL, 2, 70, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (73, '节点删除', 'media:server:delete', NULL, 2, 70, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (74, '拉流代理', 'media:stream-proxy', '/media/stream-proxy', 1, 4, 2, 'IconShare', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (75, '代理列表查询', 'media:stream-proxy:list', NULL, 2, 74, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (76, '代理新增', 'media:stream-proxy:add', NULL, 2, 74, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (77, '代理编辑', 'media:stream-proxy:edit', NULL, 2, 74, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (78, '代理删除', 'media:stream-proxy:delete', NULL, 2, 74, 4, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (79, '推流管理', 'media:stream-push', '/media/stream-push', 1, 4, 3, 'IconUpload', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (80, '推流列表查询', 'media:stream-push:list', NULL, 2, 79, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (81, '推流记录删除', 'media:stream-push:delete', NULL, 2, 79, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (82, '推流方查询', 'media:stream-push-client:list', NULL, 2, 79, 4, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (83, '推流方新增', 'media:stream-push-client:add', NULL, 2, 79, 5, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (84, '推流方编辑', 'media:stream-push-client:edit', NULL, 2, 79, 6, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (85, '推流方删除', 'media:stream-push-client:delete', NULL, 2, 79, 7, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (5, '国标管理', 'gb28181', NULL, 1, 0, 5, 'IconVideo', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (86, '设备管理', 'gb28181:device', '/gb28181/device', 1, 5, 2, 'IconDeviceCctv', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (87, '设备列表查询', 'gb28181:device:list', NULL, 2, 86, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (88, '设备目录同步', 'gb28181:device:sync', NULL, 2, 86, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (89, '设备删除', 'gb28181:device:delete', NULL, 2, 86, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (90, '设备新增', 'gb28181:device:add', NULL, 2, 86, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (91, '设备编辑', 'gb28181:device:edit', NULL, 2, 86, 5, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (92, '通道管理', 'gb28181:device-channel', '/gb28181/device-channel', 1, 5, 3, 'IconList', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (93, '通道列表查询', 'gb28181:device-channel:list', NULL, 2, 92, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (94, '通道新增', 'gb28181:device-channel:add', NULL, 2, 92, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (95, '通道编辑', 'gb28181:device-channel:edit', NULL, 2, 92, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (96, '通道删除', 'gb28181:device-channel:delete', NULL, 2, 92, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (97, '预置位查询', 'gb28181:ptz:preset', NULL, 2, 92, 5, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (98, '预置位新增', 'gb28181:ptz:preset:add', NULL, 2, 92, 6, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (99, '预置位编辑', 'gb28181:ptz:preset:edit', NULL, 2, 92, 7, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (100, '预置位删除', 'gb28181:ptz:preset:delete', NULL, 2, 92, 8, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (101, '级联平台', 'gb28181:platform', '/gb28181/platform', 1, 5, 6, 'IconShare2', 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (102, '级联平台查询', 'gb28181:platform:list', NULL, 2, 101, 1, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (103, '级联平台新增', 'gb28181:platform:add', NULL, 2, 101, 2, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (104, '级联平台编辑', 'gb28181:platform:edit', NULL, 2, 101, 3, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (105, '级联平台删除', 'gb28181:platform:delete', NULL, 2, 101, 4, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (106, '上级平台查询', 'gb28181:parent-platform:list', NULL, 2, 101, 5, NULL, 1, 0, '2026-07-28 00:00:00', '2026-07-28 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (107, '上级平台新增', 'gb28181:parent-platform:add', NULL, 2, 101, 6, NULL, 1, 0, '2026-07-28 00:00:00', '2026-07-28 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (108, '上级平台编辑', 'gb28181:parent-platform:edit', NULL, 2, 101, 7, NULL, 1, 0, '2026-07-28 00:00:00', '2026-07-28 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (109, '上级平台删除', 'gb28181:parent-platform:delete', NULL, 2, 101, 8, NULL, 1, 0, '2026-07-28 00:00:00', '2026-07-28 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (110, '推送分组查询', 'gb28181:share-group:list', NULL, 2, 101, 9, NULL, 1, 0, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (111, '推送分组新增', 'gb28181:share-group:add', NULL, 2, 101, 10, NULL, 1, 0, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (112, '推送分组编辑', 'gb28181:share-group:edit', NULL, 2, 101, 11, NULL, 1, 0, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (113, '推送分组删除', 'gb28181:share-group:delete', NULL, 2, 101, 12, NULL, 1, 0, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (6, 'ONVIF管理', 'onvif', NULL, 1, 0, 6, 'IconCamera', 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (114, '设备管理', 'onvif:device', '/onvif/device', 1, 6, 1, 'IconDeviceCctv', 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (115, '设备列表查询', 'onvif:device:list', NULL, 2, 114, 1, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (116, '设备新增', 'onvif:device:add', NULL, 2, 114, 2, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (117, '设备编辑', 'onvif:device:edit', NULL, 2, 114, 3, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (118, '设备删除', 'onvif:device:delete', NULL, 2, 114, 4, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (119, '设备探测', 'onvif:device:probe', NULL, 2, 114, 5, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (120, '云台控制', 'gb28181:ptz:control', NULL, 2, 92, 9, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (121, '巡航查询', 'gb28181:ptz:cruise', NULL, 2, 92, 10, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (122, '巡航新增', 'gb28181:ptz:cruise:add', NULL, 2, 92, 11, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (123, '巡航编辑', 'gb28181:ptz:cruise:edit', NULL, 2, 92, 12, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (124, '巡航删除', 'gb28181:ptz:cruise:delete', NULL, 2, 92, 13, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (125, '报警中心', 'gb28181:alarm', '/gb28181/alarm', 1, 5, 7, 'IconBell', 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (126, '报警查询', 'gb28181:alarm:list', NULL, 2, 125, 1, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (127, '报警处置', 'gb28181:alarm:handle', NULL, 2, 125, 2, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (128, '报警删除', 'gb28181:alarm:delete', NULL, 2, 125, 3, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (129, '移动轨迹', 'gb28181:position', '/gb28181/position', 1, 5, 8, 'IconRoute', 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (130, '轨迹查询', 'gb28181:position:list', NULL, 2, 129, 1, NULL, 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (131, '轨迹删除', 'gb28181:position:delete', NULL, 2, 129, 2, NULL, 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (132, '云端录像', 'gb28181:cloud-record', '/gb28181/cloud-record', 1, 5, 9, 'IconMovie', 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (133, '录像查询', 'gb28181:record:list', NULL, 2, 132, 1, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (134, '手动录制', 'gb28181:record:control', NULL, 2, 132, 2, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (135, '录像删除', 'gb28181:record:delete', NULL, 2, 132, 3, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (136, '录像计划', 'gb28181:record-plan', '/gb28181/record-plan', 1, 5, 10, 'IconCalendarTime', 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (137, '计划查询', 'gb28181:record:plan', NULL, 2, 136, 1, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (138, '计划新增', 'gb28181:record:plan:add', NULL, 2, 136, 2, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (139, '计划编辑', 'gb28181:record:plan:edit', NULL, 2, 136, 3, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (140, '计划删除', 'gb28181:record:plan:delete', NULL, 2, 136, 4, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (141, '设备控制', 'gb28181:device:control', NULL, 2, 92, 14, NULL, 1, 0, '2026-10-24 00:00:00', '2026-10-24 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (142, '远程重启', 'gb28181:device:reboot', NULL, 2, 92, 15, NULL, 1, 0, '2026-10-24 00:00:00', '2026-10-24 00:00:00');
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role
(
    id          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '角色编码',
    role_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
    description varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '角色描述',
    status      tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
    deleted     tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY         idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

INSERT INTO sys_role (id, role_code, role_name, description, status, deleted, create_time, update_time)
VALUES (1, 'ADMIN', '超级管理员', '拥有所有权限', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
       (2, 'USER', '普通用户', '基础用户角色', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission
(
    id            bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id       bigint NOT NULL COMMENT '角色ID',
    permission_id bigint NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY           idx_role_id (role_id),
    KEY           idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE deleted = 0;

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

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role
(
    id      bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id bigint NOT NULL COMMENT '用户ID',
    role_id bigint NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY     idx_user_id (user_id),
    KEY     idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1, 1, 1);

-- ============================================================
--  2/5  国标模块（设备 / 通道 / 云台 / 级联 / 录像 / 报警 / 流媒体）
-- ============================================================

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
    object_name   varchar(500)          DEFAULT NULL COMMENT 'S3对象名(gb28181/record/...mp4)',
    thumb_object_name varchar(500)        DEFAULT NULL COMMENT '缩略图S3对象名(截图抓拍，预留)',
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

DROP TABLE IF EXISTS gb_record_plan;
CREATE TABLE gb_record_plan
(
    id             bigint       NOT NULL COMMENT '主键ID',
    name           varchar(100)          DEFAULT NULL COMMENT '计划名称',
    device_id      varchar(50)  NOT NULL COMMENT '设备国标ID',
    channel_id     varchar(50)  NOT NULL COMMENT '通道国标ID',
    enabled        tinyint      NOT NULL DEFAULT 1 COMMENT '是否启用 0停 1启',
    transport      varchar(20)           DEFAULT NULL COMMENT '传输模式 UDP/TCP-active/TCP-passive，空随设备',
    stream_number  varchar(20)           DEFAULT NULL COMMENT '码流token（空=主码流）',
    retention_days int          NOT NULL DEFAULT 30 COMMENT '本计划录像保留天数，0=跟随全局',
    create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_device_channel (device_id, channel_id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='云端录像计划（周计划）';

DROP TABLE IF EXISTS gb_record_plan_period;
CREATE TABLE gb_record_plan_period
(
    id         bigint      NOT NULL COMMENT '主键ID',
    plan_id    bigint      NOT NULL COMMENT '所属录像计划ID',
    day_of_week tinyint    NOT NULL COMMENT '星期 1周一 ... 7周日',
    start_time varchar(8)  NOT NULL COMMENT '开始时间 HH:mm（支持跨午夜，如23:00-06:00）',
    end_time   varchar(8)  NOT NULL COMMENT '结束时间 HH:mm，跨午夜时早于开始时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_day_start (plan_id, day_of_week, start_time),
    KEY idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='录像计划周时段';

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

DELETE FROM sys_role_permission WHERE permission_id BETWEEN 143 AND 148;
DELETE FROM sys_permission          WHERE id BETWEEN 143 AND 148;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (143, '开放平台', 'openapi',        NULL,            1, 0,   10, 'IconLock', 1, 0, NOW(), NOW()),
 (144, '应用管理', 'openapi:app',    '/openapi/app',  1, 143, 1,  'IconLock', 1, 0, NOW(), NOW()),
 (145, '应用查询', 'openapi:app:list',   NULL, 2, 144, 1, NULL, 1, 0, NOW(), NOW()),
 (146, '应用新增', 'openapi:app:add',    NULL, 2, 144, 2, NULL, 1, 0, NOW(), NOW()),
 (147, '应用编辑', 'openapi:app:edit',   NULL, 2, 144, 3, NULL, 1, 0, NOW(), NOW()),
 (148, '应用换钥', 'openapi:app:rotate', NULL, 2, 144, 4, NULL, 1, 0, NOW(), NOW());

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 143 AND 148
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);

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

-- ============================================================
--  5/5  语音播报 / 对讲权限点（149~150，无新表）
-- ============================================================

DELETE FROM sys_role_permission WHERE permission_id BETWEEN 149 AND 150;
DELETE FROM sys_permission          WHERE id BETWEEN 149 AND 150;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (149, '语音播报/对讲', 'gb28181:broadcast',      '/gb28181/broadcast', 1, 5, 11, 'IconSpeakerphone', 1, 0, NOW(), NOW()),
 (150, '播报/对讲控制', 'gb28181:broadcast:talk', NULL,                 2, 149, 1, NULL,             1, 0, NOW(), NOW());

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 149 AND 150
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);

-- ============================================================
--  观看记录权限点（154~155，无新表）
-- ============================================================

DELETE FROM sys_role_permission WHERE permission_id BETWEEN 154 AND 155;
DELETE FROM sys_permission          WHERE id BETWEEN 154 AND 155;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (154, '观看记录', 'gb28181:view-record',      '/gb28181/view-record', 1, 5, 12, 'IconEye', 1, 0, NOW(), NOW()),
 (155, '观看查询', 'gb28181:view-record:list', NULL,                   2, 154, 1, NULL,      1, 0, NOW(), NOW());

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 154 AND 155
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);

SET FOREIGN_KEY_CHECKS = 1;
