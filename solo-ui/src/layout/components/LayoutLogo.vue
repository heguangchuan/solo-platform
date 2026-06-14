<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<template>
  <div class="logo">
    <span v-if="!appStore.sidebarCollapsed">Solo 管理系统</span>
    <span v-else>S</span>
  </div>
</template>

<style scoped>
.logo {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  background-color: #263445;
  white-space: nowrap;
  overflow: hidden;
}
</style>
