<script setup lang="ts">
/**
 * 云台预置位管理弹窗
 * <p>对指定通道进行预置位增删改查管理，集成云台控制面板方便调准位置后新增。</p>
 */
import { ref } from 'vue'
import type { GbPtzPreset } from '@/types/api'

const visible = ref(false)

function open() {
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="预置位管理"
    width="780px"
    top="8vh"
    :close-on-click-modal="false"
  >
    <div>预留位置，后续开发完成</div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>
