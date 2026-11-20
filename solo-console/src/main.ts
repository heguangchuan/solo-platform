import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './app/router'
import { accessDirective } from './features/access/directive'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('access', accessDirective)
app.mount('#app')
