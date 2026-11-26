import {
  IconLayoutDashboard,
  IconUser,
  IconBell,
  IconDeviceCctv,
  IconList,
  IconShare2,
  IconBellPlus,
  IconRoute,
  IconMovie,
  IconCalendarTime,
  IconSpeakerphone,
  IconEye,
  IconCamera,
  IconServer2,
  IconShare,
  IconUpload,
  IconPlugConnected,
  IconFileText,
  IconUsers,
  type Icon,
} from '@tabler/icons-vue'

export interface NavItem {
  label: string
  path: string
  icon: Icon
  perm?: string
}

export interface NavModule {
  id: string
  label: string
  icon: Icon
  items: NavItem[]
}

export const homeModule: NavModule = {
  id: 'home',
  label: '工作台',
  icon: IconLayoutDashboard,
  items: [
    { label: '工作台首页', path: '/', icon: IconLayoutDashboard },
    { label: '个人信息', path: '/profile', icon: IconUser },
    { label: '消息中心', path: '/messages', icon: IconBell },
  ],
}

export const staticNavModules: NavModule[] = [
  {
    id: 'gb28181',
    label: '国标管理',
    icon: IconDeviceCctv,
    items: [
      { label: '设备管理', path: '/gb28181/device', icon: IconDeviceCctv },
      { label: '通道管理', path: '/gb28181/device-channel', icon: IconList },
      { label: '级联平台', path: '/gb28181/platform', icon: IconShare2 },
      { label: '报警中心', path: '/gb28181/alarm', icon: IconBellPlus },
      { label: '移动轨迹', path: '/gb28181/position', icon: IconRoute },
      { label: '云端录像', path: '/gb28181/cloud-record', icon: IconMovie },
      { label: '录像计划', path: '/gb28181/record-plan', icon: IconCalendarTime },
      { label: '语音广播', path: '/gb28181/broadcast', icon: IconSpeakerphone },
      { label: '观看记录', path: '/gb28181/view-record', icon: IconEye },
    ],
  },
  {
    id: 'onvif',
    label: 'ONVIF管理',
    icon: IconCamera,
    items: [{ label: '设备管理', path: '/onvif/device', icon: IconCamera }],
  },
  {
    id: 'media',
    label: '流媒体管理',
    icon: IconServer2,
    items: [
      { label: '节点管理', path: '/media/server', icon: IconServer2 },
      { label: '拉流代理', path: '/media/stream-proxy', icon: IconShare },
      { label: '推流管理', path: '/media/stream-push', icon: IconUpload },
    ],
  },
  {
    id: 'openapi',
    label: '开放平台管理',
    icon: IconPlugConnected,
    items: [
      { label: '应用管理', path: '/openapi/app', icon: IconPlugConnected },
      { label: '调用日志', path: '/openapi/log', icon: IconFileText },
    ],
  },
  {
    id: 'user',
    label: '用户管理',
    icon: IconUsers,
    items: [{ label: '用户列表', path: '/system/user', icon: IconUsers }],
  },
]

/** 兼容原有调用点：始终返回静态导航模块。 */
export const buildNavFromMenus = (_menus?: unknown): NavModule[] => staticNavModules
