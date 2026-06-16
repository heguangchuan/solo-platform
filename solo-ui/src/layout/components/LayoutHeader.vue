<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useTagsStore } from '@/stores/tags'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const tagsStore = useTagsStore()

const presetColors = [
  '#409eff',
  '#1890ff',
  '#52c41a',
  '#13c2c2',
  '#722ed1',
  '#eb2f96',
  '#f5222d',
  '#fa8c16',
  '#faad14',
  '#2f54eb',
]

const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item: any) => item.meta.title)
  return matched.map((item: any) => ({
    path: item.path,
    title: item.meta.title as string,
  }))
})

function handleLogout() {
  tagsStore.reset()
  userStore.logout()
  window.location.href = '/login'
}

function goProfile() {
  router.push('/profile')
}

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
    return
  }
  document.documentElement.requestFullscreen()
}
</script>

<template>
  <el-header class="layout-header" :class="{ 'is-dark': appStore.isDark }">
    <div class="header-left">
      <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
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
      <el-tooltip :content="appStore.isDark ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
        <span class="header-action" @click="appStore.toggleDark">
          <el-icon>
            <Sunny v-if="appStore.isDark" />
            <Moon v-else />
          </el-icon>
        </span>
      </el-tooltip>
      <el-tooltip content="全屏" placement="bottom">
        <span class="header-action" @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </span>
      </el-tooltip>
      <el-popover placement="bottom" :width="240" trigger="click">
        <template #reference>
          <span class="header-action" title="主题颜色">
            <el-icon><Brush /></el-icon>
          </span>
        </template>
        <div class="theme-color-panel">
          <div class="theme-color-title">主题颜色</div>
          <div class="theme-color-list">
            <span
              v-for="color in presetColors"
              :key="color"
              class="theme-color-item"
              :class="{ active: appStore.primaryColor.toLowerCase() === color.toLowerCase() }"
              :style="{ background: color }"
              @click="appStore.setPrimaryColor(color)"
            >
              <el-icon v-if="appStore.primaryColor.toLowerCase() === color.toLowerCase()"><Check /></el-icon>
            </span>
          </div>
          <div class="theme-color-custom">
            <span>自定义</span>
            <el-color-picker
              :model-value="appStore.primaryColor"
              @change="(c: string) => appStore.setPrimaryColor(c || '#409eff')"
            />
          </div>
        </div>
      </el-popover>
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
            <el-dropdown-item divided @click="goProfile">
              <el-icon style="margin-right: 6px"><UserFilled /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item @click="handleLogout">
              <el-icon style="margin-right: 6px"><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<style scoped>
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  padding: 0 20px;
}

.layout-header.is-dark {
  border-bottom-color: transparent;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

:global(html.dark) .layout-header {
  border-bottom-color: transparent;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.header-action:hover {
  color: var(--el-color-primary);
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.user-dropdown:hover {
  color: var(--el-color-primary);
}

.theme-color-panel {
  padding: 4px;
}

.theme-color-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.theme-color-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.theme-color-item {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  transition: transform 0.2s;
}

.theme-color-item:hover {
  transform: scale(1.1);
}

.theme-color-item.active {
  box-shadow: 0 0 0 2px var(--el-bg-color), 0 0 0 4px var(--el-color-primary);
}

.theme-color-custom {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
