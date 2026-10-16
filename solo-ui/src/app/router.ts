import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceLayout from '@/layouts/WorkspaceLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { registerDynamicRoutes, resetDynamicRoutes } from '@/app/dynamic-routes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue') },
    {
      path: '/',
      name: 'workspace',
      component: WorkspaceLayout,
      children: [
        { path: '', name: 'home', component: () => import('@/pages/HomePage.vue') },
        { path: '/profile', name: 'profile', component: () => import('@/pages/ProfilePage.vue') },
        { path: '/messages', name: 'messages', component: () => import('@/pages/MessagesPage.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!auth.isLogin) {
    resetDynamicRoutes()
    return to.name === 'login' ? true : { name: 'login', query: { redirect: to.fullPath } }
  }

  // 已登录但菜单尚未加载（刷新页面的场景）：先拉取菜单，再注册动态路由并重新导航
  if (auth.menus.length === 0) {
    await auth.loadProfile()
    registerDynamicRoutes(router, auth.menus)
    return { ...to, replace: true }
  }

  // 登录后首次跳转：菜单已在内存，但动态路由尚未注册
  registerDynamicRoutes(router, auth.menus)

  if (to.name === 'login') return { name: 'home' }
  return true
})

export default router
