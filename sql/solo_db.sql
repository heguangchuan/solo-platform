/*
SQLyog Ultimate v12.2.6 (64 bit)
MySQL - 8.0.19 : Database - solo_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`solo_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `solo_db`;

/*Table structure for table `sys_permission` */

DROP TABLE IF EXISTS `sys_permission`;

CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `permission_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联URL',
  `type` tinyint NOT NULL COMMENT '类型: 1菜单 2按钮',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级ID',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

/*Data for the table `sys_permission` */

insert  into `sys_permission`(`id`,`permission_name`,`permission_code`,`url`,`type`,`parent_id`,`sort`,`icon`,`status`,`create_time`,`update_time`) values 
(1,'系统管理','system',NULL,1,0,1,'Setting',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(2,'用户管理','system:user','/system/user',1,1,1,'User',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(3,'角色管理','system:role','/system/role',1,1,2,'UserFilled',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(4,'权限管理','system:permission','/system/permission',1,1,3,'Lock',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(5,'用户查询','system:user:list',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(6,'用户新增','system:user:add',NULL,2,2,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(7,'用户编辑','system:user:edit',NULL,2,2,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(8,'用户删除','system:user:delete',NULL,2,2,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(9,'用户启停','system:user:switch',NULL,2,2,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(10,'角色查询','system:role:list',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(11,'角色新增','system:role:add',NULL,2,3,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(12,'角色编辑','system:role:edit',NULL,2,3,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(13,'角色删除','system:role:delete',NULL,2,3,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(15,'获取用户信息','user/info','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:23'),
(16,'用户分页列表','system:user:page','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:24'),
(17,'角色分页列表','system:role:page','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:25'),
(18,'权限列表','system:permission:list','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:28'),
(19,'用户导入','system:user:import',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(20,'用户导出','system:user:export',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(21,'角色导入','system:role:import',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(22,'角色导出','system:role:export',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(23,'用户授权','system:user:r2u',NULL,2,2,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(24,'权限新增','system:permission:add','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:02'),
(25,'权限修改','system:permission:update','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:03'),
(26,'权限删除','system:permission:delete','',2,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:04'),
(27,'系统设置','setting',NULL,1,0,2,'Tools',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(28,'参数管理','system:config','/system/config',1,27,1,'SetUp',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(29,'字典管理','system:dict','/system/dict',1,27,2,'Collection',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(30,'参数查询','system:config:list',NULL,2,28,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(31,'参数新增','system:config:add',NULL,2,28,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(32,'参数编辑','system:config:edit',NULL,2,28,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(33,'参数删除','system:config:delete',NULL,2,28,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(34,'参数导出','system:config:export',NULL,2,28,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(35,'字典类型查询','system:dict:type:list',NULL,2,29,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(36,'字典类型新增','system:dict:type:add',NULL,2,29,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(37,'字典类型编辑','system:dict:type:edit',NULL,2,29,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(38,'字典类型删除','system:dict:type:delete',NULL,2,29,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(39,'字典数据查询','system:dict:data:list',NULL,2,29,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(40,'字典数据新增','system:dict:data:add',NULL,2,29,6,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(41,'字典数据编辑','system:dict:data:edit',NULL,2,29,7,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(42,'字典数据删除','system:dict:data:delete',NULL,2,29,8,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(43,'日志管理','system:log','/system/log',1,1,4,'Document',1,'2026-06-16 09:00:00','2026-06-16 09:00:00'),
(44,'日志查询','system:log:list',NULL,2,43,1,NULL,1,'2026-06-16 09:00:00','2026-06-16 09:00:00'),
(45,'日志删除','system:log:delete',NULL,2,43,2,NULL,1,'2026-06-16 09:00:00','2026-06-16 09:00:00'),
(46,'日志导出','system:log:export',NULL,2,43,3,NULL,1,'2026-06-16 09:00:00','2026-06-16 09:00:00');

/*Table structure for table `sys_role` */

DROP TABLE IF EXISTS `sys_role`;

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

/*Data for the table `sys_role` */

insert  into `sys_role`(`id`,`role_code`,`role_name`,`description`,`status`,`create_time`,`update_time`) values 
(1,'ADMIN','超级管理员','拥有所有权限',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(2,'USER','普通用户','基础用户角色',1,'2026-06-14 22:35:02','2026-06-14 22:35:02');

/*Table structure for table `sys_role_permission` */

DROP TABLE IF EXISTS `sys_role_permission`;

CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

/*Data for the table `sys_role_permission` */

insert  into `sys_role_permission`(`id`,`role_id`,`permission_id`) values 
(1,1,1),
(7,1,2),
(8,1,3),
(9,1,4),
(10,1,5),
(11,1,6),
(12,1,7),
(13,1,8),
(14,1,9),
(15,1,10),
(16,1,11),
(17,1,12),
(18,1,13),
(2,1,14),
(3,1,15),
(4,1,16),
(5,1,17),
(6,1,18),
(21,1,19),
(22,1,20),
(23,1,21),
(24,1,22),
(19,1,23),
(20,1,24),
(39,1,25),
(40,1,26),
(41,1,27),
(42,1,28),
(43,1,29),
(44,1,30),
(45,1,31),
(46,1,32),
(47,1,33),
(48,1,34),
(49,1,35),
(50,1,36),
(51,1,37),
(52,1,38),
(53,1,39),
(54,1,40),
(55,1,41),
(56,1,42),
(57,1,43),
(58,1,44),
(59,1,45),
(60,1,46);

/*Table structure for table `sys_user` */

DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MD5加密密码',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `pwd_reset_required` tinyint NOT NULL DEFAULT '0' COMMENT '是否首次登录强制改密: 0否 1是',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

/*Data for the table `sys_user` */

insert  into `sys_user`(`id`,`username`,`password`,`nickname`,`email`,`phone`,`avatar`,`status`,`pwd_reset_required`,`create_time`,`update_time`) values
(1,'admin','0192023a7bbd73250516f069df18b500','管理员',NULL,NULL,NULL,1,0,'2026-06-14 22:35:02','2026-06-14 22:35:02');

/*Table structure for table `sys_config` */

DROP TABLE IF EXISTS `sys_config`;

CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '参数ID',
  `config_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数键',
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数值',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数表';

/*Table structure for table `sys_dict_type` */

DROP TABLE IF EXISTS `sys_dict_type`;

CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  `dict_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

/*Table structure for table `sys_dict_data` */

DROP TABLE IF EXISTS `sys_dict_data`;

CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
  `dict_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键值',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `css_class` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回显样式（如 success/danger/warning/info）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

/*Table structure for table `sys_log` */

DROP TABLE IF EXISTS `sys_log`;

CREATE TABLE `sys_log` (
  `id` bigint NOT NULL COMMENT '日志ID',
  `module` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块名',
  `business_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型: ADD/EDIT/DELETE/EXPORT/IMPORT/LOGIN/LOGOUT/OTHER',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `request_method` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTTP方法',
  `request_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URL',
  `request_param` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数JSON',
  `response_status` tinyint NOT NULL DEFAULT '1' COMMENT '响应状态: 0失败 1成功',
  `error_msg` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `cost_ms` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人用户名',
  `client_source` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端来源: web/app/...',
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_operator_name` (`operator_name`),
  KEY `idx_module_type` (`module`,`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

/*Table structure for table `sys_user_role` */

DROP TABLE IF EXISTS `sys_user_role`;

CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

/*Data for the table `sys_user_role` */

insert  into `sys_user_role`(`id`,`user_id`,`role_id`) values
(1,1,1);

/* ============================================================
   菜单图标升级脚本：将旧版 element-ui class（el-icon-xxx）
   迁移为 @element-plus/icons-vue 的 PascalCase 组件名。
   已存在的库可直接执行下面的 UPDATE。
   ============================================================ */
UPDATE `sys_permission` SET `icon` = 'Setting'    WHERE `id` = 1;
UPDATE `sys_permission` SET `icon` = 'User'       WHERE `id` = 2;
UPDATE `sys_permission` SET `icon` = 'UserFilled' WHERE `id` = 3;
UPDATE `sys_permission` SET `icon` = 'Lock'       WHERE `id` = 4;
UPDATE `sys_permission` SET `icon` = 'Tools'      WHERE `id` = 27;
UPDATE `sys_permission` SET `icon` = 'SetUp'      WHERE `id` = 28;
UPDATE `sys_permission` SET `icon` = 'Collection' WHERE `id` = 29;
UPDATE `sys_permission` SET `icon` = 'Document'   WHERE `id` = 43;

/* 增量字段：是否首次登录强制改密 */
ALTER TABLE `sys_user`
    ADD COLUMN `pwd_reset_required` tinyint NOT NULL DEFAULT 0
    COMMENT '是否首次登录强制改密: 0否 1是' AFTER `status`;

/* 移除"是否系统内置"字段（参数管理） */
ALTER TABLE `sys_config` DROP COLUMN `config_type`;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
