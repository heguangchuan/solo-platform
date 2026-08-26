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
      // 地图瓦片不需要 Vite proxy——后端按 tile-mode 二选一返回不同 tileUrl：
      //   - offline：tileUrl = /tiles/{z}/{x}/{y}.png，Vite 直接从 public/tiles/... 静态服务
      //   - online：tileUrl = https://webrd01.is.autonavi.com/...，客户端直接拉外网
      // 切换在 application.yaml 改 tile-mode + 重启后端，前端无感
      //
      // CORS 走不通的话再考虑加 proxy（Vite 4+ 启用 server.proxy['/tiles']，
      // 但这又把"在线"变成"服务器代前端拉外网"，跟 tileMode 含义冲突了，
      // 真要 CORS 兜底建议直接改用 nginx 部署的 proxy_pass，部署侧统一处理）
    },
  },
})
