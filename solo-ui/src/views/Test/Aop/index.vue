<script lang="ts">
export default { name: 'TestAop' }
</script>
<script setup lang="ts">
import { ref } from 'vue'
import RateLimitTest from '@/views/Test/components/RateLimitTest.vue'
import RepeatSubmitTest from '@/views/Test/components/RepeatSubmitTest.vue'
import XssTest from '@/views/Test/components/XssTest.vue'

const activeTab = ref('rateLimit')
</script>

<template>
  <div>
    <el-card shadow="hover" style="margin-bottom: 16px">
      <div style="display: flex; align-items: center; gap: 12px">
        <h2 style="margin: 0; font-size: 18px">AOP测试</h2>
        <el-tag type="warning" size="small">后端 @RateLimiter / @NoRepeatSubmit / XSS</el-tag>
      </div>
      <p style="margin: 8px 0 0; color: var(--el-text-color-secondary)">
        验证后端切面防护：限流、防重复提交、XSS 注入。
      </p>
    </el-card>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="限流功能测试" name="rateLimit">
          <RateLimitTest />
        </el-tab-pane>
        <el-tab-pane label="重复提交测试" name="repeatSubmit">
          <RepeatSubmitTest />
        </el-tab-pane>
        <el-tab-pane label="XSS注入测试" name="xss">
          <XssTest />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
