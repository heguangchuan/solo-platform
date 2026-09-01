<script setup lang="ts">
/**
 * 首页：左侧设备/通道树 + 右侧全屏电子地图
 *
 * <h3>布局</h3>
 * <ul>
 *   <li>左侧 DeviceTreePanel：抽屉式，可隐藏（点地图左上角 [▶] 按钮拉出）</li>
 *   <li>右侧 MapPanel：全屏地图（抽屉在它上面浮层，关闭后地图全宽）</li>
 *   <li>点通道：DeviceTreePanel → channel-click → MapPanel.locateToChannel → flyTo + openPopup</li>
 * </ul>
 */
import { ref } from 'vue'
import { Expand } from '@element-plus/icons-vue'
import MapPanel from './components/MapPanel.vue'
import DeviceTreePanel from './components/DeviceTreePanel.vue'
import type { GbDeviceChannelVO } from '@/types/api/gb28181'
import './styles/dashboard-vars.css'

const treeVisible = ref(true)
const mapRef = ref<InstanceType<typeof MapPanel>>()

/** 设备树点通道 → 调地图 flyTo + openPopup */
function handleChannelClick(channel: GbDeviceChannelVO) {
  mapRef.value?.locateToChannel(channel)
}
</script>

<template>
  <div class="dashboard">
    <!-- 左侧抽屉 -->
    <DeviceTreePanel
      v-model:visible="treeVisible"
      @channel-click="handleChannelClick"
    />

    <!-- 地图区：始终全宽，抽屉在它上面浮层 -->
    <div class="map-area">
      <MapPanel ref="mapRef" />
      <!-- 抽屉收起后，左上角的"展开"按钮 -->
      <button
        v-if="!treeVisible"
        class="open-tree-btn"
        title="打开设备/通道树"
        @click="treeVisible = true"
      >
        <el-icon><Expand /></el-icon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  position: relative;
  /* 100vh - 96 容易因为 Layout 头部 padding/边框、body margin 多出几像素溢出 → 滚动条
     改成 100% 让父级（el-main）自己撑，撑不下的话用 max-height 卡一下保险 */
  height: 100%;
  max-height: calc(100vh - 100px);
  min-height: 480px;
  display: flex;
  overflow: hidden;
}
.map-area {
  position: relative;
  flex: 1;
  min-width: 0;  /* 防止内部 MapPanel 的 invalidateSize 出问题 */
}
.map-area :deep(.map-panel) {
  width: 100%;
  height: 100%;
}

.open-tree-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 999;
  background: rgba(10, 25, 41, 0.92);
  border: 1px solid rgba(0, 188, 212, 0.4);
  border-radius: 4px;
  color: #00E5FF;
  padding: 6px 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.4), 0 0 8px rgba(0, 188, 212, 0.15);
  transition: all 0.2s;
}
.open-tree-btn:hover {
  background: rgba(0, 188, 212, 0.15);
  border-color: rgba(0, 188, 212, 0.7);
}
</style>
