<template>
  <div class="flex h-full flex-col gap-3 overflow-hidden p-3">
    <!-- 顶部 Tab 条 -->
    <div
      class="flex h-11 shrink-0 items-center gap-1 rounded-xl border border-border bg-card px-2"
    >
      <button
        v-for="t in tabs"
        :key="t.key"
        class="relative flex h-full items-center gap-1.5 px-3 text-[13px] transition-colors"
        :class="
          activeTab === t.key
            ? 'font-medium text-foreground'
            : 'text-muted-foreground hover:text-foreground'
        "
        @click="activeTab = t.key"
      >
        <component :is="t.icon" class="size-3.5" />
        {{ t.label }}
        <span
          v-if="activeTab === t.key"
          class="absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-primary"
        />
      </button>
    </div>

    <!-- 面板区域（均挂载，切换保留状态） -->
    <div class="min-h-0 flex-1">
      <div v-show="activeTab === 'push'" class="h-full">
        <StreamPushPanel :servers="servers" :active="activeTab === 'push'" />
      </div>
      <div v-show="activeTab === 'client'" class="h-full">
        <StreamPushClientPanel :active="activeTab === 'client'" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { IconBroadcast, IconKey } from '@tabler/icons-vue'
import StreamPushPanel from '@/features/media/StreamPushPanel.vue'
import StreamPushClientPanel from '@/features/media/StreamPushClientPanel.vue'
import { listMediaServerApi } from '@/api/media'
import type { MediaServer } from '@/types/api'

type TabKey = 'push' | 'client'

const activeTab = ref<TabKey>('push')
const servers = ref<MediaServer[]>([])

const tabs: { key: TabKey; label: string; icon: unknown }[] = [
  { key: 'push', label: '推流列表', icon: IconBroadcast },
  { key: 'client', label: '授权管理', icon: IconKey },
]

onMounted(async () => {
  const res = await listMediaServerApi().catch(() => null)
  if (res?.data) servers.value = res.data
})
</script>
