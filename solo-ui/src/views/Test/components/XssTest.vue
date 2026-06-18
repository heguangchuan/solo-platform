<script lang="ts">
export default { name: 'XssTest' }
</script>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useUserStore } from '@/stores/user'

interface TestResult {
  id: number
  name: string
  status: 'success' | 'error'
  message: string
  timestamp: number
  input?: any
  output?: any
  headers?: Record<string, string>
}

const SCRIPT_PAYLOAD = '<scr' + 'ipt>alert("title")</scr' + 'ipt>测试标题'
const CONTENT_PAYLOAD = '<img src=x onerror=alert("content")>测试内容<b>加粗</b>'
const KEYWORD_PAYLOAD = '<svg onload=alert("keyword")>搜索词'
const REMARK_PAYLOAD = 'javascript:alert("remark")'
const NESTED_PAYLOAD = '<iframe src="javascript:alert(1)"></iframe>嵌套内容'
const TAG_PAYLOAD = '<a href="javascript:alert(1)">危险链接</a>'

const userStore = useUserStore()
const loading = ref(false)
const results = ref<TestResult[]>([])
const form = reactive({
  title: SCRIPT_PAYLOAD,
  content: CONTENT_PAYLOAD,
  keyword: KEYWORD_PAYLOAD,
  remark: REMARK_PAYLOAD,
  nested: NESTED_PAYLOAD,
  tag: TAG_PAYLOAD
})

function requestConfig() {
  return {
    headers: {
      Authorization: userStore.token ? `Bearer ${userStore.token}` : '',
      'X-Client-Source': 'web'
    }
  }
}

function pickSecurityHeaders(headers: any) {
  return {
    'content-security-policy': headers?.['content-security-policy'] || '',
    'x-content-type-options': headers?.['x-content-type-options'] || '',
    'x-frame-options': headers?.['x-frame-options'] || '',
    'referrer-policy': headers?.['referrer-policy'] || ''
  }
}

function addResult(result: TestResult) {
  results.value.unshift(result)
}

async function testBody() {
  const payload = {
    title: form.title,
    content: form.content,
    nested: {
      value: form.nested
    },
    tags: [form.tag]
  }
  try {
    const response = await axios.post('/api/test/xss/body', payload, requestConfig())
    addResult({
      id: Date.now() + Math.random(),
      name: '请求体清洗测试',
      status: 'success',
      message: '请求体已返回清洗结果',
      timestamp: Date.now(),
      input: payload,
      output: response.data?.data,
      headers: pickSecurityHeaders(response.headers)
    })
  } catch (error: any) {
    addResult({
      id: Date.now() + Math.random(),
      name: '请求体清洗测试',
      status: 'error',
      message: error.message || '测试失败',
      timestamp: Date.now(),
      input: payload
    })
  }
}

async function testParam() {
  const input = {
    keyword: form.keyword,
    remark: form.remark
  }
  try {
    const response = await axios.get('/api/test/xss/param', { params: input, ...requestConfig() })
    addResult({
      id: Date.now() + Math.random(),
      name: '查询参数清洗测试',
      status: 'success',
      message: '查询参数已返回清洗结果',
      timestamp: Date.now(),
      input,
      output: response.data?.data,
      headers: pickSecurityHeaders(response.headers)
    })
  } catch (error: any) {
    addResult({
      id: Date.now() + Math.random(),
      name: '查询参数清洗测试',
      status: 'error',
      message: error.message || '测试失败',
      timestamp: Date.now(),
      input
    })
  }
}

async function runAll() {
  if (loading.value) return
  loading.value = true
  results.value = []
  try {
    await testBody()
    await testParam()
  } finally {
    loading.value = false
  }
}

function resetPayloads() {
  form.title = SCRIPT_PAYLOAD
  form.content = CONTENT_PAYLOAD
  form.keyword = KEYWORD_PAYLOAD
  form.remark = REMARK_PAYLOAD
  form.nested = NESTED_PAYLOAD
  form.tag = TAG_PAYLOAD
}

function formatJson(value: any) {
  return JSON.stringify(value, null, 2)
}

function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString()
}
</script>

<template>
  <div class="xss-test">
    <el-alert
      title="本页面只用文本方式展示 payload，不使用 v-html；如果脚本没有弹窗且返回内容被清洗，说明防护生效。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-card shadow="never" class="test-card">
      <template #header>测试 Payload</template>
      <el-form label-width="110px">
        <el-form-item label="标题字段">
          <el-input v-model="form.title" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="内容字段">
          <el-input v-model="form.content" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="查询 keyword">
          <el-input v-model="form.keyword" />
        </el-form-item>
        <el-form-item label="查询 remark">
          <el-input v-model="form.remark" />
        </el-form-item>
        <el-form-item label="嵌套字段">
          <el-input v-model="form.nested" />
        </el-form-item>
        <el-form-item label="数组字段">
          <el-input v-model="form.tag" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="runAll">
            执行全部测试
          </el-button>
          <el-button :disabled="loading" @click="testBody">
            请求体测试
          </el-button>
          <el-button :disabled="loading" @click="testParam">
            参数测试
          </el-button>
          <el-button :disabled="loading" @click="resetPayloads">
            重置 Payload
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="test-card">
      <template #header>测试结果</template>
      <div v-if="results.length > 0" class="result-list">
        <div v-for="result in results" :key="result.id" class="result-item" :class="result.status">
          <div class="result-title">
            <span>{{ result.name }}</span>
            <el-tag :type="result.status === 'success' ? 'success' : 'danger'" size="small">
              {{ result.status === 'success' ? '成功' : '失败' }}
            </el-tag>
            <span class="result-time">{{ formatTime(result.timestamp) }}</span>
          </div>
          <div class="result-message">{{ result.message }}</div>
          <el-row :gutter="12">
            <el-col :span="12">
              <div class="code-title">原始输入</div>
              <pre>{{ formatJson(result.input) }}</pre>
            </el-col>
            <el-col :span="12">
              <div class="code-title">服务端返回</div>
              <pre>{{ formatJson(result.output) }}</pre>
            </el-col>
          </el-row>
          <div v-if="result.headers" class="headers-block">
            <div class="code-title">安全响应头</div>
            <pre>{{ formatJson(result.headers) }}</pre>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无测试结果" :image-size="100" />
    </el-card>
  </div>
</template>

<style scoped>
.xss-test {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  padding: 12px;
  border-left: 3px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color-page);
}

.result-item.success {
  border-left-color: var(--el-color-success);
}

.result-item.error {
  border-left-color: var(--el-color-danger);
}

.result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}

.result-time,
.result-message {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.result-message {
  margin-bottom: 12px;
}

.code-title {
  margin-bottom: 6px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 500;
}

pre {
  min-height: 80px;
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.headers-block {
  margin-top: 12px;
}
</style>
