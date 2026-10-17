<template>
  <div class="flex h-full gap-3 overflow-hidden p-3 pt-3">
    <!-- 左侧：下级平台列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <IconInfoCircle class="size-3.5 shrink-0" />
          管理接入黑名单，登记的国标 ID 将被拒绝注册（下级平台、IPC、NVR 通用）
        </span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
        <Button
          v-access="PERM.GB_PLATFORM_ADD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
          @click="onCreate"
        >
          <Plus class="size-3.5" />
          新建
        </Button>
      </div>

      <!-- 搜索行 -->
      <div class="flex h-11 shrink-0 items-center gap-2 px-3">
        <IconSearch class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          v-model="platformIdQ"
          class="h-7 w-44 rounded-md bg-transparent px-2 font-mono text-[13px] outline-none placeholder:font-sans placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="国标编码"
        />
        <input
          v-model="nameQ"
          class="h-7 w-36 rounded-md bg-transparent px-2 text-[13px] outline-none placeholder:text-muted-foreground focus:bg-muted/70"
          placeholder="备注名"
        />

        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ resource.total.value }} 条
        </span>

        <div class="flex-1" />

        <Button
          v-if="hasCondition"
          variant="ghost"
          size="sm"
          class="h-7 text-xs text-muted-foreground"
          @click="onClear"
        >
          <IconX class="size-3.5" />
          清除
        </Button>
      </div>

      <!-- 列表 -->
      <div ref="scrollerRef" class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="border-b border-border bg-card text-xs text-muted-foreground">
              <th class="w-52 px-3 py-2 font-normal">国标编码</th>
              <th class="w-48 px-3 py-2 font-normal">备注名</th>
              <th class="group w-28 px-3 py-2 font-normal">
                <Select v-model="enabledFilter">
                  <RawSelectTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    接入方式
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="
                        enabledFilter !== '__all__'
                          ? 'fill-primary text-primary opacity-100'
                          : ''
                      "
                    />
                  </RawSelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">全部</SelectItem>
                    <SelectItem value="0">禁止接入</SelectItem>
                    <SelectItem value="1">允许接入</SelectItem>
                  </SelectContent>
                </Select>
              </th>
              <th class="w-44 px-3 py-2 font-normal">登记时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in resource.items.value"
              :key="row.id"
              class="group cursor-pointer border-b border-border/50"
              :class="
                !creating && selected?.id === row.id
                  ? 'bg-primary/[0.06]'
                  : 'hover:bg-muted/30'
              "
              @click="onRowClick(row)"
            >
              <td class="px-3 py-2 font-mono text-xs">{{ row.platformId }}</td>
              <td class="truncate px-3 py-2" :title="row.name">
                {{ row.name || '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  class="text-xs"
                  :class="row.enabled === 1 ? 'text-emerald-500' : 'text-red-500'"
                >
                  {{ row.enabled === 1 ? '允许接入' : '禁止接入' }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ row.createTime || '—' }}
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="firstLoading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>

        <div ref="sentinelRef" class="grid place-items-center py-4">
          <IconLoader2
            v-if="resource.loading.value && resource.items.value.length"
            class="size-4 animate-spin text-muted-foreground"
          />
          <span
            v-else-if="resource.finished.value && resource.items.value.length"
            class="text-xs text-muted-foreground"
          >
            已全部加载
          </span>
        </div>
      </div>

      <!-- 底部 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <GbChildPlatformInspector
            :platform="selected ?? undefined"
            :creating="creating"
            :local-info="localInfo"
            @close="onCloseInspector"
            @saved="onSaved"
            @created="onCreated"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconFilter,
  IconLoader2,
  IconSearch,
  IconX,
} from '@tabler/icons-vue'
import { Info as IconInfoCircle, Plus } from 'lucide-vue-next'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import {
  Select,
  SelectContent,
  SelectItem,
} from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import GbChildPlatformInspector from './GbChildPlatformInspector.vue'
import { getGbPlatformPageApi, getGbLocalPlatformInfoApi } from '@/api/gb28181'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import type {
  GbLocalPlatformInfo,
  GbPlatform,
  GbPlatformPageRequest,
} from '@/types/api'

/* ---------------- 本平台身份（供 Inspector 配置引导） ---------------- */
const localInfo = ref<GbLocalPlatformInfo>({})
const fetchLocalInfo = async () => {
  try {
    localInfo.value = (await getGbLocalPlatformInfoApi()).data
  } catch {
    // 加载失败不影响主列表
  }
}

/* ---------------- 搜索 ---------------- */
const platformIdQ = ref('')
const nameQ = ref('')
const enabledFilter = ref<'__all__' | '0' | '1'>('__all__')

const hasCondition = computed(
  () =>
    !!platformIdQ.value.trim() ||
    !!nameQ.value.trim() ||
    enabledFilter.value !== '__all__',
)

const resource = useResource<GbPlatformPageRequest, GbPlatform>({
  fetcher: (q) =>
    getGbPlatformPageApi({
      ...q,
      platformId: platformIdQ.value.trim() || undefined,
      name: nameQ.value.trim() || undefined,
      enabled: enabledFilter.value === '__all__' ? undefined : Number(enabledFilter.value),
    }),
  initial: { page: 1, pageSize: 50 },
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => resource.search(), 300)
}
watch([platformIdQ, nameQ], triggerSearch)
watch(enabledFilter, () => resource.search())

const onClear = () => {
  platformIdQ.value = ''
  nameQ.value = ''
  enabledFilter.value = '__all__'
  resource.search()
}

const onRefresh = () =>
  Promise.all([resource.load(true), fetchLocalInfo()])

/* ---------------- 检视器 ---------------- */
const selected = ref<GbPlatform | null>(null)
const creating = ref(false)

const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const onRowClick = (row: GbPlatform) => {
  creating.value = false
  selected.value = row
}
const onCreate = () => {
  selected.value = null
  creating.value = true
}
const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}
const onSaved = () => resource.search()
const onCreated = () => {
  creating.value = false
  resource.search()
}
const onDeleted = (row: GbPlatform) => {
  resource.removeWhere((item) => item.id === row.id)
  selected.value = null
  creating.value = false
}

/* ---------------- 生命周期 / observer ---------------- */
let observer: IntersectionObserver | null = null

onMounted(async () => {
  await Promise.all([resource.load(true), fetchLocalInfo()])
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) resource.load(false)
    },
    { root: scrollerRef.value, rootMargin: '80px' },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
})

watch(
  () => resource.items.value,
  (list) => {
    if (selected.value) {
      const fresh = list.find((r) => r.id === selected.value!.id)
      if (fresh) selected.value = fresh
    }
  },
)
</script>

<style scoped>
.inspector-enter-active,
.inspector-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.inspector-enter-from,
.inspector-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
