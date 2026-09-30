<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<template>
  <div class="logo" :class="{ 'is-dark': appStore.isDark }">
    <span v-if="!appStore.sidebarCollapsed">Solo 视频监控平台</span>
    <span v-else>S</span>
  </div>
</template>

<style scoped>
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-color-primary);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
  background-color: #ffffff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  white-space: nowrap;
  overflow: hidden;
  transition: background-color 0.3s, color 0.3s;
}

.logo.is-dark {
  background-color: #141414;
  border-bottom-color: var(--el-border-color);
}
</style>
