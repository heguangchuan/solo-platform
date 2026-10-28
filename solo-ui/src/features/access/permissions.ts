/**
 * 按钮权限码常量 —— 与数据库 sys_permission.permission_code 严格对应
 * 来源：sql/solo_db.sql
 * type=1 为菜单，type=2 为按钮；后端接口据此鉴权
 */
export const PERM = {
  // ===== 系统管理 system =====
  SYSTEM: 'system',
  // 用户
  USER: 'system:user',
  USER_LIST: 'system:user:list',
  USER_ADD: 'system:user:add',
  USER_EDIT: 'system:user:edit',
  USER_DELETE: 'system:user:delete',
  USER_SWITCH: 'system:user:switch',
  USER_R2U: 'system:user:r2u',
  USER_IMPORT: 'system:user:import',
  USER_EXPORT: 'system:user:export',
  USER_PAGE: 'system:user:page',
  // 角色
  ROLE: 'system:role',
  ROLE_LIST: 'system:role:list',
  ROLE_ADD: 'system:role:add',
  ROLE_EDIT: 'system:role:edit',
  ROLE_DELETE: 'system:role:delete',
  ROLE_P2R: 'system:role:p2r',
  ROLE_IMPORT: 'system:role:import',
  ROLE_EXPORT: 'system:role:export',
  ROLE_PAGE: 'system:role:page',
  // 权限
  PERMISSION: 'system:permission',
  PERMISSION_LIST: 'system:permission:list',
  PERMISSION_ADD: 'system:permission:add',
  PERMISSION_EDIT: 'system:permission:edit',
  PERMISSION_DELETE: 'system:permission:delete',
  // 日志
  LOG: 'system:log',
  LOG_LIST: 'system:log:list',
  LOG_DELETE: 'system:log:delete',
  LOG_EXPORT: 'system:log:export',
  // 在线用户
  ONLINE: 'system:online',
  ONLINE_LIST: 'system:online:list',
  ONLINE_KICK: 'system:online:kick',
  // 文件
  FILE: 'system:file',
  FILE_LIST: 'system:file:list',
  FILE_UPLOAD: 'system:file:upload',
  FILE_DELETE: 'system:file:delete',

  // ===== 服务器管理 server =====
  SERVER: 'server',
  SERVER_LIST: 'server:list',
  SERVER_ADD: 'server:add',
  SERVER_EDIT: 'server:edit',
  SERVER_DELETE: 'server:delete',
  SERVER_TERMINAL: 'server:terminal',
  SERVER_FILE_LIST: 'server:file:list',
  SERVER_FILE_UPLOAD: 'server:file:upload',
  SERVER_FILE_DOWNLOAD: 'server:file:download',
  SERVER_FILE_DELETE: 'server:file:delete',

  // ===== 系统设置 setting =====
  SETTING: 'setting',
  // 参数
  CONFIG: 'setting:config',
  CONFIG_LIST: 'setting:config:list',
  CONFIG_ADD: 'setting:config:add',
  CONFIG_EDIT: 'setting:config:edit',
  CONFIG_DELETE: 'setting:config:delete',
  CONFIG_EXPORT: 'setting:config:export',
  // 字典
  DICT: 'setting:dict',
  DICT_TYPE_LIST: 'setting:dict:type:list',
  DICT_TYPE_ADD: 'setting:dict:type:add',
  DICT_TYPE_EDIT: 'setting:dict:type:edit',
  DICT_TYPE_DELETE: 'setting:dict:type:delete',
  DICT_DATA_LIST: 'setting:dict:data:list',
  DICT_DATA_ADD: 'setting:dict:data:add',
  DICT_DATA_EDIT: 'setting:dict:data:edit',
  DICT_DATA_DELETE: 'setting:dict:data:delete',

  // ===== 媒体管理 media =====
  MEDIA: 'media',
  // 流媒体节点
  MEDIA_SERVER: 'media:server',
  MEDIA_SERVER_LIST: 'media:server:list',
  MEDIA_SERVER_SAVE: 'media:server:save',
  MEDIA_SERVER_DELETE: 'media:server:delete',
  // 拉流代理
  MEDIA_STREAM_PROXY: 'media:stream-proxy',
  MEDIA_STREAM_PROXY_LIST: 'media:stream-proxy:list',
  MEDIA_STREAM_PROXY_ADD: 'media:stream-proxy:add',
  MEDIA_STREAM_PROXY_EDIT: 'media:stream-proxy:edit',
  MEDIA_STREAM_PROXY_DELETE: 'media:stream-proxy:delete',
  // 推流
  MEDIA_STREAM_PUSH: 'media:stream-push',
  MEDIA_STREAM_PUSH_LIST: 'media:stream-push:list',
  MEDIA_STREAM_PUSH_DELETE: 'media:stream-push:delete',
  // 推流方鉴权
  MEDIA_STREAM_PUSH_CLIENT: 'media:stream-push-client',
  MEDIA_STREAM_PUSH_CLIENT_LIST: 'media:stream-push-client:list',
  MEDIA_STREAM_PUSH_CLIENT_ADD: 'media:stream-push-client:add',
  MEDIA_STREAM_PUSH_CLIENT_EDIT: 'media:stream-push-client:edit',
  MEDIA_STREAM_PUSH_CLIENT_DELETE: 'media:stream-push-client:delete',

  // ===== 国标管理 gb28181 =====
  GB: 'gb28181',
  // 设备
  GB_DEVICE: 'gb28181:device',
  GB_DEVICE_LIST: 'gb28181:device:list',
  GB_DEVICE_ADD: 'gb28181:device:add',
  GB_DEVICE_EDIT: 'gb28181:device:edit',
  GB_DEVICE_DELETE: 'gb28181:device:delete',
  GB_DEVICE_SYNC: 'gb28181:device:sync',
  // 设备通道
  GB_DEVICE_CHANNEL: 'gb28181:device-channel',
  GB_DEVICE_CHANNEL_LIST: 'gb28181:device-channel:list',
  GB_DEVICE_CHANNEL_ADD: 'gb28181:device-channel:add',
  GB_DEVICE_CHANNEL_EDIT: 'gb28181:device-channel:edit',
  GB_DEVICE_CHANNEL_DELETE: 'gb28181:device-channel:delete',
  // 视频点播
  GB_PLAY: 'gb28181:play',
  // 语音广播
  GB_BROADCAST: 'gb28181:broadcast',
  GB_BROADCAST_TALK: 'gb28181:broadcast:talk',
  // 报警中心
  GB_ALARM: 'gb28181:alarm',
  GB_ALARM_LIST: 'gb28181:alarm:list',
  GB_ALARM_HANDLE: 'gb28181:alarm:handle',
  GB_ALARM_DELETE: 'gb28181:alarm:delete',
  // 移动轨迹
  GB_POSITION: 'gb28181:position',
  GB_POSITION_LIST: 'gb28181:position:list',
  GB_POSITION_DELETE: 'gb28181:position:delete',
  // 云台控制
  GB_PTZ: 'gb28181:ptz',
  // 级联平台
  GB_PARENT_PLATFORM: 'gb28181:parent-platform',
  GB_PARENT_PLATFORM_LIST: 'gb28181:parent-platform:list',
  GB_PARENT_PLATFORM_ADD: 'gb28181:parent-platform:add',
  GB_PARENT_PLATFORM_EDIT: 'gb28181:parent-platform:edit',
  GB_PARENT_PLATFORM_DELETE: 'gb28181:parent-platform:delete',
  // 推送分组
  GB_SHARE_GROUP_ADD: 'gb28181:share-group:add',
  GB_SHARE_GROUP_EDIT: 'gb28181:share-group:edit',
  GB_SHARE_GROUP_DELETE: 'gb28181:share-group:delete',
  // 接入禁用黑名单
  GB_PLATFORM: 'gb28181:platform',
  GB_PLATFORM_LIST: 'gb28181:platform:list',
  GB_PLATFORM_ADD: 'gb28181:platform:add',
  GB_PLATFORM_EDIT: 'gb28181:platform:edit',
  GB_PLATFORM_DELETE: 'gb28181:platform:delete',
  // 云端录像
  GB_CLOUD_RECORD: 'gb28181:cloud-record',
  GB_RECORD_LIST: 'gb28181:record:list',
  GB_RECORD_CONTROL: 'gb28181:record:control',
  GB_RECORD_DELETE: 'gb28181:record:delete',
  // 录像计划
  GB_RECORD_PLAN: 'gb28181:record-plan',
  GB_RECORD_PLAN_VIEW: 'gb28181:record:plan',
  GB_RECORD_PLAN_ADD: 'gb28181:record:plan:add',
  GB_RECORD_PLAN_EDIT: 'gb28181:record:plan:edit',
  GB_RECORD_PLAN_DELETE: 'gb28181:record:plan:delete',

  // ===== ONVIF 管理 onvif =====
  ONVIF: 'onvif',
  // 设备
  ONVIF_DEVICE: 'onvif:device',
  ONVIF_DEVICE_LIST: 'onvif:device:list',
  ONVIF_DEVICE_ADD: 'onvif:device:add',
  ONVIF_DEVICE_EDIT: 'onvif:device:edit',
  ONVIF_DEVICE_DELETE: 'onvif:device:delete',
  ONVIF_DEVICE_PROBE: 'onvif:device:probe',

  // ===== 开放平台 openapi =====
  OPENAPI: 'openapi',
  OPENAPI_APP: 'openapi:app',
  OPENAPI_APP_LIST: 'openapi:app:list',
  OPENAPI_APP_ADD: 'openapi:app:add',
  OPENAPI_APP_EDIT: 'openapi:app:edit',
  OPENAPI_APP_ROTATE: 'openapi:app:rotate',
  OPENAPI_LOG: 'openapi:log',
  OPENAPI_LOG_LIST: 'openapi:log:list',
  OPENAPI_LOG_EXPORT: 'openapi:log:export',
} as const

export type PermCode = (typeof PERM)[keyof typeof PERM]
