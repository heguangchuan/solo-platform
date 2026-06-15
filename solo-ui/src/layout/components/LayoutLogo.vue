<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<template>
  <div class="logo" :class="{ 'is-dark': appStore.isDark }">
    <span v-if="!appStore.sidebarCollapsed">Solo 管理系统</span>
    <span v-else>S</span>
  </div>
</template>

<style scoped>
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  background-color: #304156;
  border-bottom: 1px solid rgba(0, 0, 0, 0.25);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.06);
  white-space: nowrap;
  overflow: hidden;
  transition: background-color 0.3s;
}

.logo.is-dark {
  background-color: #1d1e1f;
  border-bottom-color: rgba(0, 0, 0, 0.6);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.04);
}
</style>
