import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: true, // 等价于 0.0.0.0，监听所有网卡，支持局域网 IP 访问
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:10003',
        changeOrigin: true,
        ws: true, // 支持 WebSocket 代理
      },
      // 地图瓦片代理：高德公开瓦片 URL 没有 CORS 头，浏览器同源策略下 Leaflet 加载失败。
      // 把瓦片请求代理到本地，再由 Vite 转发到高德，等同源请求，绕开 CORS。
      // 生产部署用 nginx 配同样的 location 转发即可。
      '/tiles': {
        target: 'http://webrd01.is.autonavi.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/tiles/, ''),
      },
    },
  },
})
