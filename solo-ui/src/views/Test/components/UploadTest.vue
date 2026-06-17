<script setup lang="ts">
import { ref } from 'vue'
import type { SysFile } from '@/types/api'

const uploadUrl = ref('')
const uploadFile = ref<SysFile | null>(null)

function resetUploadResult() {
  uploadUrl.value = ''
  uploadFile.value = null
}
</script>

<template>
  <el-card shadow="never" class="test-card">
    <template #header>通用文件上传</template>
    <FileUpload
      v-model="uploadUrl"
      v-model:file="uploadFile"
      button-text="选择文件上传"
      tip="使用自定义 FileUpload 组件测试上传、回显和移除"
      @remove="resetUploadResult"
    />

    <el-descriptions v-if="uploadFile" :column="1" border class="result-box">
      <el-descriptions-item label="文件名">{{ uploadFile.originalName }}</el-descriptions-item>
      <el-descriptions-item label="文件类型">{{ uploadFile.ext || '—' }}</el-descriptions-item>
      <el-descriptions-item label="文件大小">{{ uploadFile.size }} B</el-descriptions-item>
      <el-descriptions-item label="访问地址">{{ uploadFile.url }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="请上传一个文件" />
  </el-card>
</template>

<style scoped>
.result-box {
  margin-top: 16px;
}
</style>
