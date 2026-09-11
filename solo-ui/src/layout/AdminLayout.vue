<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LayoutSidebar from './components/LayoutSidebar.vue'
import LayoutHeader from './components/LayoutHeader.vue'
import LayoutTagsView from './components/LayoutTagsView.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { useUserStore } from '@/stores/user'
import { useTagsStore } from '@/stores/tags'
import { useMessageStore } from '@/stores/message'

const userStore = useUserStore()
const tagsStore = useTagsStore()
const router = useRouter()

// 页面加载时初始化消息中心（刷新页面自动重连 WebSocket）
onMounted(() => {
  if (userStore.token) {
    const messageStore = useMessageStore()
    messageStore.init()
  }
})

// 强制改密弹窗：v-model 必须可写，因此通过 computed 同步到 store
const forceChangeVisible = computed({
  get: () => userStore.pwdResetRequired,
  set: (v) => {
    if (!v) userStore.clearPwdResetRequired()
    else userStore.pwdResetRequired = true
  },
})

function onPasswordChanged() {
  // 改密成功：踢回登录页用新密码登录
  userStore.logout()
  ElMessage.info('密码已更新，请使用新密码重新登录')
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout-container" direction="horizontal">
    <LayoutSidebar />
    <el-container class="layout-body" direction="vertical">
      <LayoutHeader />
      <LayoutTagsView />
      <el-main class="layout-main">
        <router-view v-slot="{ Component, route }">
          <keep-alive :include="tagsStore.cachedNames">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
    <ChangePasswordDialog
      v-model="forceChangeVisible"
      forced
      @success="onPasswordChanged"
    />
  </el-container>
</template>

<style scoped>
/* 用 100% 而不是 100vw：100vw 包含纵向滚动条宽度，容易引发横向滚动条 */
.layout-container {
  height: 100%;
  width: 100%;
}

.layout-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.layout-main {
  background-color: var(--el-bg-color-page);
  padding: 20px;
}
</style>
