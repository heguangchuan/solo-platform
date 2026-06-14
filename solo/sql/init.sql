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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='系统用户表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='系统角色表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='系统权限表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 角色权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================================
-- 初始角色
-- ============================================================
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN', '超级管理员', '拥有所有权限'),
('USER', '普通用户', '基础用户角色');

-- ============================================================
-- 初始权限数据
-- type: 1=菜单 2=按钮 3=接口
-- ============================================================

-- ========== 仪表盘 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('仪表盘', 'dashboard', '/', 1, 0, 0, '\U0001F4CA');

-- ========== 系统管理 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('系统管理', 'system', NULL, 1, 0, 1, '\U0001F6E0');

-- 系统管理 → 用户管理
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('用户管理', 'system:user', '/system/user', 1, 1, 1, '\U0001F465');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('用户查询', 'system:user:list', 2, 2, 1),
('用户新增', 'system:user:add', 2, 2, 2),
('用户编辑', 'system:user:edit', 2, 2, 3),
('用户删除', 'system:user:delete', 2, 2, 4),
('用户导出', 'system:user:export', 2, 2, 5);

-- 系统管理 → 角色管理
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('角色管理', 'system:role', '/system/role', 1, 1, 2, '\U0001F465');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('角色查询', 'system:role:list', 2, 3, 1),
('角色新增', 'system:role:add', 2, 3, 2),
('角色编辑', 'system:role:edit', 2, 3, 3),
('角色删除', 'system:role:delete', 2, 3, 4),
('角色授权', 'system:role:assign', 2, 3, 5);

-- 系统管理 → 权限管理
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('权限管理', 'system:permission', '/system/permission', 1, 1, 3, '\U0001F512');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('权限查询', 'system:permission:list', 2, 4, 1),
('权限新增', 'system:permission:add', 2, 4, 2),
('权限编辑', 'system:permission:edit', 2, 4, 3),
('权限删除', 'system:permission:delete', 2, 4, 4);

-- ========== 字典管理 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('字典管理', 'dict', NULL, 1, 0, 2, '\U0001F4C2');

-- 字典管理 → 字典类型
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('字典类型', 'dict:type', '/dict/type', 1, 5, 1, '\U0001F4CB');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('字典类型查询', 'dict:type:list', 2, 5, 1),
('字典类型新增', 'dict:type:add', 2, 5, 2),
('字典类型编辑', 'dict:type:edit', 2, 5, 3),
('字典类型删除', 'dict:type:delete', 2, 5, 4);

-- 字典管理 → 字典数据
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('字典数据', 'dict:data', '/dict/data', 1, 5, 2, '\U0001F4C3');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('字典数据查询', 'dict:data:list', 2, 6, 1),
('字典数据新增', 'dict:data:add', 2, 6, 2),
('字典数据编辑', 'dict:data:edit', 2, 6, 3),
('字典数据删除', 'dict:data:delete', 2, 6, 4);

-- ========== 任务管理 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('任务管理', 'job', NULL, 1, 0, 3, '\U2699');

-- 任务管理 → 定时任务
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('定时任务', 'job:cron', '/job/cron', 1, 7, 1, '\U0001F570');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('任务查询', 'job:cron:list', 2, 7, 1),
('任务新增', 'job:cron:add', 2, 7, 2),
('任务编辑', 'job:cron:edit', 2, 7, 3),
('任务删除', 'job:cron:delete', 2, 7, 4),
('任务执行', 'job:cron:run', 2, 7, 5);

-- 任务管理 → 任务日志
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('执行日志', 'job:log', '/job/log', 1, 7, 2, '\U0001F4DD');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('日志查询', 'job:log:list', 2, 8, 1),
('日志清除', 'job:log:clear', 2, 8, 2);

-- ========== 系统监控 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('系统监控', 'monitor', NULL, 1, 0, 4, '\U0001F4E0');

-- 系统监控 → 在线用户
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('在线用户', 'monitor:online', '/monitor/online', 1, 9, 1, '\U0001F464');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('在线用户查询', 'monitor:online:list', 2, 9, 1),
('强退用户', 'monitor:online:kick', 2, 9, 2);

-- 系统监控 → 操作日志
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('操作日志', 'monitor:log', '/monitor/log', 1, 9, 2, '\U0001F4CB');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('日志查询', 'monitor:log:list', 2, 10, 1),
('日志清除', 'monitor:log:clear', 2, 10, 2);

-- 系统监控 → 服务监控
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('服务监控', 'monitor:server', '/monitor/server', 1, 9, 3, '\U0001F5A5');

-- ========== 个人中心 ==========
INSERT INTO sys_permission (permission_name, permission_code, url, type, parent_id, sort, icon) VALUES
('个人中心', 'profile', '/profile', 1, 0, 10, '\U0001F464');

INSERT INTO sys_permission (permission_name, permission_code, type, parent_id, sort) VALUES
('信息修改', 'profile:edit', 2, 11, 1),
('密码修改', 'profile:password', 2, 11, 2),
('头像上传', 'profile:avatar', 2, 11, 3);

-- ============================================================
-- 超级管理员拥有所有权限
-- ============================================================
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 普通用户仅有仪表盘和部分系统管理权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission
WHERE permission_code IN (
    'dashboard', 'system:user', 'system:user:list', 'system:user:add', 'system:user:edit',
    'system:role', 'system:role:list', 'system:role:add', 'system:role:edit',
    'system:permission', 'system:permission:list'
);

-- ============================================================
-- 插入管理员用户（密码: admin123，MD5加密）
-- ============================================================
INSERT INTO sys_user (username, password, nickname, status) VALUES
('admin', '0192023a7bbd73250516f069df18b500', '管理员', 1);

-- 关联管理员为超级管理员
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
