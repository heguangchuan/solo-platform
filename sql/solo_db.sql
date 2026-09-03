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
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
VALUES
-- ========== 一级菜单 ==========
(1, '系统管理', 'system', NULL, 1, 0, 1, 'Setting', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(2, '系统设置', 'setting', NULL, 1, 0, 2, 'Tools', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(3, '测试管理', 'test:manage', '/test/manage', 1, 0, 3, 'Operation', 1, 0, '2026-06-16 09:00:00',
 '2026-06-16 09:00:00'),
(4, '任务调度', 'task', NULL, 1, 0, 4, 'Timer', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(6, '国标视频', 'gb28181', NULL, 1, 0, 6, 'VideoCamera', 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(7, 'ONVIF 视频', 'onvif', '/onvif', 1, 0, 7, 'Camera', 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),
(8, '媒体管理', 'media', '/media', 1, 0, 8, 'Cpu', 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),

-- ========== 系统管理 (id=1) 子菜单 ==========
(9, '用户管理', 'system:user', '/system/user', 1, 1, 1, 'User', 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(10, '角色管理', 'system:role', '/system/role', 1, 1, 2, 'UserFilled', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(11, '权限管理', 'system:permission', '/system/permission', 1, 1, 3, 'Lock', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(12, '日志管理', 'system:log', '/system/log', 1, 1, 4, 'Document', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(13, '在线用户', 'system:online', '/system/online', 1, 1, 5, 'Connection', 1, 0, '2026-06-16 09:00:00',
 '2026-06-16 09:00:00'),
(14, '文件管理', 'system:file', '/system/file', 1, 1, 6, 'Files', 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(15, '服务器管理', 'server', '/system/server', 1, 1, 7, 'Monitor', 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),

-- ----- 用户管理 (id=9) 按钮 -----
(16, '用户查询', 'system:user:list', NULL, 2, 9, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(17, '用户新增', 'system:user:add', NULL, 2, 9, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(18, '用户编辑', 'system:user:edit', NULL, 2, 9, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(19, '用户删除', 'system:user:delete', NULL, 2, 9, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(20, '用户启停', 'system:user:switch', NULL, 2, 9, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(21, '用户授权', 'system:user:r2u', NULL, 2, 9, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(22, '用户导入', 'system:user:import', NULL, 2, 9, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(23, '用户导出', 'system:user:export', NULL, 2, 9, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(24, '用户分页列表', 'system:user:page', '', 2, 9, 9, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:24'),
(25, '获取用户信息', 'user/info', '', 2, 9, 10, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:23'),

-- ----- 角色管理 (id=10) 按钮 -----
(26, '角色查询', 'system:role:list', NULL, 2, 10, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(27, '角色新增', 'system:role:add', NULL, 2, 10, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(28, '角色编辑', 'system:role:edit', NULL, 2, 10, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(29, '角色删除', 'system:role:delete', NULL, 2, 10, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(30, '角色授权', 'system:role:p2r', NULL, 2, 10, 5, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(31, '角色导入', 'system:role:import', NULL, 2, 10, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(32, '角色导出', 'system:role:export', NULL, 2, 10, 7, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(33, '角色分页列表', 'system:role:page', '', 2, 10, 8, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:25'),

-- ----- 权限管理 (id=11) 按钮 -----
(34, '权限查询', 'system:permission:list', '', 2, 11, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:28'),
(35, '权限新增', 'system:permission:add', '', 2, 11, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:02'),
(36, '权限编辑', 'system:permission:edit', '', 2, 11, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:03'),
(37, '权限删除', 'system:permission:delete', '', 2, 11, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-15 23:03:04'),

-- ----- 日志管理 (id=12) 按钮 -----
(38, '日志查询', 'system:log:list', NULL, 2, 12, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(39, '日志删除', 'system:log:delete', NULL, 2, 12, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(40, '日志导出', 'system:log:export', NULL, 2, 12, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

-- ----- 在线用户 (id=13) 按钮 -----
(41, '在线查询', 'system:online:list', NULL, 2, 13, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(42, '强制下线', 'system:online:kick', NULL, 2, 13, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

-- ----- 文件管理 (id=14) 按钮 -----
(43, '文件查询', 'system:file:list', NULL, 2, 14, 1, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(44, '文件上传', 'system:file:upload', NULL, 2, 14, 2, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(45, '文件删除', 'system:file:delete', NULL, 2, 14, 3, NULL, 1, 0, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

-- ----- 服务器管理 (id=15) 按钮 -----
(46, '服务器查询', 'server:list', NULL, 2, 15, 1, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(47, '服务器新增', 'server:add', NULL, 2, 15, 2, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(48, '服务器编辑', 'server:edit', NULL, 2, 15, 3, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(49, '服务器删除', 'server:delete', NULL, 2, 15, 4, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(50, 'Web终端', 'server:terminal', NULL, 2, 15, 5, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(51, '文件浏览', 'server:file:list', NULL, 2, 15, 6, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(52, '文件上传', 'server:file:upload', NULL, 2, 15, 7, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(53, '文件下载', 'server:file:download', NULL, 2, 15, 8, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
(54, '文件删除', 'server:file:delete', NULL, 2, 15, 9, NULL, 1, 0, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),

-- ========== 任务调度 (id=4) 子菜单 ==========
(55, '任务管理', 'task:manage', '/task/manage', 1, 4, 1, 'Timer', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(56, '执行日志', 'task:log', '/task/log', 1, 4, 2, 'Document', 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),

-- ----- 任务管理 (id=55) 按钮 -----
(57, '任务查询', 'task:manage:list', NULL, 2, 55, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(58, '任务新增', 'task:manage:add', NULL, 2, 55, 2, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(59, '任务编辑', 'task:manage:edit', NULL, 2, 55, 3, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(60, '任务删除', 'task:manage:delete', NULL, 2, 55, 4, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(61, '任务启停', 'task:manage:switch', NULL, 2, 55, 5, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),
(62, '任务执行', 'task:manage:run', NULL, 2, 55, 6, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),

-- ----- 执行日志 (id=56) 按钮 -----
(63, '任务日志查询', 'task:log:list', NULL, 2, 56, 1, NULL, 1, 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00'),

-- ========== 系统设置 (id=2) 子菜单 ==========
(64, '参数管理', 'setting:config', '/setting/config', 1, 2, 1, 'SetUp', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(65, '字典管理', 'setting:dict', '/setting/dict', 1, 2, 2, 'Collection', 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),

-- ----- 参数管理 (id=64) 按钮 -----
(66, '参数查询', 'setting:config:list', NULL, 2, 64, 1, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(67, '参数新增', 'setting:config:add', NULL, 2, 64, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(68, '参数编辑', 'setting:config:edit', NULL, 2, 64, 3, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(69, '参数删除', 'setting:config:delete', NULL, 2, 64, 4, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(70, '参数导出', 'setting:config:export', NULL, 2, 64, 5, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),

-- ----- 字典管理 (id=65) 按钮 -----
(71, '字典类型查询', 'setting:dict:type:list', NULL, 2, 65, 1, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(72, '字典类型新增', 'setting:dict:type:add', NULL, 2, 65, 2, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(73, '字典类型编辑', 'setting:dict:type:edit', NULL, 2, 65, 3, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(74, '字典类型删除', 'setting:dict:type:delete', NULL, 2, 65, 4, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(75, '字典数据查询', 'setting:dict:data:list', NULL, 2, 65, 5, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(76, '字典数据新增', 'setting:dict:data:add', NULL, 2, 65, 6, NULL, 1, 0, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(77, '字典数据编辑', 'setting:dict:data:edit', NULL, 2, 65, 7, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),
(78, '字典数据删除', 'setting:dict:data:delete', NULL, 2, 65, 8, NULL, 1, 0, '2026-06-14 22:35:02',
 '2026-06-14 22:35:02'),

-- ========== 国标视频 (id=6) 子菜单 ==========
-- 顺序：设备列表 → 通道列表(设备下，点播/回放/云台预置位均从通道行内入口进) → 级联平台 → 云台控制
(116, '设备管理', 'gb28181:device', '/gb28181/device', 1, 6, 2, 'Monitor', 1, 0, '2026-06-30 00:00:00',
 '2026-06-30 00:00:00'),
(117, '通道管理', 'gb28181:device-channel', '/gb28181/device-channel', 1, 6, 3, 'Connection', 1, 0,
 '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(121, '级联平台', 'gb28181:platform', '/gb28181/platform', 1, 6, 6, 'Share', 1, 0, '2026-07-27 00:00:00',
 '2026-07-27 00:00:00'),

-- ----- 设备列表 (id=116) 按钮 -----
(123, '设备列表查询', 'gb28181:device:list', NULL, 2, 116, 1, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(124, '设备目录同步', 'gb28181:device:sync', NULL, 2, 116, 2, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(125, '设备删除', 'gb28181:device:delete', NULL, 2, 116, 3, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(126, '设备新增', 'gb28181:device:add', NULL, 2, 116, 4, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),
(127, '设备编辑', 'gb28181:device:edit', NULL, 2, 116, 5, NULL, 1, 0, '2026-06-30 00:00:00', '2026-06-30 00:00:00'),

-- ----- 通道列表 (id=117) 按钮 -----
(128, '通道列表查询', 'gb28181:device-channel:list', NULL, 2, 117, 1, NULL, 1, 0, '2026-06-30 00:00:00',
 '2026-06-30 00:00:00'),
(129, '通道新增', 'gb28181:device-channel:add', NULL, 2, 117, 2, NULL, 1, 0, '2026-06-30 00:00:00',
 '2026-06-30 00:00:00'),
(130, '通道编辑', 'gb28181:device-channel:edit', NULL, 2, 117, 3, NULL, 1, 0, '2026-06-30 00:00:00',
 '2026-06-30 00:00:00'),
(131, '通道删除', 'gb28181:device-channel:delete', NULL, 2, 117, 4, NULL, 1, 0, '2026-06-30 00:00:00',
 '2026-06-30 00:00:00'),
-- 预置位：通道列表行内"预置位"入口，管理 gb_ptz_preset 业务数据（下发 SIP 是副作用）
(150, '预置位查询', 'gb28181:ptz:preset', NULL, 2, 117, 5, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00'),
(151, '预置位新增', 'gb28181:ptz:preset:add', NULL, 2, 117, 6, NULL, 1, 0, '2026-07-31 00:00:00', '2026-07-31 00:00:00'),
(152, '预置位编辑', 'gb28181:ptz:preset:edit', NULL, 2, 117, 7, NULL, 1, 0, '2026-07-31 00:00:00',
 '2026-07-31 00:00:00'),
(153, '预置位删除', 'gb28181:ptz:preset:delete', NULL, 2, 117, 8, NULL, 1, 0, '2026-07-31 00:00:00',
 '2026-07-31 00:00:00'),
-- 播放按钮：通道列表行内"播放"（PlayDialog）+ 云台预置位调用前的起流都走这两个权限码
-- 回放按钮：通道列表行内"回放"（PlaybackDialog：录像查询 + 播放 + 暂停/倍速/跳转）

-- ----- 级联平台 (id=121) 按钮（下级 + 上级 Tab 复用） -----
(142, '级联平台查询', 'gb28181:platform:list', NULL, 2, 121, 1, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00'),
(143, '级联平台新增', 'gb28181:platform:add', NULL, 2, 121, 2, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00'),
(144, '级联平台编辑', 'gb28181:platform:edit', NULL, 2, 121, 3, NULL, 1, 0, '2026-07-27 00:00:00', '2026-07-27 00:00:00'),
(145, '级联平台删除', 'gb28181:platform:delete', NULL, 2, 121, 4, NULL, 1, 0, '2026-07-27 00:00:00',
 '2026-07-27 00:00:00'),
(146, '上级平台查询', 'gb28181:parent-platform:list', NULL, 2, 121, 5, NULL, 1, 0, '2026-07-28 00:00:00',
 '2026-07-28 00:00:00'),
(147, '上级平台新增', 'gb28181:parent-platform:add', NULL, 2, 121, 6, NULL, 1, 0, '2026-07-28 00:00:00',
 '2026-07-28 00:00:00'),
(148, '上级平台编辑', 'gb28181:parent-platform:edit', NULL, 2, 121, 7, NULL, 1, 0, '2026-07-28 00:00:00',
 '2026-07-28 00:00:00'),
(149, '上级平台删除', 'gb28181:parent-platform:delete', NULL, 2, 121, 8, NULL, 1, 0, '2026-07-28 00:00:00',
 '2026-07-28 00:00:00'),
(154, '推送分组查询', 'gb28181:share-group:list', NULL, 2, 121, 9, NULL, 1, 0, '2026-08-14 00:00:00',
 '2026-08-14 00:00:00'),
(155, '推送分组新增', 'gb28181:share-group:add', NULL, 2, 121, 10, NULL, 1, 0, '2026-08-14 00:00:00',
 '2026-08-14 00:00:00'),
(183, '推送分组编辑', 'gb28181:share-group:edit', NULL, 2, 121, 11, NULL, 1, 0, '2026-08-14 00:00:00',
 '2026-08-14 00:00:00'),
(184, '推送分组删除', 'gb28181:share-group:delete', NULL, 2, 121, 12, NULL, 1, 0, '2026-08-14 00:00:00',
 '2026-08-14 00:00:00'),


-- ========== ONVIF 视频 (id=7) 子菜单 ==========
(156, '设备列表', 'onvif:device', '/onvif/device', 1, 7, 1, 'Monitor', 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),

-- ----- ONVIF 设备列表 (id=156) 按钮 -----
(158, '设备列表查询', 'onvif:device:list', NULL, 2, 156, 1, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),
(159, '设备新增', 'onvif:device:add', NULL, 2, 156, 2, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),
(160, '设备编辑', 'onvif:device:edit', NULL, 2, 156, 3, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),
(161, '设备删除', 'onvif:device:delete', NULL, 2, 156, 4, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),
(162, '设备探测', 'onvif:device:probe', NULL, 2, 156, 5, NULL, 1, 0, '2026-07-22 00:00:00', '2026-07-22 00:00:00'),

-- ========== 媒体管理 (id=8) 子菜单 ==========
(165, '节点管理', 'media:server', '/media/server', 1, 8, 1, 'Monitor', 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(166, '拉流代理', 'media:stream-proxy', '/media/stream-proxy', 1, 8, 2, 'Share', 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(167, '推流管理', 'media:stream-push', '/media/stream-push', 1, 8, 3, 'Upload', 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),

-- ----- 节点管理 (id=165) 按钮 -----
(168, '节点列表查询', 'media:server:list', NULL, 2, 165, 1, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),
(169, '节点保存', 'media:server:save', NULL, 2, 165, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),
(170, '节点删除', 'media:server:delete', NULL, 2, 165, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),

-- ----- 拉流代理 (id=166) 按钮 -----
(171, '代理列表查询', 'media:stream-proxy:list', NULL, 2, 166, 1, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(172, '代理新增', 'media:stream-proxy:add', NULL, 2, 166, 2, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),
(173, '代理编辑', 'media:stream-proxy:edit', NULL, 2, 166, 3, NULL, 1, 0, '2026-07-23 00:00:00', '2026-07-23 00:00:00'),
(174, '代理删除', 'media:stream-proxy:delete', NULL, 2, 166, 4, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),

-- ----- 推流管理 (id=167) 按钮 -----
(176, '推流列表查询', 'media:stream-push:list', NULL, 2, 167, 1, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(178, '推流记录删除', 'media:stream-push:delete', NULL, 2, 167, 3, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(179, '推流方查询', 'media:stream-push-client:list', NULL, 2, 167, 4, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(180, '推流方新增', 'media:stream-push-client:add', NULL, 2, 167, 5, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(181, '推流方编辑', 'media:stream-push-client:edit', NULL, 2, 167, 6, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00'),
(182, '推流方删除', 'media:stream-push-client:delete', NULL, 2, 167, 7, NULL, 1, 0, '2026-07-23 00:00:00',
 '2026-07-23 00:00:00');

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

-- 动态任务表
DROP TABLE IF EXISTS sys_task;
CREATE TABLE sys_task
(
    id           bigint                                                        NOT NULL COMMENT '任务ID',
    task_name    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
    task_code    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
    cron         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Cron表达式',
    handler_name varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务处理器名称',
    task_params  text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务参数',
    status       tinyint                                                       NOT NULL DEFAULT '0' COMMENT '状态: 0停用 1启用',
    remark       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '备注',
    deleted      tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_code (task_code),
    KEY          idx_status (status),
    KEY          idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态任务表';

-- 初始化任务数据
INSERT INTO sys_task (id, task_name, task_code, cron, handler_name, task_params, status, remark, deleted, create_time,
                      update_time)
VALUES (2067200000000000001, '示例动态任务', 'example.dynamic.task', '0/30 * * * * ?', 'exampleTask',
        '{"source":"init"}', 0, '启用后每30秒写入一条执行日志', 0, '2026-06-18 09:00:00', '2026-06-18 09:00:00');

-- 动态任务执行日志表
DROP TABLE IF EXISTS sys_task_log;
CREATE TABLE sys_task_log
(
    id             bigint                                                        NOT NULL COMMENT '日志ID',
    task_id        bigint                                                        NOT NULL COMMENT '任务ID',
    task_name      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
    task_code      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
    handler_name   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务处理器名称',
    task_params    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务参数',
    execution_time datetime                                                      NOT NULL COMMENT '执行时间',
    duration_ms    bigint                                                         DEFAULT NULL COMMENT '执行耗时(毫秒)',
    result_status  tinyint                                                       NOT NULL COMMENT '执行结果: 0失败 1成功',
    error_msg      varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
    PRIMARY KEY (id),
    KEY            idx_task_id (task_id),
    KEY            idx_task_code (task_code),
    KEY            idx_execution_time (execution_time),
    KEY            idx_result_status (result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态任务执行日志表';

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
