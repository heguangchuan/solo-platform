import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ref } from 'vue'
import { getAuthMenusApi, getAuthPermissionsApi, getProfileApi } from '@/api/auth'

const mainRouteName = 'Main'

/**
 * 约定式视图加载：
 *
 * 通过 import.meta.glob 把 `views/**​/index.vue` 全部纳入；菜单的 url 直接映射到目录路径。
 * 例如菜单 url = '/system/user'，对应 'src/views/System/User/index.vue'。
 *
 * 这样新增菜单只需要：
 *   1) 在 sys_permission 里加一条数据，url 写正确
 *   2) 按 PascalCase 在 views 下放好同名目录的 index.vue
 *
 * 不再需要在前端代码里维护"菜单 → 组件"的映射表。
 */
const viewModules = import.meta.glob('@/views/**/index.vue')

/** 把 url 段（lower-case 或 kebab-case）转成 PascalCase 目录名，与 views 下的命名约定保持一致 */
function toPascalSegment(seg: string): string {
  return seg
    .split('-')
    .filter(Boolean)
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join('')
}

/** 根据菜单 url 查找对应的 view 组件，找不到时落到 404 */
function resolveViewComponent(url: string): () => Promise<unknown> {
  const segments = url.replace(/^\//, '').split('/').filter(Boolean).map(toPascalSegment)
  if (!segments.length) return () => import('@/views/Error/404.vue')
  const target = `/src/views/${segments.join('/')}/index.vue`
  const loader = viewModules[target]
  if (loader) return loader as () => Promise<unknown>
  // eslint-disable-next-line no-console
  console.warn(`[router] 未找到菜单视图组件：url="${url}"，期望路径："${target}"`)
  return () => import('@/views/Error/404.vue')
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
        component: resolveViewComponent(menu.url),
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
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile/index.vue'),
        // hidden: true 让侧边栏不展示，仅作为头像下拉的跳转目标
        meta: { title: '个人中心', icon: 'UserFilled', hidden: true },
      },
      {
        // 用于 Tag 刷新等"先跳走再跳回"的场景，自身不进 Tag
        path: 'redirect',
        name: 'Redirect',
        component: () => import('@/views/Redirect/index.vue'),
        meta: { hidden: true },
      },
      {
        path: 'system/server/terminal',
        name: 'SystemServerTerminal',
        component: () => import('@/views/System/Server/Terminal/index.vue'),
        meta: { title: 'Web 终端', icon: 'Monitor', hidden: true },
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
