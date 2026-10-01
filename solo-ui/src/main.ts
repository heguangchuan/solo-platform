import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/element-overrides.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router, { dynamicRouteConfig } from './router'
import { useUserStore } from './stores/user'
import { setupPermissionDirective } from './directives/permission'
// Leaflet + 聚合插件样式（地图打点用，dashboard 引入）
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'

const app = createApp(App)
const pinia = createPinia()

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})
setupPermissionDirective(app)

// 恢复 token（必须在 app.use(pinia) 之后）
const userStore = useUserStore()
userStore.initFromStorage()

// 如果有 token，提前初始化路由，避免第一次跳转卡顿
if (userStore.token) {
  dynamicRouteConfig.refreshRoutes().catch(() => {
    // 初始化失败，会在路由守卫中处理
  })
}

app.mount('#app')
