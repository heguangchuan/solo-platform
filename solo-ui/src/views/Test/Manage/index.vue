<script lang="ts">
export default { name: 'TestManage' }
</script>
<script setup lang="ts">
import { ref } from 'vue'
import UploadTest from '@/views/Test/components/UploadTest.vue'
import DictTest from '@/views/Test/components/DictTest.vue'
import MessageTest from '@/views/Test/components/MessageTest.vue'

const activeTab = ref('upload')
</script>

<template>
  <div class="test-manage">
    <el-card shadow="hover" class="overview-card">
      <div class="page-header">
        <div>
          <h2>测试管理</h2>
          <p>用于集中验证前端自定义组件，包含上传、字典、消息等功能测试。</p>
        </div>
        <el-tag type="primary">组件功能测试</el-tag>
      </div>
    </el-card>

    <el-card shadow="hover" class="content-card">
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

<style scoped>
.test-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-card {
  margin-bottom: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.page-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.content-card {
  min-height: 360px;
}

.test-card {
  min-height: 260px;
}
</style>
