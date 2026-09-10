<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { dynamicRouteConfig } from '@/router'
import LayoutLogo from './LayoutLogo.vue'

const route = useRoute()
const appStore = useAppStore()

const sidebarBg = computed(() => (appStore.isDark ? '#1d1e1f' : '#304156'))
const sidebarText = computed(() => (appStore.isDark ? '#cfd3dc' : '#bfcbd9'))

function extractMenuItems(): any[] {
  const menus = dynamicRouteConfig.getMenus()
  if (!menus?.length) return []
  return menus
}
</script>

<template>
  <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="layout-aside" :style="{ backgroundColor: sidebarBg }">
    <LayoutLogo v-if="appStore.logoVisible" />
    <el-menu
      :default-active="route.path"
      router
      unique-opened
      :collapse="appStore.sidebarCollapsed"
      :background-color="sidebarBg"
      :text-color="sidebarText"
      :active-text-color="appStore.primaryColor"
      class="layout-menu"
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <template v-for="menu in extractMenuItems()" :key="menu.id">
        <template v-if="!menu.children || menu.children.length === 0">
          <el-menu-item :index="menu.url">
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.permissionName }}</template>
          </el-menu-item>
        </template>
        <el-sub-menu v-else :index="String(menu.id)">
          <template #title>
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <span>{{ menu.permissionName }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.id"
            :index="child.url"
          >
            <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
            <template #title>{{ child.permissionName }}</template>
          </el-menu-item>
        </el-sub-menu>
      </template>
    </el-menu>
  </el-aside>
</template>

<style scoped>
.layout-aside {
  transition: width 0.3s, background-color 0.3s;
  overflow: hidden;
  border-right: none;
}

.layout-menu {
  border-right: none !important;
}
</style>
