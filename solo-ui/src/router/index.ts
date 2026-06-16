import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ref } from 'vue'
import { getAuthMenusApi, getAuthPermissionsApi, getProfileApi } from '@/api/auth'

const mainRouteName = 'Main'

const menuRouteComponents: Record<string, any> = {
  Dashboard: () => import('@/views/Dashboard/index.vue'),
  SystemUser: () => import('@/views/System/User/index.vue'),
  SystemRole: () => import('@/views/System/Role/index.vue'),
  SystemPermission: () => import('@/views/System/Permission/index.vue'),
  SystemConfig: () => import('@/views/System/Config/index.vue'),
  SystemDict: () => import('@/views/System/Dict/index.vue'),
  SystemLog: () => import('@/views/System/Log/index.vue'),
}

function generateRouteName(path: string): string {
  return path
    .split('/')
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join('')
}

function registerMenuRoutes(menus: any[]) {
  menus.forEach((menu: any) => {
    if (!menu.url) {
      if (menu.children?.length) registerMenuRoutes(menu.children)
      return
    }

    const routePath = menu.url.replace(/^\//, '')
    const routeName = generateRouteName(routePath)

    if (!router.hasRoute(routeName)) {
      router.addRoute(mainRouteName, {
        path: routePath,
        name: routeName,
        component: menuRouteComponents[routeName] || (() => import('@/views/Error/404.vue')),
        meta: {
          title: menu.permissionName,
          icon: menu.icon,
          parentId: menu.parentId,
        },
      })
    }

    if (menu.children?.length) registerMenuRoutes(menu.children)
  })
}

export const userMenus = ref<any[]>([])
let isRoutesRegistered = false

export const dynamicRouteConfig = {
  registerAll(routes: any[]) {
    userMenus.value = routes
    registerMenuRoutes(routes)
    addNotFoundRoute()
    isRoutesRegistered = true
  },
  clear() {
    userMenus.value = []
    isRoutesRegistered = false
  },
  getMenus() {
    return userMenus.value
  },
  async refreshRoutes() {
    if (isRoutesRegistered) return
    const [menuRes, permissionRes, profileRes] = await Promise.all([
      getAuthMenusApi(),
      getAuthPermissionsApi(),
      getProfileApi(),
    ])
    const menus = menuRes.data
    if (menus?.length) {
      userMenus.value = menus
      registerMenuRoutes(menus)
      addNotFoundRoute()
      isRoutesRegistered = true
    }
    const { useUserStore } = await import('@/stores/user')
    const userStore = useUserStore()
    userStore.setPermissions(permissionRes.data)
    if (profileRes?.data) userStore.setUser(profileRes.data)
  },
}

const routes: RouteRecordRaw[] = [
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

const router = createRouter({
  history: createWebHistory(),
  routes,
})

function addNotFoundRoute() {
  if (!router.hasRoute('CatchAll')) {
    router.addRoute({
      path: '/:pathMatch(.*)*',
      name: 'CatchAll',
      redirect: '/404',
      meta: { hidden: true },
    })
  }
}

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

  if (!isRoutesRegistered) {
    try {
      await dynamicRouteConfig.refreshRoutes()
      next({ ...to, replace: true })
      return
    } catch {
      next('/login')
      return
    }
  }

  next()
})

export default router
