<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">移动轨迹</span>
        <div class="flex-1" />
        <RefreshButton :loading="resource.loading" :on-refresh="onRefresh" />
      </div>

      <!-- 列表 -->
      <div ref="scrollerRef" class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="group w-52 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    设备
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="deviceIdQ.trim() ? 'fill-primary text-primary opacity-100' : ''"
                    />
                  </PopoverTrigger>
                  <PopoverPortal>
                    <PopoverContent
                      align="start"
                      side="bottom"
                      :side-offset="6"
                      class="z-50 w-60 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md outline-none"
                    >
                      <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                        设备编码（模糊匹配）
                        <input
                          v-model="deviceIdQ"
                          class="h-7 rounded-md border border-input bg-transparent px-2 font-mono text-xs outline-none focus:border-ring"
                        />
                      </label>
                      <div class="mt-3 flex justify-end gap-2">
                        <Button
                          v-if="deviceIdQ"
                          variant="ghost"
                          size="sm"
                          class="h-7 text-xs"
                          @click="deviceIdQ = ''"
                        >
                          清除
                        </Button>
                        <PopoverClose as-child>
                          <Button size="sm" class="h-7 text-xs">完成</Button>
                        </PopoverClose>
                      </div>
                    </PopoverContent>
                  </PopoverPortal>
                </PopoverRoot>
              </th>
              <th class="group w-48 px-3 py-2 font-normal">
                <PopoverRoot>
                  <PopoverTrigger
                    class="inline-flex items-center gap-1 text-xs font-normal text-muted-foreground outline-none hover:text-foreground"
                  >
                    定位时间
                    <IconFilter
                      class="size-3 opacity-40 transition-opacity group-hover:opacity-100"
                      :class="
                        startTimeQ || endTimeQ ? 'fill-primary text-primary opacity-100' : ''
                      "
                    />
                  </PopoverTrigger>
                  <PopoverPortal>
                    <PopoverContent
                      align="start"
                      side="bottom"
                      :side-offset="6"
                      class="z-50 w-64 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md outline-none"
                    >
                      <div class="flex flex-col gap-2">
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          开始
                          <input
                            v-model="startTimeQ"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                        <label class="flex flex-col gap-1 text-[11px] text-muted-foreground">
                          结束
                          <input
                            v-model="endTimeQ"
                            type="datetime-local"
                            class="h-7 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
                          />
                        </label>
                      </div>
                      <div class="mt-3 flex justify-end gap-2">
                        <Button
                          v-if="startTimeQ || endTimeQ"
                          variant="ghost"
                          size="sm"
                          class="h-7 text-xs"
                          @click="startTimeQ = ''; endTimeQ = ''"
                        >
                          清除
                        </Button>
                        <PopoverClose as-child>
                          <Button size="sm" class="h-7 text-xs">完成</Button>
                        </PopoverClose>
                      </div>
                    </PopoverContent>
                  </PopoverPortal>
                </PopoverRoot>
              </th>
              <th class="w-56 px-3 py-2 font-normal">经纬度</th>
              <th class="w-24 px-3 py-2 font-normal">速度</th>
              <th class="w-24 px-3 py-2 font-normal">方向</th>
              <th class="w-24 px-3 py-2 font-normal">海拔</th>
              <th class="w-44 px-3 py-2 font-normal">接收时间</th>
              <th class="w-20 px-3 py-2 font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(p, i) in resource.items.value"
              :key="p.id"
              :data-row="i"
              class="group"
              :class="cursor === i ? 'bg-muted/50' : 'hover:bg-muted/30'"
              @click="cursor = i"
            >
              <td class="px-3 py-2">
                <div class="truncate" :title="p.deviceName">{{ p.deviceName || '—' }}</div>
                <div class="truncate font-mono text-[11px] text-muted-foreground">
                  {{ p.deviceId }}
                </div>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.positionTime || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                <template v-if="p.longitude != null && p.latitude != null">
                  {{ p.longitude.toFixed(6) }}, {{ p.latitude.toFixed(6) }}
                </template>
                <template v-else>—</template>
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ p.speed != null ? p.speed : '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ p.direction != null ? `${p.direction}°` : '—' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ p.altitude != null ? p.altitude : '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.receiveTime || '—' }}
              </td>
              <td class="px-3 py-2">
                <div class="flex items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    title="详情"
                    @click.stop="openDetail(p)"
                  >
                    <Eye class="size-3.5" />
                  </button>
                  <button
                    v-access="PERM.GB_POSITION_DELETE"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-red-400"
                    title="删除"
                    @click.stop="onDelete(p)"
                  >
                    <Trash2 class="size-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="firstLoading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>

        <div
          v-else-if="!resource.loading.value && resource.items.value.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconRouteOff class="size-8" />
          <div class="text-[13px]">暂无位置记录</div>
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

      <!-- 底部状态条 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <div class="flex-1" />
        <span>共 {{ resource.total.value }} 条位置点</span>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Dialog :open="detailOpen" @update:open="(v) => (detailOpen = v)">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>位置点详情</DialogTitle>
          <DialogDescription />
        </DialogHeader>
        <dl v-if="detail" class="grid grid-cols-[96px_1fr] gap-x-4 gap-y-3 text-[13px]">
          <dt class="text-muted-foreground">设备名称</dt>
          <dd>{{ detail.deviceName || '—' }}</dd>
          <dt class="text-muted-foreground">设备编码</dt>
          <dd class="font-mono">{{ detail.deviceId }}</dd>
          <dt class="text-muted-foreground">定位时间</dt>
          <dd class="font-mono">{{ detail.positionTime || '—' }}</dd>
          <dt class="text-muted-foreground">经度</dt>
          <dd class="font-mono">{{ detail.longitude ?? '—' }}</dd>
          <dt class="text-muted-foreground">纬度</dt>
          <dd class="font-mono">{{ detail.latitude ?? '—' }}</dd>
          <dt class="text-muted-foreground">海拔</dt>
          <dd class="font-mono">{{ detail.altitude != null ? `${detail.altitude} 米` : '—' }}</dd>
          <dt class="text-muted-foreground">速度</dt>
          <dd class="font-mono">{{ detail.speed != null ? detail.speed : '—' }}</dd>
          <dt class="text-muted-foreground">方向</dt>
          <dd class="font-mono">{{ detail.direction != null ? `${detail.direction}°` : '—' }}</dd>
          <dt class="text-muted-foreground">接收时间</dt>
          <dd class="font-mono">{{ detail.receiveTime || '—' }}</dd>
          <dt class="text-muted-foreground">入库时间</dt>
          <dd class="font-mono">{{ detail.createTime || '—' }}</dd>
        </dl>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconFilter, IconLoader2, IconRouteOff } from '@tabler/icons-vue'
import { Eye, Trash2 } from 'lucide-vue-next'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  PopoverRoot,
  PopoverTrigger,
  PopoverPortal,
  PopoverContent,
  PopoverClose,
} from 'reka-ui'
import { useResource } from '@/shared/composables/useResource'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import { deleteGbPositionApi, getGbPositionPageApi } from '@/api/gb28181'
import type { GbMobilePosition, GbMobilePositionPageRequest } from '@/types/api'

/* ---------------- 筛选条件 ---------------- */
const deviceIdQ = ref('')
const startTimeQ = ref('')
const endTimeQ = ref('')

const fmtTime = (v: string) => v.replace('T', ' ')

const resource = useResource<GbMobilePositionPageRequest, GbMobilePosition>({
  fetcher: (q) =>
    getGbPositionPageApi({
      ...q,
      deviceId: deviceIdQ.value.trim() || undefined,
      startTime: startTimeQ.value ? fmtTime(startTimeQ.value) : undefined,
      endTime: endTimeQ.value ? fmtTime(endTimeQ.value) : undefined,
    }),
  initial: { page: 1, pageSize: 50 },
})

const onRefresh = () => resource.load(true)

let debounceTimer: ReturnType<typeof setTimeout> | null = null
const triggerSearch = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    cursor.value = 0
    resource.search()
  }, 300)
}
watch([deviceIdQ, startTimeQ, endTimeQ], triggerSearch)

/* ---------------- 详情 ---------------- */
const detailOpen = ref(false)
const detail = ref<GbMobilePosition | null>(null)

const openDetail = (p: GbMobilePosition) => {
  detail.value = p
  detailOpen.value = true
}

/* ---------------- 删除 ---------------- */
const onDelete = async (p: GbMobilePosition) => {
  const ok = await confirm({
    title: '确定删除该位置点吗？',
    description: '删除后不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbPositionApi(p.id)
  toast.success('删除成功')
  resource.removeWhere((item) => item.id === p.id)
}

/* ---------------- 键盘流 ---------------- */
const cursor = ref(0)
const scrollerRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()

const firstLoading = computed(
  () => resource.loading.value && resource.items.value.length === 0,
)

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' ||
    el.tagName === 'TEXTAREA' ||
    el.tagName === 'SELECT' ||
    el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) return
  const list = resource.items.value
  switch (e.key) {
    case 'j':
    case 'J':
      e.preventDefault()
      if (list.length) {
        cursor.value = Math.min(list.length - 1, cursor.value + 1)
        scrollCursorIntoView()
      }
      break
    case 'k':
    case 'K':
      e.preventDefault()
      cursor.value = Math.max(0, cursor.value - 1)
      scrollCursorIntoView()
      break
  }
}

/* ---------------- 生命周期 ---------------- */
let observer: IntersectionObserver | null = null

onMounted(async () => {
  window.addEventListener('keydown', onGlobalKeydown)
  await resource.load(true)
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) resource.load(false)
    },
    { root: scrollerRef.value, rootMargin: '80px' },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  observer?.disconnect()
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
kbd {
  display: inline-block;
  border: 1px solid var(--border);
  border-radius: 3px;
  background: var(--muted);
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--muted-foreground);
  font-family: ui-monospace, monospace;
}
</style>
