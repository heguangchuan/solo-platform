<script lang="ts">
export default { name: 'Gb28181Play' }
</script>
<script setup lang="ts">
import { VideoPlay } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-card shadow="hover">
    <el-empty description="实时点播入口">
      <template #image>
        <el-icon :size="48" color="#409eff"><VideoPlay /></el-icon>
      </template>
      <div style="color: #606266; max-width: 560px; margin: 0 auto; line-height: 1.7; text-align: center">
        <p>请在 <b>国标管理 → 通道列表</b> 中选择在线通道，点「播放」即可调起实时画面。</p>
        <p style="color: #909399; font-size: 12px; margin-top: 8px">
          统一监控墙（1×4 / 1×9 多窗口）规划在 Sprint 2.3。
        </p>
      </div>
      <el-button type="primary" :icon="VideoPlay" @click="router.push('/gb28181/device-channel')">前往通道列表</el-button>
    </el-empty>
  </el-card>
</template>
