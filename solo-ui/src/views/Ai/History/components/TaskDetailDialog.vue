<script setup lang="ts">
import { ref } from 'vue'
import type { AiGenerationTask } from '@/api/ai'

const visible = ref(false)
const task = ref<AiGenerationTask | null>(null)

const getStatusTagType = (status: string) => {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'PROCESSING':
      return 'warning'
    case 'PENDING':
      return 'info'
    default:
      return ''
  }
}

const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消'
  }
  return statusMap[status] || status
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const open = (row: AiGenerationTask) => {
  task.value = row
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="任务详情" width="700px">
    <el-descriptions v-if="task" :column="2" border>
      <el-descriptions-item label="任务ID" :span="2">{{ task.id }}</el-descriptions-item>
      <el-descriptions-item label="任务类型">
        <el-tag size="small">{{ task.taskType }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="getStatusTagType(task.status)" size="small">
          {{ getStatusText(task.status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="使用模型" :span="2">{{ task.modelName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="消耗Token">{{ task.tokens || '-' }}</el-descriptions-item>
      <el-descriptions-item label="成本">{{ task.cost ? '¥' + task.cost.toFixed(4) : '-' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间" :span="2">{{ formatTime(task.createTime) }}</el-descriptions-item>
      <el-descriptions-item label="开始时间" :span="2">{{ formatTime(task.startTime) }}</el-descriptions-item>
      <el-descriptions-item label="完成时间" :span="2">{{ formatTime(task.finishTime) }}</el-descriptions-item>
      <el-descriptions-item label="输入内容" :span="2">
        <div style="max-height: 120px; overflow-y: auto; white-space: pre-wrap;">
          {{ task.prompt }}
        </div>
      </el-descriptions-item>
      <el-descriptions-item v-if="task.resultContent" label="输出结果" :span="2">
        <div style="max-height: 300px; overflow-y: auto; white-space: pre-wrap;">
          {{ task.resultContent }}
        </div>
      </el-descriptions-item>
      <el-descriptions-item v-if="task.errorMsg" label="错误信息" :span="2">
        <div style="color: #f56c6c; max-height: 120px; overflow-y: auto;">
          {{ task.errorMsg }}
        </div>
      </el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
