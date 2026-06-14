import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ref } from 'vue'
import { getAuthMenusApi } from '@/api/auth'

// 静态路由（不随用户权限变化）
const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/403',
    name: 'NoAuth',
    component: () => import('@/views/Error/403.vue'),
    meta: { title: '无权限', hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/Error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true },
  },
]

// 主路由容器名
let mainRouteName = 'Main'

// 动态组件注册表
const menuRouteComponents: Record<string, any> = {
  Dashboard: () => import('@/views/Dashboard/index.vue'),
  SystemUser: () => import('@/views/System/User/index.vue'),
  SystemRole: () => import('@/views/System/Role/index.vue'),
  SystemPermission: () => import('@/views/System/Permission/index.vue'),
}

function generateRouteName(path: string): string {
  return path
    .split('/')
    .filter(Boolean)
    .map((segment) => segment.replace(/^\/+/, ''))
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join('')
}

/**
 * 将菜单树注册为路由
 * 使用 router.addRoute() 而非直接操作 children，确保路由表正确更新
 */
function registerMenuRoutes(menus: any[], parentPath = '') {
  menus.forEach((menu: any) => {
    // 没有 url 的节点（如"系统管理"分组）不注册路由，只递归子节点
    if (!menu.url) {
      if (menu.children?.length) {
        registerMenuRoutes(menu.children, parentPath)
      }
      return
    }

    const routePath = menu.url.replace(/^\//, '')
    const fullPath = parentPath ? `${parentPath}/${routePath}` : routePath
    const routeName = generateRouteName(fullPath)

    const route: RouteRecordRaw = {
      path: routePath,
      name: routeName,
      component: menuRouteComponents[routeName] || (() => import('@/views/Error/404.vue')),
      meta: {
        title: menu.permissionName,
        icon: menu.icon,
        parentId: menu.parentId,
      },
    }

    // 递归注册子路由
    if (menu.children?.length) {
      registerMenuRoutes(menu.children, fullPath)
    }

    // 使用 router.addRoute 注册到主路由下
    router.addRoute(mainRouteName, route)
  })
}

// 用户菜单树
export const userMenus = ref<any[]>([])

// 是否已注册过路由（防止重复注册）
let isRoutesRegistered = false

// 导出动态路由配置
export const dynamicRouteConfig = {
  /** 登录后调用：注册路由 */
  registerAll(routes: any[]) {
    userMenus.value = routes
    registerMenuRoutes(routes)
    isRoutesRegistered = true
  },

  /** 退出登录时清除 */
  clear() {
    // 清除所有动态注册的路由（保留 Dashboard）
    const mainRoute = mainRoutes.find((r) => r.name === mainRouteName)
    if (!mainRoute) return
    mainRoute.children = mainRoute.children!.filter((r) => r.name === 'Dashboard')
    userMenus.value = []
    isRoutesRegistered = false
  },

  /** 获取当前菜单树 */
  getMenus() {
    return userMenus.value
  },

  /** 刷新页面时重新拉菜单并注册路由 */
  async refreshRoutes() {
    if (isRoutesRegistered) return
    try {
      const res = await getAuthMenusApi()
      const menus = res.data
      if (menus?.length) {
        userMenus.value = menus
        registerMenuRoutes(menus)
        isRoutesRegistered = true
      }
    } catch {
      // 拉取失败不影响页面
    }
  },
}

// 首页 + 主路由容器
const mainRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    name: mainRouteName,
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard/index.vue'),
        meta: { title: '首页', icon: 'Odometer' },
      },
    ],
  },
]

const allRoutes: RouteRecordRaw[] = [...staticRoutes, ...mainRoutes]

const router = createRouter({
  history: createWebHistory(),
  routes: allRoutes,
})

router.beforeEach(async (to, _from, next) => {
  document.title = `${(to.meta.title as string) || ''} - Solo 后台管理系统`

  if (to.path === '/login') {
    next()
    return
  }

  const token = localStorage.getItem('solo-token')
  if (!token) {
    next('/login')
    return
  }

  // 刷新页面时，如果还没注册过动态路由，重新拉菜单
  if (!isRoutesRegistered) {
    try {
      const res = await getAuthMenusApi()
      const menus = res.data
      if (menus?.length) {
        userMenus.value = menus
        registerMenuRoutes(menus)
        isRoutesRegistered = true
      }
    } catch {
      // 拉取失败，放行
    }
  }

  next()
})

export default router
