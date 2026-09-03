<script lang="ts">
export default { name: 'TestComponent' }
</script>
<script setup lang="ts">
import { ref } from 'vue'
import UploadTest from '@/views/Test/components/UploadTest.vue'
import DictTest from '@/views/Test/components/DictTest.vue'
import MessageTest from '@/views/Test/components/MessageTest.vue'

const activeTab = ref('upload')
</script>

<template>
  <div>
    <el-card shadow="hover" style="margin-bottom: 16px">
      <div style="display: flex; align-items: center; gap: 12px">
        <h2 style="margin: 0; font-size: 18px">组件测试</h2>
        <el-tag type="primary" size="small">前端 Vue / Element Plus 组件</el-tag>
      </div>
      <p style="margin: 8px 0 0; color: var(--el-text-color-secondary)">
        验证封装的通用组件：上传、字典回显、消息提示。
      </p>
    </el-card>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="上传组件测试" name="upload">
          <UploadTest />
        </el-tab-pane>
        <el-tab-pane label="字典组件测试" name="dict">
          <DictTest />
        </el-tab-pane>
        <el-tab-pane label="消息功能测试" name="message">
          <MessageTest />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
