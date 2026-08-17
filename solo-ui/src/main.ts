import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/element-overrides.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import { setupPermissionDirective } from './directives/permission'
// ECharts 按需注册（必须先于 VChart 使用，否则 vue-echarts 找不到 chart）
import './views/Dashboard/echarts-setup'

const app = createApp(App)
const pinia = createPinia()

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 全局注册 VChart（仪表盘的 sparkline / 折线 / 环形图都用它）
app.component('VChart', VChart)

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})
setupPermissionDirective(app)

// 恢复 token（必须在 app.use(pinia) 之后）
const userStore = useUserStore()
userStore.initFromStorage()

app.mount('#app')
