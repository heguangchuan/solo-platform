<template>
  <div class="space-y-5">
    <!-- 场景选择 -->
    <div>
      <h4 class="text-sm font-medium">选择测试场景</h4>
      <div class="mt-2 flex flex-wrap gap-1.5">
        <button
          v-for="(c, key) in configs"
          :key="key"
          :disabled="running"
          class="rounded-md px-2.5 py-1 text-xs transition-colors disabled:opacity-50"
          :class="
            activeKey === key
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-muted-foreground hover:text-foreground'
          "
          @click="selectConfig(key)"
        >
          {{ c.name }}
        </button>
      </div>

      <div class="mt-3 rounded-lg bg-muted/40 p-3 text-xs">
        <p class="text-foreground">{{ active.description }}</p>
        <div class="mt-2 flex flex-wrap gap-1.5">
          <Badge variant="outline" class="text-[10px]">阈值 {{ active.maxCount }} 次</Badge>
          <Badge variant="outline" class="text-[10px]">窗口 {{ active.timeWindow }} 秒</Badge>
          <Badge variant="outline" class="text-[10px]">并发 {{ active.concurrent }}</Badge>
        </div>
      </div>
    </div>

    <!-- 操作区 -->
    <div class="flex flex-wrap items-center gap-2">
      <Button size="sm" :loading="loading" :disabled="running" @click="startTest">开始测试</Button>
      <Button v-if="running" size="sm" variant="destructive" @click="stopTest">停止</Button>
      <Button size="sm" variant="outline" :disabled="running" @click="resetStats">重置统计</Button>
      <Button size="sm" variant="ghost" :disabled="running" @click="singleRequest">单次请求</Button>
      <span class="text-[11px] text-muted-foreground">连续点击观察 429</span>
    </div>

    <!-- 统计 -->
    <div class="grid grid-cols-4 gap-3">
      <div v-for="s in statCards" :key="s.label" class="rounded-lg border border-border p-3 text-center">
        <div class="text-[11px] text-muted-foreground">{{ s.label }}</div>
        <div class="mt-1 text-lg font-semibold" :class="s.class">
          {{ s.value }}<span v-if="s.suffix" class="text-xs font-normal">{{ s.suffix }}</span>
        </div>
      </div>
    </div>

    <div
      v-if="limitTriggered"
      class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-600 dark:text-emerald-500"
    >
      滑动窗口限流已触发，Redis 限流机制工作正常。
    </div>

    <!-- 日志 -->
    <div>
      <h4 class="mb-2 text-sm font-medium">请求日志</h4>
      <div class="max-h-72 space-y-1 overflow-auto rounded-lg border border-border p-2">
        <div
          v-for="r in results.slice(0, 50)"
          :key="r.id"
          class="flex items-center gap-2 rounded px-2 py-1 text-xs"
          :style="{ borderLeft: `2px solid ${r.ok ? '#10b981' : '#ef4444'}` }"
        >
          <span class="font-mono text-[10px] text-muted-foreground">{{ fmt(r.time) }}</span>
          <span :class="r.ok ? '' : 'text-destructive'">{{ r.message }}</span>
        </div>
        <div v-if="!results.length" class="py-6 text-center text-xs text-muted-foreground">
          暂无请求记录
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { rateLimitApi, type RateLimitScene } from '@/api/test'
import Button from '@/components/ui/button/Button.vue'
import { Badge } from '@/components/ui/badge'

interface SceneConfig {
  name: string
  scene: RateLimitScene
  description: string
  maxCount: number
  timeWindow: number
  concurrent: number
  interval: number
  total: number
}

const configs = reactive<Record<string, SceneConfig>>({
  basic: {
    name: '基础限流', scene: 'basic',
    description: '10 秒内最多 5 次请求，适合手动点击。',
    maxCount: 5, timeWindow: 10, concurrent: 1, interval: 200, total: 10,
  },
  strict: {
    name: '严格限流', scene: 'strict',
    description: '3 秒内最多 2 次，快速触发限流。',
    maxCount: 2, timeWindow: 3, concurrent: 1, interval: 100, total: 5,
  },
  highPressure: {
    name: '高压限流', scene: 'high-pressure',
    description: '1 秒内最多 100 次，并发压力测试。',
    maxCount: 100, timeWindow: 1, concurrent: 10, interval: 10, total: 150,
  },
  user: {
    name: '用户级', scene: 'user',
    description: '按登录用户限流：10 秒内最多 3 次。',
    maxCount: 3, timeWindow: 10, concurrent: 1, interval: 500, total: 6,
  },
  global: {
    name: '全局限流', scene: 'global',
    description: '全局共享：5 秒内总共最多 10 次。',
    maxCount: 10, timeWindow: 5, concurrent: 3, interval: 50, total: 20,
  },
})

const activeKey = ref('basic')
const active = computed(() => configs[activeKey.value])

const running = ref(false)
const loading = ref(false)
const requestCount = ref(0)
const successCount = ref(0)
const errorCount = ref(0)
const limitTriggered = ref(false)

interface Row { id: number; ok: boolean; message: string; time: number }
const results = ref<Row[]>([])

let abort: AbortController | null = null

const selectConfig = (key: string) => {
  activeKey.value = key
  resetStats()
}

const resetStats = () => {
  requestCount.value = 0
  successCount.value = 0
  errorCount.value = 0
  limitTriggered.value = false
  results.value = []
}

const doRequest = async (): Promise<Row> => {
  const time = Date.now()
  try {
    const res = await rateLimitApi(active.value.scene)
    successCount.value++
    return { id: time + Math.random(), ok: true, message: String(res.data.message ?? '请求成功'), time }
  } catch (e: unknown) {
    errorCount.value++
    const status = (e as { response?: { status?: number } })?.response?.status
    if (status === 429) limitTriggered.value = true
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ?? (e as Error).message
    return { id: time + Math.random(), ok: false, message: msg, time }
  } finally {
    requestCount.value++
  }
}

const startTest = async () => {
  if (running.value) return
  running.value = true
  loading.value = true
  abort = new AbortController()
  resetStats()

  const cfg = active.value
  try {
    for (let i = 0; i < cfg.total; i += cfg.concurrent) {
      if (!running.value) break
      const n = Math.min(cfg.concurrent, cfg.total - i)
      const rows = await Promise.all(Array.from({ length: n }, () => doRequest()))
      results.value.unshift(...rows)
      if (i + n < cfg.total && running.value) {
        await new Promise((r) => setTimeout(r, cfg.interval))
      }
    }
  } finally {
    running.value = false
    loading.value = false
  }
}

const stopTest = () => {
  running.value = false
  abort?.abort()
}

const singleRequest = async () => {
  results.value.unshift(await doRequest())
}

const pct = (v: number) => (requestCount.value ? ` (${Math.round((v / requestCount.value) * 100)}%)` : '')

const statCards = computed(() => [
  { label: '总请求', value: requestCount.value, class: 'text-primary', suffix: '' },
  { label: '成功', value: successCount.value, class: 'text-emerald-600 dark:text-emerald-500', suffix: pct(successCount.value) },
  { label: '限流', value: errorCount.value, class: 'text-destructive', suffix: pct(errorCount.value) },
  {
    label: '限流状态',
    value: limitTriggered.value ? '已触发' : '未触发',
    class: limitTriggered.value ? 'text-emerald-600 dark:text-emerald-500' : 'text-muted-foreground',
    suffix: '',
  },
])

const fmt = (t: number) => new Date(t).toLocaleTimeString()
</script>
