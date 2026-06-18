<script lang="ts">
export default { name: 'RepeatSubmitTest' }
</script>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import request from '@/utils/request'

interface TestResult {
  id: number
  status: 'success' | 'error'
  message: string
  timestamp: number
  data?: any
}

interface TestConfig {
  name: string
  endpoint: string
  description: string
  expire: number
  concurrent: number
  delayMs: number
  mutateParams: boolean
}

const configs = reactive<Record<string, TestConfig>>({
  quickUnlock: {
    name: '执行中防重',
    endpoint: '/api/test/repeat-submit/quick-unlock',
    description: '锁在接口执行完成后释放，并发请求中只允许一个执行。',
    expire: 3,
    concurrent: 5,
    delayMs: 1200,
    mutateParams: false
  },
  fixedWindow: {
    name: '固定窗口防重',
    endpoint: '/api/test/repeat-submit/fixed-window',
    description: '接口返回后不立即释放锁，5秒内重复提交会被拦截。',
    expire: 5,
    concurrent: 5,
    delayMs: 100,
    mutateParams: false
  },
  includeParamsSame: {
    name: '参数级-相同参数',
    endpoint: '/api/test/repeat-submit/include-params',
    description: '同一接口相同参数5秒内重复提交会被拦截。',
    expire: 5,
    concurrent: 5,
    delayMs: 100,
    mutateParams: false
  },
  includeParamsDifferent: {
    name: '参数级-不同参数',
    endpoint: '/api/test/repeat-submit/include-params',
    description: '同一接口不同参数使用不同锁 key，可以同时提交成功。',
    expire: 5,
    concurrent: 5,
    delayMs: 100,
    mutateParams: true
  },
  global: {
    name: '全局防重',
    endpoint: '/api/test/repeat-submit/global',
    description: '不区分用户/IP，接口全局5秒内只允许提交一次。',
    expire: 5,
    concurrent: 5,
    delayMs: 100,
    mutateParams: false
  }
})

const activeConfigKey = ref('quickUnlock')
const activeConfig = ref<TestConfig>(configs.quickUnlock)
const running = ref(false)
const requestCount = ref(0)
const successCount = ref(0)
const repeatCount = ref(0)
const results = ref<TestResult[]>([])
const form = reactive({
  bizNo: 'ORDER-TEST-001',
  content: '重复提交测试表单'
})

function selectConfig(key: string) {
  if (running.value) return
  activeConfigKey.value = key
  activeConfig.value = configs[key]
  resetStats()
}

function resetStats() {
  requestCount.value = 0
  successCount.value = 0
  repeatCount.value = 0
  results.value = []
}

function buildPayload(index: number) {
  const config = activeConfig.value
  return {
    bizNo: config.mutateParams ? `${form.bizNo}-${index}` : form.bizNo,
    content: form.content,
    delayMs: config.delayMs
  }
}

async function sendRequest(index = 0): Promise<TestResult> {
  const result: TestResult = {
    id: Date.now() + index + Math.random(),
    status: 'success',
    message: '提交成功',
    timestamp: Date.now()
  }

  try {
    const response = await request.post(activeConfig.value.endpoint, buildPayload(index))
    result.data = response.data
    successCount.value++
  } catch (error: any) {
    result.status = 'error'
    result.message = error.response?.data?.message || error.message || '提交失败'
    repeatCount.value++
  }

  requestCount.value++
  return result
}

async function submitOnce() {
  if (running.value) return
  running.value = true
  try {
    const result = await sendRequest(0)
    results.value.unshift(result)
  } finally {
    running.value = false
  }
}

async function submitConcurrent() {
  if (running.value) return
  running.value = true
  resetStats()
  try {
    const tasks = Array.from({ length: activeConfig.value.concurrent }, (_, index) => sendRequest(index))
    const batchResults = await Promise.all(tasks)
    results.value.unshift(...batchResults)
  } finally {
    running.value = false
  }
}

function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString()
}

function getStatsPercentage(value: number) {
  if (requestCount.value === 0) return 0
  return Math.round((value / requestCount.value) * 100)
}
</script>

<template>
  <div class="repeat-submit-test">
    <div class="config-section">
      <h4>选择测试场景</h4>
      <div class="config-buttons">
        <el-button
          v-for="(config, key) in configs"
          :key="key"
          :type="activeConfigKey === key ? 'primary' : 'default'"
          :disabled="running"
          size="small"
          @click="selectConfig(key)"
        >
          {{ config.name }}
        </el-button>
      </div>

      <el-alert :title="activeConfig.description" type="info" :closable="false" show-icon>
        <template #default>
          <el-tag size="small" type="info">锁过期: {{ activeConfig.expire }}秒</el-tag>
          <el-tag size="small" type="warning" style="margin-left: 8px">并发: {{ activeConfig.concurrent }}</el-tag>
          <el-tag size="small" type="success" style="margin-left: 8px">延迟: {{ activeConfig.delayMs }}ms</el-tag>
        </template>
      </el-alert>
    </div>

    <el-form label-width="100px" class="form-section">
      <el-form-item label="业务编号">
        <el-input v-model="form.bizNo" :disabled="running" style="width: 320px" />
      </el-form-item>
      <el-form-item label="表单内容">
        <el-input v-model="form.content" :disabled="running" style="width: 420px" />
      </el-form-item>
    </el-form>

    <div class="action-section">
      <el-button type="primary" :loading="running" @click="submitConcurrent">
        并发提交测试
      </el-button>
      <el-button :disabled="running" @click="submitOnce">
        单次提交
      </el-button>
      <el-button :disabled="running" @click="resetStats">
        重置统计
      </el-button>
      <span class="tip">固定窗口类场景连续测试前请等待锁过期，否则新一轮提交也会被正常拦截。</span>
    </div>

    <div class="stats-panel">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">总提交数</div>
            <div class="stat-value total">{{ requestCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">成功</div>
            <div class="stat-value success">{{ successCount }} ({{ getStatsPercentage(successCount) }}%)</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">防重拦截</div>
            <div class="stat-value error">{{ repeatCount }} ({{ getStatsPercentage(repeatCount) }}%)</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div class="results-section">
      <h4>提交日志（最新在前）</h4>
      <div v-if="results.length > 0" class="results-list">
        <div
          v-for="result in results.slice(0, 50)"
          :key="result.id"
          class="result-item"
          :class="result.status"
        >
          <span class="result-time">{{ formatTime(result.timestamp) }}</span>
          <span class="result-message">{{ result.message }}</span>
          <span v-if="result.data?.timestamp" class="result-detail">
            服务端响应: {{ result.data.message }} / {{ result.data.bizNo }}
          </span>
        </div>
      </div>
      <el-empty v-else description="暂无提交记录" :image-size="100" />
    </div>
  </div>
</template>

<style scoped>
.repeat-submit-test {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-section h4,
.results-section h4 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
}

.config-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.form-section {
  padding: 12px 0 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.action-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stats-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-card {
  text-align: center;
  border: 1px solid var(--el-border-color-lighter);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
}

.stat-value.total {
  color: var(--el-color-primary);
}

.stat-value.success {
  color: var(--el-color-success);
}

.stat-value.error {
  color: var(--el-color-danger);
}

.results-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.results-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-bg-color-page);
  border-left: 3px solid var(--el-border-color);
  border-radius: 0 4px 4px 0;
  font-size: 13px;
}

.result-item.success {
  border-left-color: var(--el-color-success);
}

.result-item.error {
  border-left-color: var(--el-color-danger);
}

.result-time {
  color: var(--el-text-color-secondary);
  font-family: monospace;
  font-size: 12px;
}

.result-message {
  font-weight: 500;
}

.result-item.success .result-message {
  color: var(--el-color-success);
}

.result-item.error .result-message {
  color: var(--el-color-danger);
}

.result-detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
