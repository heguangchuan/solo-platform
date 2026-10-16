<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child>
      <button
        type="button"
        class="flex h-7 w-44 items-center gap-1.5 rounded-md px-2 text-left text-[13px] outline-none hover:bg-muted/70 focus:bg-muted/70"
        :title="selected ? displayName(selected) + ' ' + modelValue : '按设备筛选'"
      >
        <template v-if="selected">
          <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="dotClass(selected.status)" />
          <span class="min-w-0 flex-1 truncate">{{ displayName(selected) }}</span>
          <span
            class="shrink-0 rounded-sm p-0.5 text-muted-foreground hover:bg-muted-foreground/15 hover:text-foreground"
            @click.stop="onClear"
          >
            <IconX class="size-3" />
          </span>
        </template>
        <span v-else class="truncate text-muted-foreground">请选择设备</span>
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="4"
        class="z-50 w-[300px] rounded-md border border-border bg-popover p-0 text-popover-foreground shadow-md"
      >
        <!-- 搜索 -->
        <div class="flex items-center gap-2 border-b border-border px-2.5">
          <IconSearch class="size-3.5 shrink-0 text-muted-foreground" />
          <input
            ref="inputRef"
            v-model="keyword"
            class="h-8 flex-1 bg-transparent text-[13px] outline-none placeholder:text-muted-foreground"
            placeholder="搜索设备名称 / 编码"
          />
          <IconLoader2 v-if="loading" class="size-3.5 animate-spin text-muted-foreground" />
        </div>

        <!-- 列表（滚动分页） -->
        <div ref="scrollerRef" class="max-h-64 overflow-auto p-1">
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-[13px] hover:bg-accent"
            :class="!modelValue ? 'bg-accent' : ''"
            @click="onSelect(undefined)"
          >
            <IconServer class="size-3.5 text-muted-foreground" />
            <span class="flex-1">全部设备</span>
          </button>

          <button
            v-for="d in devices"
            :key="d.id"
            type="button"
            class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-[13px] hover:bg-accent"
            :class="modelValue === d.deviceId ? 'bg-accent' : ''"
            @click="onSelect(d.deviceId)"
          >
            <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="dotClass(d.status)" />
            <span class="min-w-0 flex-1 truncate">{{ displayName(d) }}</span>
            <span class="shrink-0 font-mono text-[11px] text-muted-foreground">{{ d.deviceId }}</span>
          </button>

          <div ref="sentinelRef" class="grid place-items-center py-2">
            <IconLoader2
              v-if="loading && devices.length"
              class="size-3.5 animate-spin text-muted-foreground"
            />
          </div>

          <div
            v-if="!loading && !devices.length"
            class="px-2 py-6 text-center text-xs text-muted-foreground"
          >
            无匹配设备
          </div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
} from 'reka-ui'
import { IconX, IconSearch, IconServer } from '@tabler/icons-vue'
import { Loader2 as IconLoader2 } from 'lucide-vue-next'
import { searchGbDeviceApi, getGbDeviceApi } from '@/api/gb28181'
import type { GbDevice } from '@/types/api'

const PAGE_SIZE = 30

const props = defineProps<{
  /** 设备国标编码；空 = 全部设备 */
  modelValue?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

const open = ref(false)
const keyword = ref('')
const loading = ref(false)
const devices = ref<GbDevice[]>([])
const page = ref(1)
const finished = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
const scrollerRef = ref<HTMLElement | null>(null)
const sentinelRef = ref<HTMLElement | null>(null)

/** 已选设备：可能不在当前分页结果中，单独缓存以解析名称 */
const selectedDevice = ref<GbDevice | null>(null)

const displayName = (d: GbDevice) => d.name || d.reportName || d.deviceId

const dotClass = (status?: number) => {
  if (status === 1) return 'bg-emerald-500'
  if (status === 0) return 'bg-red-500'
  return 'bg-muted-foreground/50'
}

const load = async (reset = false) => {
  if (loading.value) return
  if (!reset && finished.value) return
  if (reset) {
    page.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    const res = (
      await searchGbDeviceApi({
        keyword: keyword.value.trim() || undefined,
        page: page.value,
        pageSize: PAGE_SIZE,
      })
    ).data
    devices.value = reset ? res.rows : [...devices.value, ...res.rows]
    if (devices.value.length >= res.total || res.rows.length === 0) {
      finished.value = true
    } else {
      page.value += 1
    }
  } finally {
    loading.value = false
  }
}

/* ---------------- 搜索防抖（服务端查询） ---------------- */
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let suppressSearch = false
watch(keyword, () => {
  if (!open.value || suppressSearch) return
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => load(true), 300)
})

/* ---------------- 已选设备名称解析 ---------------- */
const selected = computed(
  () =>
    devices.value.find((d) => d.deviceId === props.modelValue) ??
    (selectedDevice.value?.deviceId === props.modelValue ? selectedDevice.value : undefined),
)

const resolveSelected = async (deviceId?: string) => {
  if (!deviceId) {
    selectedDevice.value = null
    return
  }
  if (devices.value.some((d) => d.deviceId === deviceId)) return
  try {
    selectedDevice.value = (await getGbDeviceApi(deviceId)).data
  } catch {
    selectedDevice.value = null
  }
}

watch(
  () => props.modelValue,
  (v) => resolveSelected(v),
  { immediate: true },
)

/* ---------------- 弹层开关 / 滚动分页 ---------------- */
let observer: IntersectionObserver | null = null

watch(open, async (v) => {
  if (!v) return
  suppressSearch = true
  keyword.value = ''
  suppressSearch = false
  await nextTick()
  inputRef.value?.focus()
  await load(true)
  await nextTick()
  if (observer) observer.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) load(false)
    },
    { root: scrollerRef.value, rootMargin: '80px' },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
})

const onSelect = (deviceId: string | undefined) => {
  emit('update:modelValue', deviceId)
  open.value = false
}

const onClear = () => emit('update:modelValue', undefined)
</script>
