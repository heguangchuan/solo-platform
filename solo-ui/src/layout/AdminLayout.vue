<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LayoutSidebar from './components/LayoutSidebar.vue'
import LayoutHeader from './components/LayoutHeader.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

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
      <el-main class="layout-main">
        <router-view />
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
.layout-container {
  height: 100vh;
  width: 100vw;
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
