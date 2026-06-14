-- 创建数据库
DROP DATABASE IF EXISTS solo_db;
CREATE DATABASE solo_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE solo_db;

-- 用户表
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT 'MD5加密密码',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    avatar VARCHAR(255) COMMENT '头像URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by BIGINT COMMENT '创建人',
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 权限表
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    url VARCHAR(255) COMMENT '关联URL',
    type TINYINT NOT NULL COMMENT '类型: 1菜单 2按钮 3接口',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    icon VARCHAR(50) COMMENT '图标',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 插入初始数据
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '超级管理员', '拥有所有权限'),
('USER', '普通用户', '基础用户角色');

-- 获取刚插入的角色ID（1=ADMIN, 2=USER）
SET @admin_role_id = LAST_INSERT_ID();
-- 上面的INSERT插入了两行，LAST_INSERT_ID返回第一行的id，即1

-- 插入初始权限
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('系统管理', 'system', NULL, 1, 0, 1, 'el-icon-setting'),
('用户管理', 'system:user', '/system/user', 1, 1, 1, 'el-icon-user'),
('角色管理', 'system:role', '/system/role', 1, 1, 2, 'el-icon-user-solid'),
('权限管理', 'system:permission', '/system/permission', 1, 1, 3, 'el-icon-lock');

-- 用户管理按钮权限
INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('用户查询', 'system:user:list', 2, 2, 1),
('用户新增', 'system:user:add', 2, 2, 2),
('用户编辑', 'system:user:edit', 2, 2, 3),
('用户删除', 'system:user:delete', 2, 2, 4),
('用户启停', 'system:user:switch', 2, 2, 5);

-- 角色管理按钮权限
INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('角色查询', 'system:role:list', 2, 3, 1),
('角色新增', 'system:role:add', 2, 3, 2),
('角色编辑', 'system:role:edit', 2, 3, 3),
('角色删除', 'system:role:delete', 2, 3, 4);

-- 接口权限
INSERT INTO sys_permission (permission_name, permission_code, url, type) VALUES
('登录接口', 'login', '/api/auth/login', 3),
('获取用户信息', 'user/info', '/api/user/info', 3),
('用户分页列表', 'system:user:page', '/api/system/user/page', 3),
('角色分页列表', 'system:role:page', '/api/system/role/page', 3),
('权限列表', 'system:permission:list', '/api/system/permission/list', 3);

-- 获取权限ID
SET @menu_perm_id = 1;  -- 系统管理
SET @user_perm_id = 2;  -- 用户管理
SET @role_perm_id = 3;  -- 角色管理
SET @perm_manage_id = 4; -- 权限管理

-- 插入管理员用户（密码: admin123，MD5加密）
INSERT INTO sys_user (username, password, nickname, status) VALUES
('admin', '0192023a7bbd73250516f069df18b500', '管理员', 1);

-- 关联管理员为超级管理员
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 超级管理员拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 普通用户拥有登录和信息查询权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE permission_code IN ('login', 'user/info');
