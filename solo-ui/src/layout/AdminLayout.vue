<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { dynamicRouteConfig } from '@/router'

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

function handleLogout() {
  userStore.logout()
  window.location.href = '/login'
}

// 面包屑
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item: any) => item.meta.title)
  return matched.map((item: any) => ({
    path: item.path,
    title: item.meta.title as string,
  }))
})

// 从动态路由中提取菜单项（排除首页）
function extractMenuItems(): any[] {
  const mainRoute = dynamicRouteConfig.getMenus()
  if (!mainRoute?.length) return []
  return mainRoute
}
</script>

<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!appStore.sidebarCollapsed">Solo 管理系统</span>
        <span v-else>S</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        :collapse="appStore.sidebarCollapsed"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <!-- 首页 -->
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <!-- 动态菜单 -->
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

    <!-- 主区域 -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            @click="appStore.toggleSidebar"
          >
            <Expand v-if="appStore.sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-dropdown">
              <el-icon><Avatar /></el-icon>
              <span>{{ userStore.user?.nickname || userStore.user?.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ userStore.user?.username }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

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

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
}

.user-dropdown:hover {
  color: #409eff;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
