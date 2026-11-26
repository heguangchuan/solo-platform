import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceLayout from '@/layouts/WorkspaceLayout.vue'
import { useAuthStore } from '@/stores/auth'

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
        { path: '/gb28181/device', name: 'gb-device', component: () => import('@/pages/gb28181/device/index.vue') },
        { path: '/gb28181/device-channel', name: 'gb-channel', component: () => import('@/pages/gb28181/device-channel/index.vue') },
        { path: '/gb28181/platform', name: 'gb-platform', component: () => import('@/pages/gb28181/platform/index.vue') },
        { path: '/gb28181/alarm', name: 'gb-alarm', component: () => import('@/pages/gb28181/alarm/index.vue') },
        { path: '/gb28181/position', name: 'gb-position', component: () => import('@/pages/gb28181/position/index.vue') },
        { path: '/gb28181/cloud-record', name: 'gb-cloud-record', component: () => import('@/pages/gb28181/cloud-record/index.vue') },
        { path: '/gb28181/broadcast', name: 'gb-broadcast', component: () => import('@/pages/gb28181/broadcast/index.vue') },
        { path: '/gb28181/view-record', name: 'gb-view-record', component: () => import('@/pages/gb28181/view-record/index.vue') },
        { path: '/onvif/device', name: 'onvif-device', component: () => import('@/pages/onvif/device/index.vue') },
        { path: '/media/server', name: 'media-server', component: () => import('@/pages/media/server/index.vue') },
        { path: '/media/stream-proxy', name: 'media-stream-proxy', component: () => import('@/pages/media/stream-proxy/index.vue') },
        { path: '/media/stream-push', name: 'media-stream-push', component: () => import('@/pages/media/stream-push/index.vue') },
        { path: '/openapi/app', name: 'openapi-app', component: () => import('@/pages/openapi/app/index.vue') },
        { path: '/openapi/log', name: 'openapi-log', component: () => import('@/pages/openapi/log/index.vue') },
        { path: '/system/user', name: 'system-user', component: () => import('@/pages/system/user/index.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.isLogin) {
    return to.name === 'login' ? true : { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login') return { name: 'home' }
  return true
})

export default router
