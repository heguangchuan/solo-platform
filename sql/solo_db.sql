-- =====================================================
-- Solo Platform Database Initialization Script
-- Database: solo_db
-- Date: 2026-06-19
-- =====================================================

-- 创建数据库（如果不存在）
CREATE
DATABASE IF NOT EXISTS solo_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE
solo_db;

SET
FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 系统模块表
-- =====================================================

-- 系统参数表
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

-- 字典数据表
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

-- 初始化字典数据
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, deleted, create_time, update_time)
VALUES (2066820557811949570, 'sys_file_types', 'mp4', '1', 2, 1, 0, '2026-06-16 17:49:55', '2026-06-16 17:49:55'),
       (2066820617408815105, 'sys_file_types', 'png', '2', 1, 1, 0, '2026-06-16 17:49:10', '2026-06-16 17:50:10');

-- 字典类型表
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

-- 文件管理表
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

-- 服务器管理表
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

-- 操作日志表
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

-- 系统消息表
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

-- 系统权限表
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

-- 初始化权限数据
-- 组织顺序：一级菜单 → 二级菜单 → 按钮；同一父级下按 sort 升序；
-- ID 从 1 开始连续递增，parent_id 引用同一脚本内已定义好的行。
-- 初始化权限数据
-- 组织顺序：一级菜单 → 二级菜单 → 按钮；同一父级下按 sort 升序；
-- ID 从 1 开始连续递增，parent_id 引用同一脚本内已定义好的行。

-- ========== 一级菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (1, '系统管理', 'system', NULL, 1, 0, 1, 'IconSettings', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- ========== 系统管理 (id=1) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (7, '用户管理', 'system:user', '/system/user', 1, 1, 1, 'IconUsers', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- ----- 用户管理 (id=7) 按钮 -----
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

-- ----- 角色管理 (id=18) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (19, '角色查询', 'system:role:list', NULL, 2, 18, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (20, '角色新增', 'system:role:add', NULL, 2, 18, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (21, '角色编辑', 'system:role:edit', NULL, 2, 18, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (22, '角色删除', 'system:role:delete', NULL, 2, 18, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (23, '角色授权', 'system:role:p2r', NULL, 2, 18, 5, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (24, '角色导入', 'system:role:import', NULL, 2, 18, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (25, '角色导出', 'system:role:export', NULL, 2, 18, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (26, '角色分页列表', 'system:role:page', NULL, 2, 18, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:25');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (27, '权限管理', 'system:permission', '/system/permission', 1, 1, 3, 'IconLock', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- ----- 权限管理 (id=27) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (28, '权限查询', 'system:permission:list', NULL, 2, 27, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:28');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (29, '权限新增', 'system:permission:add', NULL, 2, 27, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (30, '权限编辑', 'system:permission:edit', NULL, 2, 27, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:03');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (31, '权限删除', 'system:permission:delete', NULL, 2, 27, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:04');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (32, '日志管理', 'system:log', '/system/log', 1, 1, 4, 'IconFileText', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

-- ----- 日志管理 (id=32) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (33, '日志查询', 'system:log:list', NULL, 2, 32, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (34, '日志删除', 'system:log:delete', NULL, 2, 32, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (35, '日志导出', 'system:log:export', NULL, 2, 32, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (36, '在线用户', 'system:online', '/system/online', 1, 1, 5, 'IconPlugConnected', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

-- ----- 在线用户 (id=36) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (37, '在线查询', 'system:online:list', NULL, 2, 36, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (38, '强制下线', 'system:online:kick', NULL, 2, 36, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (39, '文件管理', 'system:file', '/system/file', 1, 1, 6, 'IconFolder', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

-- ----- 文件管理 (id=39) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (40, '文件查询', 'system:file:list', NULL, 2, 39, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (41, '文件上传', 'system:file:upload', NULL, 2, 39, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (42, '文件删除', 'system:file:delete', NULL, 2, 39, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (43, '服务器管理', 'server', '/system/server', 1, 1, 7, 'IconServer', 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- ----- 服务器管理 (id=43) 按钮 -----
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

-- ========== 系统设置 (id=2) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (53, '参数管理', 'setting:config', '/setting/config', 1, 2, 1, 'IconAdjustments', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- ----- 参数管理 (id=53) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (54, '参数查询', 'setting:config:list', NULL, 2, 53, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (55, '参数新增', 'setting:config:add', NULL, 2, 53, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (56, '参数编辑', 'setting:config:edit', NULL, 2, 53, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (57, '参数删除', 'setting:config:delete', NULL, 2, 53, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (58, '参数导出', 'setting:config:export', NULL, 2, 53, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (59, '字典管理', 'setting:dict', '/setting/dict', 1, 2, 2, 'IconBook2', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- ----- 字典管理 (id=59) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (60, '字典类型查询', 'setting:dict:type:list', NULL, 2, 59, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (61, '字典类型新增', 'setting:dict:type:add', NULL, 2, 59, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (62, '字典类型编辑', 'setting:dict:type:edit', NULL, 2, 59, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (63, '字典类型删除', 'setting:dict:type:delete', NULL, 2, 59, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (64, '字典数据查询', 'setting:dict:data:list', NULL, 2, 59, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (65, '字典数据新增', 'setting:dict:data:add', NULL, 2, 59, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (66, '字典数据编辑', 'setting:dict:data:edit', NULL, 2, 59, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (67, '字典数据删除', 'setting:dict:data:delete', NULL, 2, 59, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (3, '测试管理', 'test:manage', NULL, 1, 0, 3, 'IconFlask', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');

-- ========== 测试管理 (id=3) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (68, '组件测试', 'test:component', '/test/component', 1, 3, 1, 'IconTool', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (69, 'AOP测试', 'test:aop', '/test/aop', 1, 3, 2, 'IconChartHistogram', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (4, '媒体管理', 'media', NULL, 1, 0, 4, 'IconCpu', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

-- ========== 媒体管理 (id=4) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (70, '节点管理', 'media:server', '/media/server', 1, 4, 1, 'IconServer2', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

-- ----- 节点管理 (id=70) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (71, '节点列表查询', 'media:server:list', NULL, 2, 70, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (72, '节点保存', 'media:server:save', NULL, 2, 70, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (73, '节点删除', 'media:server:delete', NULL, 2, 70, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (74, '拉流代理', 'media:stream-proxy', '/media/stream-proxy', 1, 4, 2, 'IconShare', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

-- ----- 拉流代理 (id=74) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (75, '代理列表查询', 'media:stream-proxy:list', NULL, 2, 74, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (76, '代理新增', 'media:stream-proxy:add', NULL, 2, 74, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (77, '代理编辑', 'media:stream-proxy:edit', NULL, 2, 74, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (78, '代理删除', 'media:stream-proxy:delete', NULL, 2, 74, 4, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (79, '推流管理', 'media:stream-push', '/media/stream-push', 1, 4, 3, 'IconUpload', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');

-- ----- 推流管理 (id=79) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (80, '推流列表查询', 'media:stream-push:list', NULL, 2, 79, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (81, '推流记录删除', 'media:stream-push:delete', NULL, 2, 79, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (82, '推流方查询', 'media:stream-push-client:list', NULL, 2, 79, 4, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (83, '推流方新增', 'media:stream-push-client:add', NULL, 2, 79, 5, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (84, '推流方编辑', 'media:stream-push-client:edit', NULL, 2, 79, 6, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (85, '推流方删除', 'media:stream-push-client:delete', NULL, 2, 79, 7, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (5, '国标管理', 'gb28181', NULL, 1, 0, 5, 'IconVideo', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

-- ========== 国标管理 (id=5) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (86, '设备管理', 'gb28181:device', '/gb28181/device', 1, 5, 2, 'IconDeviceCctv', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

-- ----- 设备管理 (id=86) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (87, '设备列表查询', 'gb28181:device:list', NULL, 2, 86, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (88, '设备目录同步', 'gb28181:device:sync', NULL, 2, 86, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (89, '设备删除', 'gb28181:device:delete', NULL, 2, 86, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (90, '设备新增', 'gb28181:device:add', NULL, 2, 86, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (91, '设备编辑', 'gb28181:device:edit', NULL, 2, 86, 5, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (92, '通道管理', 'gb28181:device-channel', '/gb28181/device-channel', 1, 5, 3, 'IconList', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');

-- ----- 通道管理 (id=92) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (93, '通道列表查询', 'gb28181:device-channel:list', NULL, 2, 92, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (94, '通道新增', 'gb28181:device-channel:add', NULL, 2, 92, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (95, '通道编辑', 'gb28181:device-channel:edit', NULL, 2, 92, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (96, '通道删除', 'gb28181:device-channel:delete', NULL, 2, 92, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (97, '预置位查询', 'gb28181:ptz:preset', NULL, 2, 92, 5, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (98, '预置位新增', 'gb28181:ptz:preset:add', NULL, 2, 92, 6, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (99, '预置位编辑', 'gb28181:ptz:preset:edit', NULL, 2, 92, 7, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (100, '预置位删除', 'gb28181:ptz:preset:delete', NULL, 2, 92, 8, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (101, '级联平台', 'gb28181:platform', '/gb28181/platform', 1, 5, 6, 'IconShare2', 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00');

-- ----- 级联平台 (id=101) 按钮 -----
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

-- ========== ONVIF管理 (id=6) 子菜单 ==========
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (114, '设备管理', 'onvif:device', '/onvif/device', 1, 6, 1, 'IconDeviceCctv', 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');

-- ----- 设备管理 (id=114) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (115, '设备列表查询', 'onvif:device:list', NULL, 2, 114, 1, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (116, '设备新增', 'onvif:device:add', NULL, 2, 114, 2, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (117, '设备编辑', 'onvif:device:edit', NULL, 2, 114, 3, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (118, '设备删除', 'onvif:device:delete', NULL, 2, 114, 4, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (119, '设备探测', 'onvif:device:probe', NULL, 2, 114, 5, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
-- PTZ 云台控制权限（挂在通道管理 92 下）
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (120, '云台控制', 'gb28181:ptz:control', NULL, 2, 92, 9, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (121, '巡航查询', 'gb28181:ptz:cruise', NULL, 2, 92, 10, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (122, '巡航新增', 'gb28181:ptz:cruise:add', NULL, 2, 92, 11, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (123, '巡航编辑', 'gb28181:ptz:cruise:edit', NULL, 2, 92, 12, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (124, '巡航删除', 'gb28181:ptz:cruise:delete', NULL, 2, 92, 13, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (125, '报警中心', 'gb28181:alarm', '/gb28181/alarm', 1, 5, 7, 'IconBell', 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');

-- ----- 报警中心 (id=125) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (126, '报警查询', 'gb28181:alarm:list', NULL, 2, 125, 1, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (127, '报警处置', 'gb28181:alarm:handle', NULL, 2, 125, 2, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (128, '报警删除', 'gb28181:alarm:delete', NULL, 2, 125, 3, NULL, 1, 0, '2026-09-11 00:00:00', '2026-09-11 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (129, '移动轨迹', 'gb28181:position', '/gb28181/position', 1, 5, 8, 'IconRoute', 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');

-- ----- 移动轨迹 (id=129) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (130, '轨迹查询', 'gb28181:position:list', NULL, 2, 129, 1, NULL, 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (131, '轨迹删除', 'gb28181:position:delete', NULL, 2, 129, 2, NULL, 1, 0, '2026-10-19 00:00:00', '2026-10-19 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (132, '云端录像', 'gb28181:cloud-record', '/gb28181/cloud-record', 1, 5, 9, 'IconMovie', 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');

-- ----- 云端录像 (id=132) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (133, '录像查询', 'gb28181:record:list', NULL, 2, 132, 1, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (134, '手动录制', 'gb28181:record:control', NULL, 2, 132, 2, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (135, '录像删除', 'gb28181:record:delete', NULL, 2, 132, 3, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (136, '录像计划', 'gb28181:record-plan', '/gb28181/record-plan', 1, 5, 10, 'IconCalendarTime', 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');

-- ----- 录像计划 (id=136) 按钮 -----
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (137, '计划查询', 'gb28181:record:plan', NULL, 2, 136, 1, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (138, '计划新增', 'gb28181:record:plan:add', NULL, 2, 136, 2, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (139, '计划编辑', 'gb28181:record:plan:edit', NULL, 2, 136, 3, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (140, '计划删除', 'gb28181:record:plan:delete', NULL, 2, 136, 4, NULL, 1, 0, '2026-10-22 00:00:00', '2026-10-22 00:00:00');
-- 设备控制（布防撤防/报警复位/本地录像/强制关键帧）和远程重启，挂通道管理 92 下
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (141, '设备控制', 'gb28181:device:control', NULL, 2, 92, 14, NULL, 1, 0, '2026-10-24 00:00:00', '2026-10-24 00:00:00');
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES (142, '远程重启', 'gb28181:device:reboot', NULL, 2, 92, 15, NULL, 1, 0, '2026-10-24 00:00:00', '2026-10-24 00:00:00');
-- 系统角色表
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

-- 初始化角色数据
INSERT INTO sys_role (id, role_code, role_name, description, status, deleted, create_time, update_time)
VALUES (1, 'ADMIN', '超级管理员', '拥有所有权限', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
       (2, 'USER', '普通用户', '基础用户角色', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02');

-- 角色权限关联表
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

-- 初始化超级管理员权限（使用 INSERT SELECT 方式，将所有权限分配给超级管理员角色）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE deleted = 0;

-- 系统用户表
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

-- 初始化用户数据（默认密码：admin123，MD5加密：0192023a7bbd73250516f069df18b500）
INSERT INTO sys_user (id, username, password, nickname, email, phone, avatar, status, pwd_reset_required, deleted,
                      create_time, update_time)
VALUES (1, 'admin', '0192023a7bbd73250516f069df18b500', '管理员', NULL, NULL, NULL, 1, 0, 0, '2026-06-14 22:35:02',
        '2026-06-14 22:35:02');

-- 用户角色关联表
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

-- 初始化用户角色关联
INSERT INTO sys_user_role (id, user_id, role_id)
VALUES (1, 1, 1);

SET
FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 注：GB/T 28181 国标模块的所有表（gb_*）已拆分到 sql/gb28181.sql
-- 启用国标功能时再单独执行该脚本，按模块解耦。
-- =====================================================
