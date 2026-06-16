/*
 * Solo 权限管理平台 — 数据库初始化脚本
 * MySQL 8.0+
 *
 * 包含：
 *   - 数据库 solo_db 的创建
 *   - 系统用户 / 角色 / 权限 / 角色-权限 / 用户-角色 / 操作日志 / 系统参数 / 字典 等表
 *   - admin 超级管理员账号（密码 admin123）及其全量权限分配
 *
 * 直接执行本文件即可完成全新部署。
 */

/*!40101 SET NAMES utf8mb4 */;
/*!40101 SET SQL_MODE='' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `solo_db`
    /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */
    /*!80016 DEFAULT ENCRYPTION='N' */;

USE `solo_db`;


/* =====================================================================
 * 1. sys_user — 系统用户表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`           varchar(50)   COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password`           varchar(100)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MD5加密密码',
  `nickname`           varchar(50)   COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `email`              varchar(100)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone`              varchar(20)   COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `avatar`             varchar(255)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `status`             tinyint       NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `pwd_reset_required` tinyint       NOT NULL DEFAULT '0' COMMENT '是否首次登录强制改密: 0否 1是',
  `deleted`            tinyint       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

INSERT INTO `sys_user` (`id`,`username`,`password`,`nickname`,`email`,`phone`,`avatar`,`status`,`pwd_reset_required`,`create_time`,`update_time`) VALUES
(1,'admin','0192023a7bbd73250516f069df18b500','管理员',NULL,NULL,NULL,1,0,'2026-06-14 22:35:02','2026-06-14 22:35:02');


/* =====================================================================
 * 2. sys_role — 系统角色表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code`   varchar(50)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `status`      tinyint      NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `deleted`     tinyint      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

INSERT INTO `sys_role` (`id`,`role_code`,`role_name`,`description`,`status`,`create_time`,`update_time`) VALUES
(1,'ADMIN','超级管理员','拥有所有权限',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(2,'USER','普通用户','基础用户角色',1,'2026-06-14 22:35:02','2026-06-14 22:35:02');


/* =====================================================================
 * 3. sys_permission — 系统权限表
 *    type: 1=菜单 2=按钮
 *    icon: @element-plus/icons-vue 组件名（PascalCase）
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `url`             varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联URL',
  `type`            tinyint      NOT NULL COMMENT '类型: 1菜单 2按钮',
  `parent_id`       bigint       NOT NULL DEFAULT '0' COMMENT '父级ID',
  `sort`            int          NOT NULL DEFAULT '0' COMMENT '排序',
  `icon`            varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `status`          tinyint      NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `deleted`         tinyint      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

INSERT INTO `sys_permission` (`id`,`permission_name`,`permission_code`,`url`,`type`,`parent_id`,`sort`,`icon`,`status`,`create_time`,`update_time`) VALUES
/* —— 一级菜单 —— */
(1, '系统管理', 'system',                NULL,                    1, 0,  1, 'Setting',    1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(27,'系统设置', 'setting',               NULL,                    1, 0,  2, 'Tools',      1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),

/* —— 系统管理 → 子菜单 —— */
(2, '用户管理', 'system:user',           '/system/user',          1, 1,  1, 'User',       1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(3, '角色管理', 'system:role',           '/system/role',          1, 1,  2, 'UserFilled', 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(4, '权限管理', 'system:permission',     '/system/permission',    1, 1,  3, 'Lock',       1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(43,'日志管理', 'system:log',            '/system/log',           1, 1,  4, 'Document',   1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(48,'在线用户', 'system:online',         '/system/online',        1, 1,  5, 'Connection', 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(51,'文件管理', 'system:file',           '/system/file',          1, 1,  6, 'Files',      1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

/* —— 用户管理 → 按钮 —— */
(5, '用户查询',     'system:user:list',   NULL, 2, 2,  1, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(6, '用户新增',     'system:user:add',    NULL, 2, 2,  2, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(7, '用户编辑',     'system:user:edit',   NULL, 2, 2,  3, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(8, '用户删除',     'system:user:delete', NULL, 2, 2,  4, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(9, '用户启停',     'system:user:switch', NULL, 2, 2,  5, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(23,'用户授权',     'system:user:r2u',    NULL, 2, 2,  6, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(19,'用户导入',     'system:user:import', NULL, 2, 2,  7, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(20,'用户导出',     'system:user:export', NULL, 2, 2,  8, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(16,'用户分页列表', 'system:user:page',   '',   2, 2,  9, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:24'),
(15,'获取用户信息', 'user/info',          '',   2, 2, 10, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:23'),

/* —— 角色管理 → 按钮 —— */
(10,'角色查询',     'system:role:list',   NULL, 2, 3,  1, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(11,'角色新增',     'system:role:add',    NULL, 2, 3,  2, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(12,'角色编辑',     'system:role:edit',   NULL, 2, 3,  3, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(13,'角色删除',     'system:role:delete', NULL, 2, 3,  4, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(47,'角色授权',     'system:role:p2r',    NULL, 2, 3,  5, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(21,'角色导入',     'system:role:import', NULL, 2, 3,  6, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(22,'角色导出',     'system:role:export', NULL, 2, 3,  7, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(17,'角色分页列表', 'system:role:page',   '',   2, 3,  8, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:25'),

/* —— 权限管理 → 按钮 —— */
(18,'权限查询', 'system:permission:list',   '', 2, 4, 1, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:28'),
(24,'权限新增', 'system:permission:add',    '', 2, 4, 2, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:02'),
(25,'权限编辑', 'system:permission:update', '', 2, 4, 3, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:03'),
(26,'权限删除', 'system:permission:delete', '', 2, 4, 4, NULL, 1, '2026-06-14 22:35:02', '2026-06-15 23:03:04'),

/* —— 日志管理 → 按钮 —— */
(44,'日志查询', 'system:log:list',   NULL, 2, 43, 1, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(45,'日志删除', 'system:log:delete', NULL, 2, 43, 2, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(46,'日志导出', 'system:log:export', NULL, 2, 43, 3, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

/* —— 在线用户 → 按钮 —— */
(49,'在线查询', 'system:online:list',  NULL, 2, 48, 1, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(50,'强制下线', 'system:online:kick',  NULL, 2, 48, 2, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

/* —— 文件管理 → 按钮 —— */
(52,'文件查询', 'system:file:list',   NULL, 2, 51, 1, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(53,'文件上传', 'system:file:upload', NULL, 2, 51, 2, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(54,'文件删除', 'system:file:delete', NULL, 2, 51, 3, NULL, 1, '2026-06-16 09:00:00', '2026-06-16 09:00:00'),

/* —— 系统设置 → 子菜单 —— */
(28,'参数管理', 'system:config', '/system/config', 1, 27, 1, 'SetUp',      1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(29,'字典管理', 'system:dict',   '/system/dict',   1, 27, 2, 'Collection', 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),

/* —— 参数管理 → 按钮 —— */
(30,'参数查询', 'system:config:list',   NULL, 2, 28, 1, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(31,'参数新增', 'system:config:add',    NULL, 2, 28, 2, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(32,'参数编辑', 'system:config:edit',   NULL, 2, 28, 3, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(33,'参数删除', 'system:config:delete', NULL, 2, 28, 4, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(34,'参数导出', 'system:config:export', NULL, 2, 28, 5, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),

/* —— 字典管理 → 按钮 —— */
(35,'字典类型查询', 'system:dict:type:list',   NULL, 2, 29, 1, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(36,'字典类型新增', 'system:dict:type:add',    NULL, 2, 29, 2, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(37,'字典类型编辑', 'system:dict:type:edit',   NULL, 2, 29, 3, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(38,'字典类型删除', 'system:dict:type:delete', NULL, 2, 29, 4, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(39,'字典数据查询', 'system:dict:data:list',   NULL, 2, 29, 5, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(40,'字典数据新增', 'system:dict:data:add',    NULL, 2, 29, 6, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(41,'字典数据编辑', 'system:dict:data:edit',   NULL, 2, 29, 7, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02'),
(42,'字典数据删除', 'system:dict:data:delete', NULL, 2, 29, 8, NULL, 1, '2026-06-14 22:35:02', '2026-06-14 22:35:02');


/* =====================================================================
 * 4. sys_user_role — 用户角色关联表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`      bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

INSERT INTO `sys_user_role` (`id`,`user_id`,`role_id`) VALUES
(1,1,1);


/* =====================================================================
 * 5. sys_role_permission — 角色权限关联表
 *    超级管理员（role_id=1）拥有全部权限
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id`       bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`)
SELECT 1, `id` FROM `sys_permission`;


/* =====================================================================
 * 6. sys_config — 系统参数表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '参数ID',
  `config_name`  varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数名称',
  `config_key`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数键',
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值',
  `remark`       varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted`      tinyint      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数表';


/* =====================================================================
 * 7. sys_dict_type — 字典类型表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  `dict_name`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
  `dict_type`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
  `status`      tinyint      NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `remark`      varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted`     tinyint      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';


/* =====================================================================
 * 8. sys_dict_data — 字典数据表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
  `dict_type`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
  `dict_label`  varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典标签',
  `dict_value`  varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键值',
  `sort`        int          NOT NULL DEFAULT '0' COMMENT '排序',
  `css_class`   varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回显样式（如 success/danger/warning/info）',
  `status`      tinyint      NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `remark`      varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted`     tinyint      NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';


/* =====================================================================
 * 9. sys_log — 操作日志表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
  `id`              bigint       NOT NULL COMMENT '日志ID',
  `module`          varchar(64)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块名',
  `business_type`   varchar(16)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型: ADD/EDIT/DELETE/EXPORT/IMPORT/LOGIN/LOGOUT/OTHER',
  `description`     varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `request_method`  varchar(8)   COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTTP方法',
  `request_url`     varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URL',
  `request_param`   text         COLLATE utf8mb4_unicode_ci COMMENT '请求参数JSON',
  `response_status` tinyint      NOT NULL DEFAULT '1' COMMENT '响应状态: 0失败 1成功',
  `error_msg`       varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `cost_ms`         bigint       DEFAULT NULL COMMENT '耗时(毫秒)',
  `operator_id`     bigint       DEFAULT NULL COMMENT '操作人ID',
  `operator_name`   varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人用户名',
  `client_source`   varchar(16)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端来源: web/app/...',
  `client_ip`       varchar(64)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent`      varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_operator_name` (`operator_name`),
  KEY `idx_module_type` (`module`,`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';


/* =====================================================================
 * 10. sys_file — 文件管理表
 * ===================================================================== */
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
  `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `original_name`  varchar(255)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传时的原始文件名',
  `storage_name`   varchar(255)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径名（含哈希，避免重名）',
  `url`            varchar(500)  COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可访问URL（相对路径或绝对URL）',
  `ext`            varchar(20)   COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '扩展名（小写，不带点）',
  `mime_type`      varchar(100)  COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Content-Type',
  `size`           bigint        NOT NULL COMMENT '字节数',
  `uploader_id`    bigint        DEFAULT NULL COMMENT '上传人ID',
  `uploader_name`  varchar(50)   COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上传人账号',
  `deleted`        tinyint       NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_uploader` (`uploader_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件管理表';


/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
