<script lang="ts">
export default { name: 'Gb28181Play' }
</script>
<script setup lang="ts">
import { VideoPlay } from '@element-plus/icons-vue'
// 实时点播页占位（Sprint S2.2/S2.3 落地）
// 当前 S2.1 仅打通 ZLM hook 与节点管理；INVITE / PlayService / 播放器尚未实现，
// 这里先放占位提示，避免菜单 404。
</script>

<template>
  <el-card shadow="hover">
    <el-empty description="实时点播功能开发中（Sprint 2.2 / 2.3）">
      <template #image>
        <el-icon :size="48" color="#909399"><VideoPlay /></el-icon>
      </template>
      <div style="color: #909399; max-width: 520px; margin: 0 auto; line-height: 1.7">
        当前阶段（S2.1）仅完成 ZLM 节点对接与 hook 通道，
        SIP INVITE / SDP / Dialog 管理、PlayService 编排、前端播放器
        将在 S2.2 / S2.3 实现。
      </div>
    </el-empty>
  </el-card>
</template>
