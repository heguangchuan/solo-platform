<script setup lang="ts">
/**
 * 首页：全屏电子地图
 *
 * <h3>布局</h3>
 * <ul>
 *   <li>整页只渲染 {@link MapPanel}，占满视口（扣除顶部 header）</li>
 *   <li>地图配置从 {@code GET /api/dashboard/map-config} 拉（application.yaml 的 {@code map.*} 段）</li>
 *   <li>通道 marker 数据从 {@code GET /api/gb28181/device-channel/map-markers} 拉，按当前视口过滤</li>
 * </ul>
 */
import MapPanel from './components/MapPanel.vue'
import './styles/dashboard-vars.css'
</script>

<template>
  <div class="dashboard">
    <MapPanel />
  </div>
</template>

<style scoped>
.dashboard {
  /* 视口高度 - 顶部 layout header / padding，留给地图足够空间 */
  height: calc(100vh - 96px);
  min-height: 480px;
  display: flex;
}
.dashboard :deep(.map-panel) {
  flex: 1;
}
</style>
