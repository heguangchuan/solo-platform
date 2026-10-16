<template>
  <div class="space-y-5">
    <!-- 提示 -->
    <div class="rounded-lg border border-blue-500/30 bg-blue-500/[0.06] p-3 text-xs text-blue-700 dark:text-blue-400">
      本页面只用纯文本方式展示 payload，不使用 v-html；脚本没有弹窗且返回内容被清洗，说明 XSS 防护生效。
    </div>

    <!-- Payload 表单 -->
    <div>
      <h4 class="mb-2 text-sm font-medium">测试 Payload</h4>
      <div class="space-y-3 rounded-lg border border-border p-3">
        <div class="grid gap-1.5">
          <Label class="text-xs text-muted-foreground">标题字段（请求体）</Label>
          <textarea
            v-model="form.title"
            :disabled="loading"
            rows="2"
            class="w-full resize-y rounded-md border border-input bg-transparent px-3 py-2 font-mono text-xs outline-none focus:border-primary"
          />
        </div>
        <div class="grid gap-1.5">
          <Label class="text-xs text-muted-foreground">内容字段（请求体）</Label>
          <textarea
            v-model="form.content"
            :disabled="loading"
            rows="2"
            class="w-full resize-y rounded-md border border-input bg-transparent px-3 py-2 font-mono text-xs outline-none focus:border-primary"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="grid gap-1.5">
            <Label class="text-xs text-muted-foreground">查询 keyword</Label>
            <Input v-model="form.keyword" :disabled="loading" class="h-8 font-mono text-xs" />
          </div>
          <div class="grid gap-1.5">
            <Label class="text-xs text-muted-foreground">查询 remark</Label>
            <Input v-model="form.remark" :disabled="loading" class="h-8 font-mono text-xs" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="grid gap-1.5">
            <Label class="text-xs text-muted-foreground">嵌套字段 nested.value</Label>
            <Input v-model="form.nested" :disabled="loading" class="h-8 font-mono text-xs" />
          </div>
          <div class="grid gap-1.5">
            <Label class="text-xs text-muted-foreground">数组字段 tags[0]</Label>
            <Input v-model="form.tag" :disabled="loading" class="h-8 font-mono text-xs" />
          </div>
        </div>
      </div>
    </div>

    <!-- 操作 -->
    <div class="flex flex-wrap items-center gap-2">
      <Button size="sm" :loading="loading" @click="runAll">执行全部测试</Button>
      <Button size="sm" variant="outline" :disabled="loading" @click="testBody">请求体测试</Button>
      <Button size="sm" variant="outline" :disabled="loading" @click="testParam">参数测试</Button>
      <Button size="sm" variant="ghost" :disabled="loading" @click="resetPayloads">重置 Payload</Button>
    </div>

    <!-- 结果 -->
    <div>
      <h4 class="mb-2 text-sm font-medium">测试结果</h4>
      <div v-if="results.length" class="space-y-3">
        <div
          v-for="r in results"
          :key="r.id"
          class="rounded-lg bg-muted/40 p-3"
          :style="{ borderLeft: `3px solid ${r.status === 'success' ? '#10b981' : '#ef4444'}` }"
        >
          <div class="mb-1 flex items-center gap-2">
            <span class="text-xs font-medium">{{ r.name }}</span>
            <Badge :variant="r.status === 'success' ? 'default' : 'destructive'" class="h-4 px-1.5 text-[10px] leading-none">
              {{ r.status === 'success' ? '成功' : '失败' }}
            </Badge>
            <span class="ml-auto font-mono text-[10px] text-muted-foreground">{{ fmt(r.timestamp) }}</span>
          </div>
          <p class="mb-2 text-[11px] text-muted-foreground">{{ r.message }}</p>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <div class="mb-1 text-[11px] font-medium text-muted-foreground">原始输入</div>
              <pre class="min-h-20 max-h-60 overflow-auto whitespace-pre-wrap break-all rounded bg-background p-2 font-mono text-[11px]">{{ formatJson(r.input) }}</pre>
            </div>
            <div>
              <div class="mb-1 text-[11px] font-medium text-muted-foreground">服务端返回</div>
              <pre class="min-h-20 max-h-60 overflow-auto whitespace-pre-wrap break-all rounded bg-background p-2 font-mono text-[11px]">{{ formatJson(r.output) }}</pre>
            </div>
          </div>
          <div v-if="r.headers" class="mt-2">
            <div class="mb-1 text-[11px] font-medium text-muted-foreground">安全响应头</div>
            <pre class="max-h-40 overflow-auto whitespace-pre-wrap break-all rounded bg-background p-2 font-mono text-[11px]">{{ formatJson(r.headers) }}</pre>
          </div>
        </div>
      </div>
      <div v-else class="rounded-lg border border-dashed border-border py-8 text-center text-xs text-muted-foreground">
        暂无测试结果
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import service from '@/utils/request'
import type { Result } from '@/types/api'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import { Badge } from '@/components/ui/badge'

const SCRIPT_PAYLOAD = '<scr' + 'ipt>alert("title")</scr' + 'ipt>测试标题'
const CONTENT_PAYLOAD = '<img src=x onerror=alert("content")>测试内容<b>加粗</b>'
const KEYWORD_PAYLOAD = '<svg onload=alert("keyword")>搜索词'
const REMARK_PAYLOAD = 'javascript:alert("remark")'
const NESTED_PAYLOAD = '<iframe src="javascript:alert(1)"></iframe>嵌套内容'
const TAG_PAYLOAD = '<a href="javascript:alert(1)">危险链接</a>'

interface TestResult {
  id: number
  name: string
  status: 'success' | 'error'
  message: string
  timestamp: number
  input?: unknown
  output?: unknown
  headers?: Record<string, string>
}

const loading = ref(false)
const results = ref<TestResult[]>([])
const form = reactive({
  title: SCRIPT_PAYLOAD,
  content: CONTENT_PAYLOAD,
  keyword: KEYWORD_PAYLOAD,
  remark: REMARK_PAYLOAD,
  nested: NESTED_PAYLOAD,
  tag: TAG_PAYLOAD,
})

const pickSecurityHeaders = (headers: AxiosResponse['headers']): Record<string, string> => ({
  'content-security-policy': headers?.['content-security-policy'] || '',
  'x-content-type-options': headers?.['x-content-type-options'] || '',
  'x-frame-options': headers?.['x-frame-options'] || '',
  'referrer-policy': headers?.['referrer-policy'] || '',
})

const testBody = async () => {
  const payload = {
    title: form.title,
    content: form.content,
    nested: { value: form.nested },
    tags: [form.tag],
  }
  try {
    const response = await service.post('/api/test/xss/body', payload, { rawResponse: true } as AxiosRequestConfig) as unknown as AxiosResponse<Result>
    
    results.value.unshift({
      id: Date.now() + Math.random(),
      name: '请求体清洗测试',
      status: 'success',
      message: '请求体已返回清洗结果',
      timestamp: Date.now(),
      input: payload,
      output: response?.data?.data,
      headers: pickSecurityHeaders(response.headers),
    })
  } catch (e: unknown) {
    results.value.unshift({
      id: Date.now() + Math.random(),
      name: '请求体清洗测试',
      status: 'error',
      message: (e as Error).message || '测试失败',
      timestamp: Date.now(),
      input: payload,
    })
  }
}

const testParam = async () => {
  const input = { keyword: form.keyword, remark: form.remark }
  try {
    const response = await service.get('/api/test/xss/param', { params: input, rawResponse: true } as AxiosRequestConfig) as unknown as AxiosResponse<Result>
    results.value.unshift({
      id: Date.now() + Math.random(),
      name: '查询参数清洗测试',
      status: 'success',
      message: '查询参数已返回清洗结果',
      timestamp: Date.now(),
      input,
      output: response?.data?.data,
      headers: pickSecurityHeaders(response.headers),
    })
  } catch (e: unknown) {
    results.value.unshift({
      id: Date.now() + Math.random(),
      name: '查询参数清洗测试',
      status: 'error',
      message: (e as Error).message || '测试失败',
      timestamp: Date.now(),
      input,
    })
  }
}

const runAll = async () => {
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

const resetPayloads = () => {
  form.title = SCRIPT_PAYLOAD
  form.content = CONTENT_PAYLOAD
  form.keyword = KEYWORD_PAYLOAD
  form.remark = REMARK_PAYLOAD
  form.nested = NESTED_PAYLOAD
  form.tag = TAG_PAYLOAD
}

const formatJson = (value: unknown) => JSON.stringify(value, null, 2)
const fmt = (t: number) => new Date(t).toLocaleTimeString()
</script>
