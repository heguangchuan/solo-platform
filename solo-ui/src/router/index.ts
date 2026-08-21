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

/** 获取菜单的第一个可跳转的子菜单url（递归查找） */
function getFirstChildUrl(menu: any): string | null {
  if (!menu.children || menu.children.length === 0) return null
  for (const child of menu.children) {
    if (child.url) return child.url
    const childUrl = getFirstChildUrl(child)
    if (childUrl) return childUrl
  }
  return null
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
    // 先递归处理子菜单
    if (menu.children?.length) {
      registerMenuRoutes(menu.children)
    }

    if (!menu.url) {
      return
    }

    const routePath = menu.url.replace(/^\//, '')
    const routeName = generateRouteName(routePath)

    if (router.hasRoute(routeName)) {
      return
    }

    // 如果是有子菜单的父菜单，优先检查是否有对应组件，没有的话重定向到第一个子菜单
    if (menu.children?.length) {
      const segments = menu.url.replace(/^\//, '').split('/').filter(Boolean).map(toPascalSegment)
      const targetPath = `/src/views/${segments.join('/')}/index.vue`

      // 如果存在对应组件，就注册为普通路由
      if (viewModules[targetPath]) {
        router.addRoute(mainRouteName, {
          path: routePath,
          name: routeName,
          component: viewModules[targetPath],
          meta: {
            title: menu.permissionName,
            icon: menu.icon,
            parentId: menu.parentId,
          },
        })
      } else {
        // 不存在组件，尝试重定向到第一个子菜单
        const firstChildUrl = getFirstChildUrl(menu)
        if (firstChildUrl) {
          router.addRoute(mainRouteName, {
            path: routePath,
            name: routeName,
            redirect: firstChildUrl,
            meta: {
              title: menu.permissionName,
              icon: menu.icon,
              parentId: menu.parentId,
            },
          })
        }
      }
      return
    }

    // 没有子菜单的普通菜单，加载组件
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
  })
}

export const userMenus = ref<any[]>([])
let isRoutesRegistered = false
let isRefreshingRoutes = false // 路由刷新锁，防止并发调用

export const dynamicRouteConfig = {
  registerAll(routes: any[]) {
    // 如果正在刷新路由，等待刷新完成后再注册，最多等待30秒
    if (isRefreshingRoutes) {
      const startTime = Date.now()
      const interval = setInterval(() => {
        if (!isRefreshingRoutes) {
          clearInterval(interval)
          this.registerAll(routes)
        } else if (Date.now() - startTime > 30000) {
          clearInterval(interval)
          console.error('路由注册超时')
        }
      }, 50)
      return
    }

    userMenus.value = routes
    registerMenuRoutes(routes)
    addNotFoundRoute()
    isRoutesRegistered = true
  },
  clear() {
    userMenus.value = []
    isRoutesRegistered = false
    isRefreshingRoutes = false
  },
  getMenus() {
    return userMenus.value
  },
  async refreshRoutes() {
    if (isRoutesRegistered) return
    if (isRefreshingRoutes) {
      // 如果正在刷新，等待刷新完成，最多等待30秒
      await new Promise((resolve, reject) => {
        const startTime = Date.now()
        const interval = setInterval(() => {
          if (isRoutesRegistered) {
            clearInterval(interval)
            resolve(true)
          } else if (Date.now() - startTime > 30000) {
            clearInterval(interval)
            reject(new Error('路由刷新超时'))
          }
        }, 50)
      })
      return
    }

    isRefreshingRoutes = true
    try {
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
    } finally {
      isRefreshingRoutes = false
    }
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
      // 动态注册路由后，需要重新触发路由匹配，replace: true 避免重复历史记录
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
