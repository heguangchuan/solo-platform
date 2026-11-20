<template>
  <section class="rounded-xl border border-border bg-card">
    <!-- 头部：节点选择 + 实时控制 -->
    <div class="flex h-12 items-center gap-2 border-b border-border px-4">
      <h2 class="shrink-0 text-sm font-semibold">媒体节点</h2>

      <Select v-if="servers.length > 1" v-model="serverId">
        <SelectTrigger class="h-7 w-60 text-xs">
          <SelectValue placeholder="选择节点" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="s in servers" :key="s.serverId" :value="s.serverId">
            {{ s.name || s.serverId }}（{{ s.hostIp }}:{{ s.httpPort }}）
          </SelectItem>
        </SelectContent>
      </Select>

      <template v-if="summary">
        <span
          v-if="servers.length <= 1"
          class="truncate text-[13px] font-medium"
        >
          {{ summary.serverName || summary.serverId }}
        </span>
        <span
          class="shrink-0 text-xs"
          :class="summary.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'"
        >
          {{ summary.status === 1 ? '在线' : '离线' }}
        </span>
        <span class="shrink-0 font-mono text-xs text-muted-foreground">
          {{ summary.hostIp }}:{{ summary.httpPort }}
        </span>
      </template>

      <div class="flex-1" />

      <Switch :model-value="realtime" class="scale-90" @update:model-value="realtime = $event" />
      <span class="text-xs text-muted-foreground">实时</span>
      <span v-if="lastRefreshAt" class="font-mono text-xs text-muted-foreground">
        {{ formatTime(lastRefreshAt) }}
      </span>
      <Button variant="outline" size="xs" class="h-7" @click="refreshAll">
        <IconRefresh class="size-3" />
        刷新
      </Button>
    </div>

    <!-- 主体 -->
    <div v-if="summary" class="space-y-3 p-4">
      <!-- KPI -->
      <div class="grid grid-cols-5 gap-2">
        <div
          v-for="kpi in kpis"
          :key="kpi.label"
          class="rounded-md border px-2 py-2.5 text-center"
          :class="kpi.highlight ? 'border-primary/40 bg-primary/[0.07]' : ''"
        >
          <div class="text-xl font-semibold leading-none">{{ kpi.value }}</div>
          <div class="mt-1.5 text-[11px] text-muted-foreground">{{ kpi.label }}</div>
        </div>
      </div>

      <!-- 明细 Tabs -->
      <Tabs :model-value="activeTab" class="gap-2" @update:model-value="onTabChange">
        <TabsList>
          <TabsTrigger v-for="t in tabs" :key="t.key" :value="t.key">
            {{ t.label }} ({{ t.count }})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="gb">
          <DetailTable :loading="tabLoading.gb" :count="gbSessions.length" empty="无国标会话">
            <template #head>
              <tr>
                <th class="w-56 px-3 py-2 font-normal">streamId</th>
                <th class="w-24 px-3 py-2 font-normal">类型</th>
                <th class="w-24 px-3 py-2 font-normal">状态</th>
                <th class="w-44 px-3 py-2 font-normal">开始时间</th>
              </tr>
            </template>
            <tr v-for="r in gbSessions" :key="r.streamId" class="border-b border-border last:border-0">
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.streamId">{{ r.streamId }}</td>
              <td class="px-3 py-2">
                <Badge :variant="r.sessionType === 'PLAYBACK' ? 'default' : 'secondary'">
                  {{ r.sessionType || '—' }}
                </Badge>
              </td>
              <td class="px-3 py-2">
                <Badge :variant="r.status === 'READY' ? 'secondary' : 'outline'">
                  {{ r.status || '—' }}
                </Badge>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">{{ r.startTime || '—' }}</td>
            </tr>
          </DetailTable>
        </TabsContent>

        <TabsContent value="push">
          <DetailTable :loading="tabLoading.push" :count="pushItems.length" empty="无第三方推流">
            <template #head>
              <tr>
                <th class="w-28 px-3 py-2 font-normal">app</th>
                <th class="w-48 px-3 py-2 font-normal">stream</th>
                <th class="w-24 px-3 py-2 font-normal">协议</th>
                <th class="w-44 px-3 py-2 font-normal">推流开始时间</th>
              </tr>
            </template>
            <tr v-for="r in pushItems" :key="`${r.app}/${r.stream}`" class="border-b border-border last:border-0">
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.app">{{ r.app }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.stream">{{ r.stream }}</td>
              <td class="px-3 py-2 text-xs">{{ r.originType || '—' }}</td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">{{ r.pushTime || '—' }}</td>
            </tr>
          </DetailTable>
        </TabsContent>

        <TabsContent value="proxy">
          <DetailTable :loading="tabLoading.proxy" :count="proxyItems.length" empty="无拉流代理">
            <template #head>
              <tr>
                <th class="w-28 px-3 py-2 font-normal">app</th>
                <th class="w-40 px-3 py-2 font-normal">stream</th>
                <th class="w-56 px-3 py-2 font-normal">源地址</th>
                <th class="w-24 px-3 py-2 font-normal">状态</th>
              </tr>
            </template>
            <tr v-for="r in proxyItems" :key="`${r.app}/${r.stream}`" class="border-b border-border last:border-0">
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.app">{{ r.app }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.stream">{{ r.stream }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="r.srcUrl">
                {{ r.srcUrl }}
              </td>
              <td class="px-3 py-2">
                <Badge :variant="r.status === 1 ? 'secondary' : 'outline'">
                  {{ r.status === 1 ? '运行中' : '未启动' }}
                </Badge>
              </td>
            </tr>
          </DetailTable>
        </TabsContent>

        <TabsContent value="onvif">
          <DetailTable :loading="tabLoading.onvif" :count="onvifSessions.length" empty="无 ONVIF 会话">
            <template #head>
              <tr>
                <th class="w-56 px-3 py-2 font-normal">streamId</th>
                <th class="w-28 px-3 py-2 font-normal">设备 ID</th>
                <th class="w-56 px-3 py-2 font-normal">proxyKey</th>
                <th class="w-44 px-3 py-2 font-normal">开始时间</th>
              </tr>
            </template>
            <tr v-for="r in onvifSessions" :key="r.streamId" class="border-b border-border last:border-0">
              <td class="truncate px-3 py-2 font-mono text-xs" :title="r.streamId">{{ r.streamId }}</td>
              <td class="px-3 py-2 font-mono text-xs">{{ r.deviceId ?? '—' }}</td>
              <td class="truncate px-3 py-2 font-mono text-xs text-muted-foreground" :title="r.proxyKey">
                {{ r.proxyKey || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">{{ r.startTime || '—' }}</td>
            </tr>
          </DetailTable>
        </TabsContent>
      </Tabs>
    </div>

    <div
      v-else-if="serversLoaded && servers.length === 0"
      class="grid place-items-center py-12 text-xs text-muted-foreground"
    >
      还未配置媒体节点
    </div>
    <div v-else class="grid place-items-center py-12">
      <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconLoader2, IconRefresh } from '@tabler/icons-vue'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import Button from '@/components/ui/button/Button.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import DetailTable from './DetailTable.vue'
import {
  getHomeMediaServersApi,
  getHomeStreamStatsSummaryApi,
  getHomeStreamStatsDetailApi,
} from '@/api/home'
import type {
  MediaServer,
  MediaServerStreamStatsSummary,
  StreamStatsGbItem,
  StreamStatsOnvifItem,
  StreamStatsProxyItem,
  StreamStatsPushItem,
  StreamStatsTabKey,
} from '@/types/api'

const REFRESH_INTERVAL = 5000

const servers = ref<MediaServer[]>([])
const serversLoaded = ref(false)
const serverId = ref('')
const realtime = ref(true)
const activeTab = ref<StreamStatsTabKey>('gb')
const summary = ref<MediaServerStreamStatsSummary | null>(null)

const gbSessions = ref<StreamStatsGbItem[]>([])
const pushItems = ref<StreamStatsPushItem[]>([])
const proxyItems = ref<StreamStatsProxyItem[]>([])
const onvifSessions = ref<StreamStatsOnvifItem[]>([])

const tabLoading = ref<Record<StreamStatsTabKey, boolean>>({
  gb: false,
  push: false,
  proxy: false,
  onvif: false,
})
const tabLoaded = ref<Record<StreamStatsTabKey, boolean>>({
  gb: false,
  push: false,
  proxy: false,
  onvif: false,
})

const lastRefreshAt = ref<Date | null>(null)
let timer: ReturnType<typeof setInterval> | null = null
let silent = false

const kpis = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '国标流', value: s.gbStreamCount, highlight: false },
    { label: '推流数', value: s.pushStreamCount, highlight: false },
    { label: '拉流数', value: s.proxyStreamCount, highlight: false },
    { label: 'ONVIF 流', value: s.onvifStreamCount, highlight: false },
    { label: '总数', value: s.total, highlight: true },
  ]
})

const tabs = computed(() => {
  const s = summary.value
  return [
    { key: 'gb' as const, label: '国标会话', count: s?.gbStreamCount ?? 0 },
    { key: 'push' as const, label: '推流', count: s?.pushStreamCount ?? 0 },
    { key: 'proxy' as const, label: '拉流代理', count: s?.proxyStreamCount ?? 0 },
    { key: 'onvif' as const, label: 'ONVIF 会话', count: s?.onvifStreamCount ?? 0 },
  ]
})

const loadSummary = async (id: string) => {
  try {
    const res = await getHomeStreamStatsSummaryApi(id)
    summary.value = res.data
  } catch (e) {
    if (!silent) console.warn('[流统计] summary 加载失败', e)
  }
}

const loadTab = async (key: StreamStatsTabKey, force = false) => {
  if (!summary.value) return
  if (!force && (tabLoaded.value[key] || tabLoading.value[key])) return
  tabLoading.value[key] = true
  try {
    const id = summary.value.serverId
    if (key === 'gb') {
      gbSessions.value = (await getHomeStreamStatsDetailApi<StreamStatsGbItem>(id, 'gb')).data
    } else if (key === 'push') {
      pushItems.value = (await getHomeStreamStatsDetailApi<StreamStatsPushItem>(id, 'push')).data
    } else if (key === 'proxy') {
      proxyItems.value = (await getHomeStreamStatsDetailApi<StreamStatsProxyItem>(id, 'proxy')).data
    } else {
      onvifSessions.value = (
        await getHomeStreamStatsDetailApi<StreamStatsOnvifItem>(id, 'onvif')
      ).data
    }
    tabLoaded.value[key] = true
  } catch (e) {
    if (!silent) console.warn(`[流统计] ${key} 明细加载失败`, e)
  } finally {
    tabLoading.value[key] = false
  }
}

const onTabChange = (value: string | number) => {
  activeTab.value = String(value) as StreamStatsTabKey
  void loadTab(activeTab.value)
}

const refreshAll = async () => {
  if (!summary.value) return
  const wasSilent = silent
  silent = true
  try {
    await loadSummary(summary.value.serverId)
    if (summary.value) await loadTab(activeTab.value, true)
  } finally {
    silent = wasSilent
  }
  lastRefreshAt.value = new Date()
}

/** 切换节点：重置明细缓存，重拉概要 + 首个 Tab */
watch(serverId, async (id) => {
  if (!id) return
  summary.value = null
  activeTab.value = 'gb'
  gbSessions.value = []
  pushItems.value = []
  proxyItems.value = []
  onvifSessions.value = []
  tabLoaded.value = { gb: false, push: false, proxy: false, onvif: false }
  await loadSummary(id)
  if (summary.value) {
    lastRefreshAt.value = new Date()
    await loadTab('gb')
  }
})

const startTimer = () => {
  if (timer) return
  timer = setInterval(refreshAll, REFRESH_INTERVAL)
}
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(realtime, (on) => {
  if (on) startTimer()
  else stopTimer()
})

onMounted(async () => {
  try {
    servers.value = (await getHomeMediaServersApi()).data
    // 默认选第一个在线节点，无在线则取第一个
    const preferred = servers.value.find((s) => s.status === 1) ?? servers.value[0]
    if (preferred) serverId.value = preferred.serverId
  } catch {
    // 节点列表加载失败留空态
  } finally {
    serversLoaded.value = true
  }
  if (realtime.value) startTimer()
})

onBeforeUnmount(stopTimer)

const formatTime = (d: Date) => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>
