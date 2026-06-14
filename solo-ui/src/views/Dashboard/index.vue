<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getStatsApi } from '@/api/auth'

const stats = ref({ userCount: 0, roleCount: 0, permissionCount: 0 })

onMounted(async () => {
  try {
    const res = await getStatsApi()
    stats.value = res.data
  } catch {
    // 统计接口可能需要特定权限，失败不影响页面
  }
})
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="用户总数" :value="stats.userCount" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="角色总数" :value="stats.roleCount" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="权限总数" :value="stats.permissionCount" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" style="margin-top: 20px">
      <template #header>
        <span>欢迎使用 Solo 后台管理系统</span>
      </template>
      <p class="welcome-text">
        这是一个基于 Vue 3、Vite、TypeScript、Element Plus 构建的后台管理系统前端。
        后端采用 Spring Boot + MyBatis-Plus 实现 RBAC 权限管理。
      </p>
      <el-divider />
      <el-space wrap>
        <el-tag type="success">Vue 3</el-tag>
        <el-tag type="primary">Vite</el-tag>
        <el-tag type="warning">TypeScript</el-tag>
        <el-tag type="danger">Element Plus</el-tag>
        <el-tag>Pinia</el-tag>
        <el-tag>Vue Router</el-tag>
        <el-tag type="info">Axios</el-tag>
      </el-space>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 0;
}

.welcome-text {
  color: #606266;
  line-height: 1.8;
}
</style>
