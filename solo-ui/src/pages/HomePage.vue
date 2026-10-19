<template>
  <div class="h-full overflow-y-auto">
    <div class="px-6 py-6">
      <!-- 问候 -->
      <div class="mb-5">
        <h1 class="text-2xl font-semibold tracking-tight">{{ greeting }}，{{ auth.user?.nickname }}</h1>
        <p class="mt-1 text-sm text-muted-foreground">
          今天是 {{ today }}，按
          <kbd class="rounded border border-border bg-secondary px-1.5 py-0.5 text-xs">⌘K</kbd>
          快速跳转页面
        </p>
      </div>

      <!-- 接入概览 -->
      <div class="mb-4 grid grid-cols-4 gap-4">
            <div
              v-for="c in cards"
              :key="c.label"
              class="flex items-center gap-4 rounded-xl border border-border bg-card p-5"
            >
              <div class="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                <component :is="c.icon" class="h-5 w-5" />
              </div>
              <div class="min-w-0">
                <div class="flex items-baseline gap-1.5">
                  <span
                    class="font-mono text-2xl font-bold leading-none tabular-nums"
                    :class="c.online > 0 ? 'text-emerald-500' : ''"
                  >
                    {{ c.online }}
                  </span>
                  <span class="font-mono text-sm text-muted-foreground tabular-nums">
                    / {{ c.total }}
                  </span>
                </div>
                <div class="mt-1.5 text-xs text-muted-foreground">{{ c.label }}</div>
              </div>
            </div>
          </div>

          <!-- 媒体节点流统计：所有账号可见，数据口径一致 -->
          <MediaServerStreamStatsPanel />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { IconDevices, IconVideo, IconDeviceCctv, IconServer } from '@tabler/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getHomeOverviewApi } from '@/api/home'
import MediaServerStreamStatsPanel from '@/features/media/MediaServerStreamStatsPanel.vue'
import type { HomeOverview } from '@/types/api'

const auth = useAuthStore()

const overview = ref<HomeOverview>({
  gbDeviceTotal: 0,
  gbDeviceOnline: 0,
  gbChannelTotal: 0,
  gbChannelOnline: 0,
  onvifDeviceTotal: 0,
  onvifDeviceOnline: 0,
  mediaServerTotal: 0,
  mediaServerOnline: 0,
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

const cards = computed(() => {
  const o = overview.value
  return [
    { label: '国标设备（在线 / 总数）', online: o.gbDeviceOnline, total: o.gbDeviceTotal, icon: IconDevices },
    { label: '国标通道（在线 / 总数）', online: o.gbChannelOnline, total: o.gbChannelTotal, icon: IconVideo },
    { label: 'ONVIF 设备（在线 / 总数）', online: o.onvifDeviceOnline, total: o.onvifDeviceTotal, icon: IconDeviceCctv },
    { label: '媒体节点（在线 / 总数）', online: o.mediaServerOnline, total: o.mediaServerTotal, icon: IconServer },
  ]
})

onMounted(async () => {
  try {
    overview.value = (await getHomeOverviewApi()).data
  } catch {
    /* 统计失败不阻塞页面 */
  }
})
</script>
