<template>
  <div class="flex h-full flex-col overflow-hidden p-3">
    <div class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">录像计划</span>
        <div class="flex-1" />
        <Button
          v-access="PERM.GB_RECORD_PLAN_ADD"
          size="sm"
          class="h-7 text-xs"
          @click="openCreate"
        >
          <IconPlus class="size-3.5" />
          新增计划
        </Button>
        <RefreshButton :loading="loading" :on-refresh="loadList" />
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
              <th class="w-32 px-3 py-2 font-normal">录像时段</th>
              <th class="w-20 px-3 py-2 font-normal">启用</th>
              <th class="w-24 px-3 py-2 font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in plans"
              :key="p.id"
              class="group hover:bg-muted/30"
            >
              <td class="px-3 py-2">
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
              <td class="px-3 py-2 text-xs text-muted-foreground">
                {{ periodSummary(p.periods) }}
              </td>
              <td class="px-3 py-2">
                <Switch
                  :model-value="p.enabled === 1"
                  :disabled="!hasEditPerm || togglingId === p.id"
                  @update:model-value="(v: boolean) => onToggle(p, v)"
                />
              </td>
              <td class="px-3 py-2">
                <div class="flex items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    v-access="PERM.GB_RECORD_PLAN_EDIT"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
                    title="编辑"
                    @click="openEdit(p)"
                  >
                    <Pencil class="size-3.5" />
                  </button>
                  <button
                    v-access="PERM.GB_RECORD_PLAN_DELETE"
                    class="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-red-400"
                    title="删除"
                    @click="onDelete(p)"
                  >
                    <Trash2 class="size-3.5" />
                  </button>
                </div>
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
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <Dialog v-model:open="formOpen">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle class="text-base">{{ editing ? '编辑录像计划' : '新增录像计划' }}</DialogTitle>
          <DialogDescription />
        </DialogHeader>

        <div class="grid grid-cols-2 gap-3">
          <div class="space-y-1.5">
            <Label>计划名称</Label>
            <Input v-model="form.name" placeholder="如：大门枪机夜间录像" class="h-9" />
          </div>
          <div class="space-y-1.5">
            <Label>保留天数</Label>
            <Input
              v-model.number="form.retentionDays"
              type="number"
              min="0"
              class="h-9"
              placeholder="0 = 跟随全局"
            />
          </div>
          <div class="space-y-1.5">
            <Label>设备</Label>
            <div class="h-9 rounded-md border border-border px-1">
              <GbDeviceSelect v-model="form.deviceId" class="w-full" />
            </div>
          </div>
          <div class="space-y-1.5">
            <Label>通道</Label>
            <Select v-model="form.channelId">
              <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                <SelectValue :placeholder="channelsLoading ? '通道加载中...' : '请选择通道'" />
              </RawSelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="c in channels"
                  :key="c.channelId"
                  :value="c.channelId"
                >
                  {{ c.name || c.reportName || c.channelId }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5">
            <Label>收流模式</Label>
            <Select v-model="form.transport">
              <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                <SelectValue placeholder="随设备" />
              </RawSelectTrigger>
              <SelectContent>
                <SelectItem value="">随设备</SelectItem>
                <SelectItem value="UDP">UDP</SelectItem>
                <SelectItem value="TCP-passive">TCP 被动</SelectItem>
                <SelectItem value="TCP-active">TCP 主动</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5">
            <Label>码流</Label>
            <Select v-model="form.streamNumber">
              <RawSelectTrigger class="h-9 w-full px-2 text-[13px]">
                <SelectValue placeholder="主码流" />
              </RawSelectTrigger>
              <SelectContent>
                <SelectItem value="">主码流</SelectItem>
                <SelectItem value="stream:1">子码流</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <!-- 周时段 -->
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label>录像时段（每周循环，支持跨午夜）</Label>
            <Button variant="ghost" size="sm" class="h-7 text-xs" @click="addWeekdayQuick">
              <IconPlus class="size-3.5" />
              全天
            </Button>
          </div>
          <div class="space-y-1.5">
            <div
              v-for="(day, idx) in weekDays"
              :key="day.value"
              class="flex items-start gap-2 rounded-md border border-border px-2.5 py-2"
            >
              <div class="flex h-7 w-12 shrink-0 items-center text-xs text-muted-foreground">
                {{ day.label }}
              </div>
              <div class="flex min-w-0 flex-1 flex-wrap gap-1.5">
                <div
                  v-for="(p, pi) in form.periods[idx]"
                  :key="pi"
                  class="flex h-7 items-center gap-1 rounded-md bg-muted px-1.5"
                >
                  <input
                    v-model="p.startTime"
                    type="time"
                    class="w-[88px] bg-transparent text-xs outline-none"
                    title="开始时间"
                  />
                  <span class="text-xs text-muted-foreground">~</span>
                  <input
                    v-model="p.endTime"
                    type="time"
                    class="w-[88px] bg-transparent text-xs outline-none"
                    title="结束时间"
                  />
                  <button
                    class="flex size-5 items-center justify-center rounded text-muted-foreground hover:bg-background hover:text-red-400"
                    title="删除时段"
                    @click="removePeriod(idx, pi)"
                  >
                    <IconX class="size-3" />
                  </button>
                </div>
                <button
                  class="flex h-7 items-center gap-1 rounded-md border border-dashed border-border px-2 text-xs text-muted-foreground hover:border-primary/50 hover:text-foreground"
                  @click="addPeriod(idx)"
                >
                  <IconPlus class="size-3" />
                  添加
                </button>
              </div>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" size="sm" @click="formOpen = false">取消</Button>
          <Button size="sm" :loading="saving" @click="onSave">保存</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 云端录像计划
 * - 列表不分页（计划数量有限），可按设备过滤
 * - 周时段整体编辑、整体提交（后端先删后插）
 * - 停用/启用直接调 toggle，不进编辑弹窗
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { IconLoader2, IconPlus, IconX, IconCalendarTime } from '@tabler/icons-vue'
import { Pencil, Trash2 } from 'lucide-vue-next'
import RefreshButton from '@/components/RefreshButton.vue'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectValue } from '@/components/ui/select'
import { SelectTrigger as RawSelectTrigger } from 'reka-ui'
import GbDeviceSelect from '@/features/gb/GbDeviceSelect.vue'
import { PERM } from '@/features/access/permissions'
import { useAccess } from '@/features/access/useAccess'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import {
  getRecordPlanListApi,
  getRecordPlanApi,
  saveRecordPlanApi,
  updateRecordPlanApi,
  deleteRecordPlanApi,
  toggleRecordPlanApi,
  getGbDeviceChannelPageApi,
} from '@/api/gb28181'
import type { GbRecordPlan, GbRecordPlanPeriod, GbDeviceChannel } from '@/types/api'

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
onMounted(loadList)

const transportLabel = (t?: string) => {
  if (t === 'UDP') return 'UDP'
  if (t === 'TCP-passive') return 'TCP 被动'
  if (t === 'TCP-active') return 'TCP 主动'
  return '随设备'
}

const weekDayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

// 列表只显示“每天 N 段”这种摘要，完整时段进编辑弹窗看
const periodSummary = (periods?: GbRecordPlanPeriod[]) => {
  if (!periods || periods.length === 0) return '未配置'
  const days = new Set(periods.map((p) => p.dayOfWeek))
  if (days.size === 7) return `每天（${periods.length} 段）`
  return `${days.size} 天（${periods.length} 段）`
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

const onDelete = async (p: GbRecordPlan) => {
  const ok = await confirm({
    title: `确定删除计划「${p.name || ''}」吗？`,
    description: '删除后计划对应的周期录制将立即停止。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok || !p.id) return
  await deleteRecordPlanApi(p.id)
  toast.success('删除成功')
  plans.value = plans.value.filter((item) => item.id !== p.id)
}

/* ---------------- 新增 / 编辑 ---------------- */
const weekDays = weekDayLabels.map((label, i) => ({ label, value: i + 1 }))

interface FormState {
  id?: string
  name: string
  deviceId: string | undefined
  channelId: string | undefined
  transport: string
  streamNumber: string
  retentionDays: number
  enabled: number
  // 按周一~周日分桶，编辑/提交时再拍平
  periods: GbRecordPlanPeriod[][]
}

const formOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const channels = ref<GbDeviceChannel[]>([])
const channelsLoading = ref(false)

const emptyForm = (): FormState => ({
  name: '',
  deviceId: undefined,
  channelId: undefined,
  transport: 'TCP-passive',
  streamNumber: '',
  retentionDays: 0,
  enabled: 1,
  periods: [[], [], [], [], [], [], []],
})

const form = reactive<FormState>(emptyForm())

// 设备选定后拉通道
watch(
  () => form.deviceId,
  async (deviceId) => {
    form.channelId = undefined
    channels.value = []
    if (!deviceId) return
    channelsLoading.value = true
    try {
      const { data } = await getGbDeviceChannelPageApi({
        page: 1,
        pageSize: 1000,
        deviceId,
        parental: 0,
      })
      channels.value = data.rows
    } finally {
      channelsLoading.value = false
    }
  },
)

const addPeriod = (dayIdx: number) => {
  form.periods[dayIdx].push({ dayOfWeek: dayIdx + 1, startTime: '00:00', endTime: '23:59' })
}

// “全天”按钮：七天各加一段 00:00~23:59，已有时段的天跳过
const addWeekdayQuick = () => {
  form.periods.forEach((list, idx) => {
    if (list.length === 0) {
      list.push({ dayOfWeek: idx + 1, startTime: '00:00', endTime: '23:59' })
    }
  })
}

const removePeriod = (dayIdx: number, pi: number) => {
  form.periods[dayIdx].splice(pi, 1)
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  channels.value = []
  editing.value = false
  formOpen.value = true
}

const openEdit = async (p: GbRecordPlan) => {
  if (!p.id) return
  Object.assign(form, emptyForm())
  editing.value = true
  formOpen.value = true
  try {
    // 详情才带 periods，列表接口不带
    const { data } = await getRecordPlanApi(p.id)
    form.id = data.id
    form.name = data.name || ''
    form.deviceId = data.deviceId
    form.channelId = data.channelId
    form.transport = data.transport || 'TCP-passive'
    form.streamNumber = data.streamNumber || ''
    form.retentionDays = data.retentionDays ?? 0
    form.enabled = data.enabled ?? 1
    const buckets: GbRecordPlanPeriod[][] = [[], [], [], [], [], [], []]
    for (const period of data.periods || []) {
      const idx = (period.dayOfWeek || 1) - 1
      buckets[idx]?.push({ ...period })
    }
    form.periods = buckets
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
    formOpen.value = false
  }
}

const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/

const onSave = async () => {
  if (!form.name.trim()) {
    toast.warning('请填写计划名称')
    return
  }
  if (!form.deviceId || !form.channelId) {
    toast.warning('请选择设备与通道')
    return
  }
  const periods = form.periods.flat()
  if (periods.length === 0) {
    toast.warning('请至少配置一个录像时段')
    return
  }
  if (periods.some((p) => !TIME_RE.test(p.startTime) || !TIME_RE.test(p.endTime))) {
    toast.warning('时段时间格式不正确')
    return
  }
  // 相等表示全天录像（后端允许）；结束早于开始表示跨午夜
  const payload: GbRecordPlan = {
    id: form.id,
    name: form.name.trim(),
    deviceId: form.deviceId,
    channelId: form.channelId,
    enabled: form.enabled,
    transport: form.transport || undefined,
    streamNumber: form.streamNumber || undefined,
    retentionDays: form.retentionDays || 0,
    periods: periods.map((p) => ({
      dayOfWeek: p.dayOfWeek,
      startTime: p.startTime,
      endTime: p.endTime,
    })),
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateRecordPlanApi(payload)
      toast.success('保存成功')
    } else {
      await saveRecordPlanApi(payload)
      toast.success('新增成功')
    }
    formOpen.value = false
    await loadList()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}
</script>
