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
        target: 'http://162.14.110.28:10003',
        changeOrigin: true,
        ws: true, // 支持 WebSocket 代理
      },
    },
  },
})
