<template>
  <div class="space-y-5">
    <!-- 场景 -->
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
          <Badge variant="outline" class="text-[10px]">锁过期 {{ active.expire }} 秒</Badge>
          <Badge variant="outline" class="text-[10px]">并发 {{ active.concurrent }}</Badge>
          <Badge variant="outline" class="text-[10px]">延迟 {{ active.delayMs }} ms</Badge>
        </div>
      </div>
    </div>

    <!-- 表单 -->
    <div class="grid gap-3 rounded-lg border border-border p-3">
      <div class="flex items-center gap-3">
        <Label class="w-16 shrink-0 text-xs text-muted-foreground">业务编号</Label>
        <Input v-model="form.bizNo" :disabled="running" class="h-8 w-64 text-xs" />
      </div>
      <div class="flex items-center gap-3">
        <Label class="w-16 shrink-0 text-xs text-muted-foreground">表单内容</Label>
        <Input v-model="form.content" :disabled="running" class="h-8 w-64 text-xs" />
      </div>
    </div>

    <!-- 操作 -->
    <div class="flex flex-wrap items-center gap-2">
      <Button size="sm" :loading="running" @click="submitConcurrent">并发提交测试</Button>
      <Button size="sm" variant="outline" :disabled="running" @click="submitOnce">单次提交</Button>
      <Button size="sm" variant="ghost" :disabled="running" @click="resetStats">重置统计</Button>
      <span class="text-[11px] text-muted-foreground">固定窗口场景请等锁过期后再测</span>
    </div>

    <!-- 统计 -->
    <div class="grid grid-cols-3 gap-3">
      <div class="rounded-lg border border-border p-3 text-center">
        <div class="text-[11px] text-muted-foreground">总提交</div>
        <div class="mt-1 text-lg font-semibold text-primary">{{ requestCount }}</div>
      </div>
      <div class="rounded-lg border border-border p-3 text-center">
        <div class="text-[11px] text-muted-foreground">成功</div>
        <div class="mt-1 text-lg font-semibold text-emerald-600 dark:text-emerald-500">
          {{ successCount }}{{ pct(successCount) }}
        </div>
      </div>
      <div class="rounded-lg border border-border p-3 text-center">
        <div class="text-[11px] text-muted-foreground">防重拦截</div>
        <div class="mt-1 text-lg font-semibold text-destructive">
          {{ blockedCount }}{{ pct(blockedCount) }}
        </div>
      </div>
    </div>

    <!-- 日志 -->
    <div>
      <h4 class="mb-2 text-sm font-medium">提交日志</h4>
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
          暂无提交记录
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { repeatSubmitApi, type RepeatSubmitScene } from '@/api/test'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import { Badge } from '@/components/ui/badge'

interface SceneConfig {
  name: string
  scene: RepeatSubmitScene
  description: string
  expire: number
  concurrent: number
  delayMs: number
  mutateParams: boolean
}

const configs = reactive<Record<string, SceneConfig>>({
  quickUnlock: {
    name: '执行中防重', scene: 'quick-unlock',
    description: '锁在接口执行完成后释放，并发请求只允许一个执行。',
    expire: 3, concurrent: 5, delayMs: 1200, mutateParams: false,
  },
  fixedWindow: {
    name: '固定窗口', scene: 'fixed-window',
    description: '返回后不立即释放，5 秒内重复提交会被拦截。',
    expire: 5, concurrent: 5, delayMs: 100, mutateParams: false,
  },
  includeParamsSame: {
    name: '参数相同', scene: 'include-params',
    description: '相同参数 5 秒内重复提交会被拦截。',
    expire: 5, concurrent: 5, delayMs: 100, mutateParams: false,
  },
  includeParamsDifferent: {
    name: '参数不同', scene: 'include-params',
    description: '不同参数使用不同锁 key，可同时提交成功。',
    expire: 5, concurrent: 5, delayMs: 100, mutateParams: true,
  },
  global: {
    name: '全局防重', scene: 'global',
    description: '不区分用户/IP，全局 5 秒内只允许一次。',
    expire: 5, concurrent: 5, delayMs: 100, mutateParams: false,
  },
})

const activeKey = ref('quickUnlock')
const active = computed(() => configs[activeKey.value])

const form = reactive({ bizNo: 'ORDER-TEST-001', content: '重复提交测试表单' })
const running = ref(false)
const requestCount = ref(0)
const successCount = ref(0)
const blockedCount = ref(0)

interface Row { id: number; ok: boolean; message: string; time: number }
const results = ref<Row[]>([])

const selectConfig = (key: string) => {
  activeKey.value = key
  resetStats()
}

const resetStats = () => {
  requestCount.value = 0
  successCount.value = 0
  blockedCount.value = 0
  results.value = []
}

const sendOne = async (index: number): Promise<Row> => {
  const time = Date.now()
  const a = active.value
  try {
    const res = await repeatSubmitApi(a.scene, {
      bizNo: a.mutateParams ? `${form.bizNo}-${index}` : form.bizNo,
      content: form.content,
      delayMs: a.delayMs,
    })
    successCount.value++
    return { id: time + Math.random(), ok: true, message: String(res.data.message ?? '提交成功'), time }
  } catch (e: unknown) {
    blockedCount.value++
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ?? (e as Error).message
    return { id: time + Math.random(), ok: false, message: msg, time }
  } finally {
    requestCount.value++
  }
}

const submitConcurrent = async () => {
  if (running.value) return
  running.value = true
  resetStats()
  try {
    const rows = await Promise.all(
      Array.from({ length: active.value.concurrent }, (_, i) => sendOne(i)),
    )
    results.value.unshift(...rows)
  } finally {
    running.value = false
  }
}

const submitOnce = async () => {
  results.value.unshift(await sendOne(0))
}

const pct = (v: number) => (requestCount.value ? ` (${Math.round((v / requestCount.value) * 100)}%)` : '')
const fmt = (t: number) => new Date(t).toLocaleTimeString()
</script>
