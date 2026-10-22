<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">录像计划</span>
        <div class="flex-1" />
        <RefreshButton :loading="loading" :on-refresh="loadList" />
        <Button
          v-access="PERM.GB_RECORD_PLAN_ADD"
          variant="ghost"
          size="sm"
          class="ml-1 gap-1.5"
          @click="openCreate"
        >
          <IconPlus class="size-3.5" />
          新建
          <kbd class="rounded border border-border bg-muted px-1 text-[10px] text-muted-foreground">N</kbd>
        </Button>
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 px-3">
        <GbDeviceSelect v-model="deviceIdQ" />
        <span v-if="plans.length" class="text-xs text-muted-foreground">
          共 {{ plans.length }} 个计划
        </span>
      </div>

      <!-- 列表 -->
      <div class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="w-44 px-3 py-2 font-normal">计划名称</th>
              <th class="w-72 px-3 py-2 font-normal">设备 / 通道</th>
              <th class="w-28 px-3 py-2 font-normal">收流模式</th>
              <th class="w-20 px-3 py-2 font-normal">码流</th>
              <th class="w-24 px-3 py-2 font-normal">保留天数</th>
              <th class="w-40 px-3 py-2 font-normal">创建时间</th>
              <th class="w-20 px-3 py-2 font-normal">启用</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in plans"
              :key="p.id"
              class="group cursor-pointer"
              :class="
                !creating && selected?.id === p.id
                  ? 'bg-primary/[0.07]'
                  : 'hover:bg-muted/30'
              "
              @click="openDetail(p)"
            >
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === p.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <div class="truncate" :title="p.name">{{ p.name || '—' }}</div>
              </td>
              <td class="px-3 py-2">
                <div class="truncate font-mono text-xs">{{ p.deviceId }}</div>
                <div class="truncate font-mono text-[11px] text-muted-foreground">
                  {{ p.channelId }}
                </div>
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ transportLabel(p.transport) }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ p.streamNumber ? '子码流' : '主码流' }}
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ p.retentionDays ? `${p.retentionDays} 天` : '跟随全局' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ p.createTime || '—' }}
              </td>
              <td class="px-3 py-2" @click.stop>
                <Switch
                  :model-value="p.enabled === 1"
                  :disabled="!hasEditPerm || togglingId === p.id"
                  @update:model-value="(v: boolean) => onToggle(p, v)"
                />
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>
        <div
          v-else-if="plans.length === 0"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconCalendarTime class="size-8" />
          <div class="text-[13px]">暂无录像计划</div>
        </div>
      </div>

      <!-- 快捷键条 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>N</kbd> 新建</span>
        <span><kbd>R</kbd> 刷新</span>
        <div class="flex-1" />
        <span>共 {{ plans.length }} 个计划</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <GbRecordPlanInspector
            :plan="selected ?? undefined"
            :creating="creating"
            @close="onCloseInspector"
            @created="onCreated"
            @saved="onSaved"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
/**
 * 云端录像计划
 * - 列表不分页（计划数量有限），可按设备过滤
 * - 详情 / 新增 / 编辑 / 删除统一走右侧 Inspector（与设备页一致）
 * - 停用/启用直接调 toggle，不进检视器
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { IconLoader2, IconPlus, IconCalendarTime } from '@tabler/icons-vue'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import { Switch } from '@/components/ui/switch'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import GbRecordPlanInspector from '@/features/gb/GbRecordPlanInspector.vue'
import { PERM } from '@/features/access/permissions'
import { useAccess } from '@/features/access/useAccess'
import { toast } from '@/lib/toast'
import {
  getRecordPlanListApi,
  toggleRecordPlanApi,
} from '@/api/gb28181'
import type { GbRecordPlan } from '@/types/api'

/* ---------------- 列表 ---------------- */
const plans = ref<GbRecordPlan[]>([])
const loading = ref(false)
const deviceIdQ = ref<string | undefined>(undefined)
const togglingId = ref('')

const { has } = useAccess()
const hasEditPerm = computed(() => has(PERM.GB_RECORD_PLAN_EDIT))

const loadList = async () => {
  loading.value = true
  try {
    const { data } = await getRecordPlanListApi(deviceIdQ.value || undefined)
    plans.value = data || []
  } finally {
    loading.value = false
  }
}

watch(deviceIdQ, () => loadList())

const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' ||
    el.tagName === 'TEXTAREA' ||
    el.tagName === 'SELECT' ||
    el.isContentEditable)

/* ---------------- 检视器状态 ---------------- */
const selected = ref<GbRecordPlan | null>(null)
const creating = ref(false)

const openCreate = () => {
  selected.value = null
  creating.value = true
}

const openDetail = (p: GbRecordPlan) => {
  creating.value = false
  selected.value = p
}

const onCloseInspector = () => {
  creating.value = false
  selected.value = null
}

const onCreated = () => {
  creating.value = false
  loadList()
}

const onSaved = () => {
  loadList()
}

const onDeleted = (p: GbRecordPlan) => {
  plans.value = plans.value.filter((item) => item.id !== p.id)
  selected.value = null
  creating.value = false
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (creating.value || selected.value || isTypingTarget(e.target)) return
  if (e.key === 'n' || e.key === 'N') {
    e.preventDefault()
    openCreate()
  }
}

onMounted(() => {
  loadList()
  window.addEventListener('keydown', onGlobalKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))

const transportLabel = (t?: string) => {
  if (t === 'UDP') return 'UDP'
  if (t === 'TCP-passive') return 'TCP 被动'
  if (t === 'TCP-active') return 'TCP 主动'
  return '随设备'
}

const onToggle = async (p: GbRecordPlan, enabled: boolean) => {
  if (!p.id) return
  togglingId.value = p.id
  try {
    await toggleRecordPlanApi(p.id, enabled)
    p.enabled = enabled ? 1 : 0
    toast.success(enabled ? '已启用' : '已停用')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    togglingId.value = ''
  }
}
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
