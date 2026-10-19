import {
  IconLayoutDashboard,
  IconSettings,
  IconTool,
  IconFlask,
  IconCpu,
  IconVideo,
  IconCamera,
  IconUsers,
  IconUserCog,
  IconLock,
  IconFileText,
  IconPlugConnected,
  IconFolder,
  IconServer,
  IconServer2,
  IconAdjustments,
  IconBook2,
  IconChartHistogram,
  IconShare,
  IconShare2,
  IconUpload,
  IconDeviceCctv,
  IconList,
  IconBell,
  IconRoute,
  IconUser,
  type Icon,
} from '@tabler/icons-vue'
import type { UserAuthResponse } from '@/types/api'
import { hasPage } from '@/app/dynamic-routes'

export interface NavItem {
  label: string
  path: string
  icon: Icon
  perm?: string
}
/** 一级模块：显示在图标栏，其下叶子项显示在常驻二级栏 */
export interface NavModule {
  id: string
  label: string
  icon: Icon
  items: NavItem[]
}

/**
 * 已注册图标表。
 * 数据库 sys_permission.icon 存 Tabler 图标导出名（如 IconUsers），
 * 这里按需导入注册，避免全量引入整个图标库；未注册的图标走兜底。
 */
const ICON_REGISTRY: Record<string, Icon> = {
  IconSettings,
  IconTool,
  IconFlask,
  IconCpu,
  IconVideo,
  IconCamera,
  IconUsers,
  IconUserCog,
  IconLock,
  IconFileText,
  IconPlugConnected,
  IconFolder,
  IconServer,
  IconServer2,
  IconAdjustments,
  IconBook2,
  IconChartHistogram,
  IconShare,
  IconShare2,
  IconUpload,
  IconDeviceCctv,
  IconList,
  IconBell,
  IconRoute,
}

/**
 * 固定在图标栏首位的工作台模块（独立于后端菜单树）。
 * 后端菜单树没有首页相关项，这里用固定项填充二级栏，
 * 使工作台与其它模块一样拥有常驻菜单项，高亮/面包屑逻辑统一。
 */
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

const toItem = (menu: UserAuthResponse): NavItem | null => {
  if (!hasPage(menu.url)) return null
  return {
    label: menu.permissionName,
    path: menu.url,
    icon: (menu.icon && ICON_REGISTRY[menu.icon]) || IconFileText,
  }
}

/**
 * 将后端菜单树转换为两级导航模块。
 * 一级目录（带 icon）显示在图标栏；其下有页面的叶子菜单显示在常驻二级栏。
 */
export const buildNavFromMenus = (menus: UserAuthResponse[]): NavModule[] =>
  menus
    .map((top) => {
      const items = (top.children ?? [])
        .map((child) => toItem(child))
        .filter((i): i is NavItem => i !== null)
      return {
        id: String(top.id),
        label: top.permissionName,
        icon: (top.icon && ICON_REGISTRY[top.icon]) || IconFileText,
        items,
      }
    })
    .filter((m) => m.items.length > 0)
