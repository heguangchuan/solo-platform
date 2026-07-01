-- =========================================================
-- Solo 平台 - 服务器管理模块 SQL
-- 创建日期：2026-07-01
-- 说明：本文件为服务器管理功能增量 SQL，需先执行 sql/solo_db.sql。
-- 范围：sys_server 表、服务器管理菜单权限、超级管理员角色授权。
-- =========================================================

USE solo_db;

SET FOREIGN_KEY_CHECKS = 0;

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
    server_group       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '服务器分组',
    os_type            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '操作系统类型',
    status             tinyint                                                       NOT NULL DEFAULT '1' COMMENT '状态: 0停用 1启用',
    owner              varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '负责人',
    description        varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci          DEFAULT NULL COMMENT '描述',
    deleted            tinyint                                                       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
    create_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        datetime                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_server_name (server_name),
    KEY                idx_host (host),
    KEY                idx_status (status),
    KEY                idx_server_group (server_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器管理表';

-- 服务器管理菜单和按钮权限
INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 125, '服务器管理', 'system:server', '/system/server', 1, 1, 7, 'Monitor', 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 126, '服务器查询', 'system:server:list', NULL, 2, 125, 1, NULL, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server:list');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 127, '服务器新增', 'system:server:add', NULL, 2, 125, 2, NULL, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server:add');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 128, '服务器编辑', 'system:server:edit', NULL, 2, 125, 3, NULL, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server:edit');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 129, '服务器删除', 'system:server:delete', NULL, 2, 125, 4, NULL, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server:delete');

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted,
                            create_time, update_time)
SELECT 130, 'Web终端', 'system:server:terminal', NULL, 2, 125, 5, NULL, 1, 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system:server:terminal');

-- 超级管理员角色授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.permission_code IN (
    'system:server',
    'system:server:list',
    'system:server:add',
    'system:server:edit',
    'system:server:delete',
    'system:server:terminal'
)
  AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_permission rp
    WHERE rp.role_id = 1
      AND rp.permission_id = p.id
  );

SET FOREIGN_KEY_CHECKS = 1;
