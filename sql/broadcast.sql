USE
solo_db;

-- ============================================================
--  语音广播（网页麦克风实时喊话，单设备单通道单向广播）
--  只加管理端权限点，没有新业务表：广播会话走 Redis，信令复用 gb_* 设备/通道表。
--
--  接在 sys_permission 现有最大 id 148（开放平台）之后：
--    149 语音广播  二级菜单 type=1 url=/gb28181/broadcast parent=5（国标管理）
--    150 喊话控制  按钮   type=2 parent=149（开始/停止共用一个权限点）
--  脚本可重复执行：先清旧数据再插入。
-- ============================================================
DELETE FROM sys_role_permission WHERE permission_id BETWEEN 149 AND 150;
DELETE FROM sys_permission          WHERE id BETWEEN 149 AND 150;

INSERT INTO sys_permission (id, permission_name, permission_code, url, type, parent_id, sort, icon, status, deleted, create_time, update_time) VALUES
 (149, '语音广播', 'gb28181:broadcast',      '/gb28181/broadcast', 1, 5, 11, 'IconSpeakerphone', 1, 0, NOW(), NOW()),
 (150, '喊话控制', 'gb28181:broadcast:talk', NULL,                 2, 149, 1, NULL,             1, 0, NOW(), NOW());

-- 顺手挂给超级管理员（role_id=1）。其他角色在"角色管理"里自己勾。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 149 AND 150
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 1 AND rp.permission_id = sys_permission.id);
