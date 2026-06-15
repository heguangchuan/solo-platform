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
  `type` tinyint NOT NULL COMMENT '类型: 1菜单 2按钮 3接口',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级ID',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `icon` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`),
  KEY `idx_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

/*Data for the table `sys_permission` */

insert  into `sys_permission`(`id`,`permission_name`,`permission_code`,`url`,`type`,`parent_id`,`sort`,`icon`,`status`,`create_time`,`update_time`) values 
(1,'系统管理','system',NULL,1,0,1,'el-icon-setting',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(2,'用户管理','system:user','/system/user',1,1,1,'el-icon-user',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(3,'角色管理','system:role','/system/role',1,1,2,'el-icon-user-solid',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(4,'权限管理','system:permission','/system/permission',1,1,3,'el-icon-lock',1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(5,'用户查询','system:user:list',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(6,'用户新增','system:user:add',NULL,2,2,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(7,'用户编辑','system:user:edit',NULL,2,2,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(8,'用户删除','system:user:delete',NULL,2,2,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(9,'用户启停','system:user:switch',NULL,2,2,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(10,'角色查询','system:role:list',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(11,'角色新增','system:role:add',NULL,2,3,2,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(12,'角色编辑','system:role:edit',NULL,2,3,3,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(13,'角色删除','system:role:delete',NULL,2,3,4,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(15,'获取用户信息','user/info','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:23'),
(16,'用户分页列表','system:user:page','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:24'),
(17,'角色分页列表','system:role:page','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:25'),
(18,'权限列表','system:permission:list','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:28'),
(19,'用户导入','system:user:import',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(20,'用户导出','system:user:export',NULL,2,2,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(21,'角色导入','system:role:import',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(22,'角色导出','system:role:export',NULL,2,3,1,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(23,'用户授权','system:user:r2u',NULL,2,2,5,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02'),
(24,'权限新增','system:permission:add','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:02'),
(25,'权限修改','system:permission:update','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:03'),
(26,'权限删除','system:permission:delete','',3,0,0,NULL,1,'2026-06-14 22:35:02','2026-06-15 23:03:04');

/*Table structure for table `sys_role` */

DROP TABLE IF EXISTS `sys_role`;

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

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
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

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
(40,1,26);

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
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

/*Data for the table `sys_user` */

insert  into `sys_user`(`id`,`username`,`password`,`nickname`,`email`,`phone`,`avatar`,`status`,`create_time`,`update_time`) values 
(1,'admin','0192023a7bbd73250516f069df18b500','管理员',NULL,NULL,NULL,1,'2026-06-14 22:35:02','2026-06-14 22:35:02');

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

/*Data for the table `sys_user_role` */

insert  into `sys_user_role`(`id`,`user_id`,`role_id`) values 
(1,1,1);

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
