<script lang="ts">
export default { name: 'RateLimitTest' }
</script>
<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import request from '@/utils/request'

interface TestResult {
  id: number
  status: 'success' | 'error' | 'pending'
  message: string
  timestamp: number
  data?: any
}

interface TestConfig {
  name: string
  endpoint: string
  description: string
  maxCount: number
  timeWindow: number
  concurrent: number
  interval: number
  totalRequests: number
}

const loading = ref(false)
const running = ref(false)
const requestCount = ref(0)
const successCount = ref(0)
const errorCount = ref(0)
const limitTriggered = ref(false)
const lastLimitTime = ref<number | null>(null)

const results = ref<TestResult[]>([])
const abortController = ref<AbortController | null>(null)

const configs = reactive<Record<string, TestConfig>>({
  basic: {
    name: '基础限流测试',
    endpoint: '/api/test/rate-limit/basic',
    description: '10秒内最多5次请求，适合手动点击测试',
    maxCount: 5,
    timeWindow: 10,
    concurrent: 1,
    interval: 200,
    totalRequests: 10
  },
  strict: {
    name: '严格限流测试',
    endpoint: '/api/test/rate-limit/strict',
    description: '3秒内最多2次请求，快速触发限流',
    maxCount: 2,
    timeWindow: 3,
    concurrent: 1,
    interval: 100,
    totalRequests: 5
  },
  highPressure: {
    name: '高压限流测试',
    endpoint: '/api/test/rate-limit/high-pressure',
    description: '1秒内最多100次请求，并发压力测试',
    maxCount: 100,
    timeWindow: 1,
    concurrent: 10,
    interval: 10,
    totalRequests: 150
  },
  user: {
    name: '用户级限流测试',
    endpoint: '/api/test/rate-limit/user',
    description: '按登录用户限流：10秒内最多3次',
    maxCount: 3,
    timeWindow: 10,
    concurrent: 1,
    interval: 500,
    totalRequests: 6
  },
  global: {
    name: '全局限流测试',
    endpoint: '/api/test/rate-limit/global',
    description: '全局限流：5秒内总共最多10次',
    maxCount: 10,
    timeWindow: 5,
    concurrent: 3,
    interval: 50,
    totalRequests: 20
  }
})

const activeConfig = ref<TestConfig>(configs.basic)
const activeConfigKey = ref('basic')

function selectConfig(key: string) {
  if (running.value) {
    return
  }
  activeConfigKey.value = key
  activeConfig.value = configs[key]
  resetStats()
}

function resetStats() {
  requestCount.value = 0
  successCount.value = 0
  errorCount.value = 0
  limitTriggered.value = false
  lastLimitTime.value = null
  results.value = []
}

async function sendSingleRequest(): Promise<TestResult> {
  const id = Date.now() + Math.random()
  const result: TestResult = {
    id,
    status: 'pending',
    message: '请求中...',
    timestamp: Date.now()
  }

  try {
    const response = await request.get(activeConfig.value.endpoint, {
      signal: abortController.value?.signal
    })
    result.status = 'success'
    result.message = '请求成功'
    result.data = response.data
    successCount.value++
  } catch (error: any) {
    result.status = 'error'
    if (error.response?.status === 429) {
      result.message = error.response.data?.message || '触发限流'
      limitTriggered.value = true
      lastLimitTime.value = Date.now()
    } else if (error.name === 'AbortError') {
      result.message = '已取消'
    } else {
      result.message = error.message || '请求失败'
    }
    errorCount.value++
  }

  requestCount.value++
  return result
}

async function runSequentialTest() {
  const config = activeConfig.value
  for (let i = 0; i < config.totalRequests; i++) {
    if (!running.value) break

    const result = await sendSingleRequest()
    results.value.unshift(result)

    if (i < config.totalRequests - 1 && running.value) {
      await new Promise(resolve => setTimeout(resolve, config.interval))
    }
  }
}

async function runConcurrentTest() {
  const config = activeConfig.value
  const batchSize = config.concurrent

  for (let i = 0; i < config.totalRequests; i += batchSize) {
    if (!running.value) break

    const actualBatch = Math.min(batchSize, config.totalRequests - i)
    const batch = Array.from({ length: actualBatch }, () => sendSingleRequest())

    const batchResults = await Promise.all(batch)
    results.value.unshift(...batchResults)

    if (i + actualBatch < config.totalRequests && running.value) {
      await new Promise(resolve => setTimeout(resolve, config.interval))
    }
  }
}

async function startTest() {
  if (running.value) return

  running.value = true
  loading.value = true
  abortController.value = new AbortController()

  resetStats()

  try {
    if (activeConfig.value.concurrent > 1) {
      await runConcurrentTest()
    } else {
      await runSequentialTest()
    }
  } finally {
    running.value = false
    loading.value = false
  }
}

function stopTest() {
  running.value = false
  if (abortController.value) {
    abortController.value.abort()
  }
}

function getStatusColor(status: string) {
  switch (status) {
    case 'success': return 'var(--el-color-success)'
    case 'error': return 'var(--el-color-danger)'
    default: return 'var(--el-color-warning)'
  }
}

function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString()
}

function getStatsPercentage(value: number) {
  if (requestCount.value === 0) return 0
  return Math.round((value / requestCount.value) * 100)
}

onUnmounted(() => {
  stopTest()
})
</script>

<template>
  <div class="rate-limit-test">
    <!-- 测试配置区 -->
    <div class="config-section">
      <h4>选择测试场景</h4>
      <div class="config-buttons">
        <el-button
          v-for="(config, key) in configs"
          :key="key"
          :type="activeConfigKey === key ? 'primary' : 'default'"
          :disabled="running"
          @click="selectConfig(key)"
          size="small"
        >
          {{ config.name }}
        </el-button>
      </div>

      <el-alert
        :title="activeConfig.description"
        type="info"
        :closable="false"
        show-icon
        class="config-desc"
      >
        <template #default>
          <div>
            <el-tag size="small" type="info">阈值: {{ activeConfig.maxCount }}次</el-tag>
            <el-tag size="small" type="info" style="margin-left: 8px">窗口: {{ activeConfig.timeWindow }}秒</el-tag>
            <el-tag size="small" type="warning" style="margin-left: 8px">并发: {{ activeConfig.concurrent }}</el-tag>
          </div>
        </template>
      </el-alert>
    </div>

    <!-- 操作区 -->
    <div class="action-section">
      <el-button
        type="primary"
        :loading="loading"
        :disabled="running"
        @click="startTest"
      >
        {{ running ? '测试中...' : '开始测试' }}
      </el-button>
      <el-button
        v-if="running"
        type="danger"
        @click="stopTest"
      >
        停止测试
      </el-button>
      <el-button @click="resetStats">
        重置统计
      </el-button>

      <div class="single-request">
        <el-button @click="() => sendSingleRequest().then(r => results.unshift(r))">
          单次请求测试
        </el-button>
        <span class="tip">（手动连续点击观察限流效果）</span>
      </div>
    </div>

    <!-- 统计面板 -->
    <div class="stats-panel">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">总请求数</div>
            <div class="stat-value total">{{ requestCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">成功</div>
            <div class="stat-value success">{{ successCount }} ({{ getStatsPercentage(successCount) }}%)</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">失败/限流</div>
            <div class="stat-value error">{{ errorCount }} ({{ getStatsPercentage(errorCount) }}%)</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">限流状态</div>
            <div class="stat-value" :class="{ 'limit-active': limitTriggered }">
              {{ limitTriggered ? '已触发限流 ✓' : '未触发' }}
            </div>
          </el-card>
        </el-col>
      </el-row>

      <div v-if="limitTriggered" class="limit-notice">
        <el-alert
          :title="'限流已在 ' + formatTime(lastLimitTime!) + ' 触发'"
          type="success"
          :closable="false"
          show-icon
        >
          <template #default>
            Redis 滑动窗口限流机制工作正常！
          </template>
        </el-alert>
      </div>
    </div>

    <!-- 请求日志 -->
    <div class="results-section">
      <h4>请求日志（最新在前）</h4>
      <div class="results-list" v-if="results.length > 0">
        <div
          v-for="result in results.slice(0, 50)"
          :key="result.id"
          class="result-item"
          :style="{ borderLeftColor: getStatusColor(result.status) }"
        >
          <span class="result-time">{{ formatTime(result.timestamp) }}</span>
          <span class="result-message" :style="{ color: getStatusColor(result.status) }">
            {{ result.message }}
          </span>
          <span v-if="result.data?.timestamp" class="result-detail">
            服务端响应: {{ result.data.message }}
          </span>
        </div>
      </div>
      <el-empty v-else description="暂无请求记录" :image-size="100" />
    </div>
  </div>
</template>

<style scoped>
.rate-limit-test {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-section h4 {
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

.config-desc {
  margin-top: 4px;
}

.action-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.single-request {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.single-request .tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
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

.stat-value.limit-active {
  color: var(--el-color-success);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.limit-notice {
  margin-top: 8px;
}

.results-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.results-section h4 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
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

.result-time {
  color: var(--el-text-color-secondary);
  font-family: monospace;
  font-size: 12px;
}

.result-message {
  font-weight: 500;
}

.result-detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
