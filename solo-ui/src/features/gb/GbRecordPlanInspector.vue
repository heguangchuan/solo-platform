<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconCalendarTime class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增录像计划' : plan?.name || '未命名计划' }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          按周编排录像时段，到点自动录制
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 基础设置 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">基础设置</h4>
      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            计划名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input v-model="form.name" class="h-8 text-xs" placeholder="如：大门枪机夜间录像" />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            设备<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="device-select flex-1">
            <GbDeviceSelect v-model="form.deviceId" />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            通道<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Select v-model="form.channelId">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue :placeholder="channelsLoading ? '通道加载中...' : '请选择通道'" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="c in channels" :key="c.channelId" :value="c.channelId">
                  {{ c.name || c.reportName || c.channelId }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">收流模式</Label>
          <div class="flex-1">
            <!-- reka-ui 的 SelectItem 不允许空串 value，用 __default__ 占位，提交时还原成空串 -->
            <Select
              :model-value="form.transport || '__default__'"
              @update:model-value="(v: string) => (form.transport = v === '__default__' ? '' : v)"
            >
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue placeholder="随设备" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__default__">随设备</SelectItem>
                <SelectItem value="UDP">UDP</SelectItem>
                <SelectItem value="TCP-passive">TCP 被动</SelectItem>
                <SelectItem value="TCP-active">TCP 主动</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">码流</Label>
          <div class="flex-1">
            <Select
              :model-value="form.streamNumber || '__default__'"
              @update:model-value="(v: string) => (form.streamNumber = v === '__default__' ? '' : v)"
            >
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue placeholder="主码流" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__default__">主码流</SelectItem>
                <SelectItem value="stream:1">子码流</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">保留天数</Label>
          <div class="flex-1">
            <Input
              v-model.number="form.retentionDays"
              type="number"
              min="0"
              class="h-8 font-mono text-xs"
              placeholder="0 = 跟随全局"
            />
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">启用</Label>
          <div class="flex-1">
            <Switch
              :model-value="form.enabled === 1"
              :disabled="!canEdit"
              @update:model-value="(v: boolean) => (form.enabled = v ? 1 : 0)"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- 录像时段 -->
    <div class="my-4 h-px bg-border" />
    <section>
      <div class="mb-3 flex items-center justify-between">
        <h4 class="text-[13px] font-medium">录像时段<span class="ml-0.5 text-destructive">*</span></h4>
        <Button variant="ghost" size="sm" class="h-7 text-xs" :disabled="!canEdit" @click="fillAllDay">
          <IconPlus class="size-3.5" />
          全天
        </Button>
      </div>
      <p class="mb-2.5 text-[11px] text-muted-foreground">每周循环，结束时间早于开始时间表示跨午夜。</p>
      <div class="space-y-1.5">
        <div
          v-for="(day, idx) in weekDays"
          :key="day"
          class="flex items-start gap-2 rounded-md border border-border px-2.5 py-2"
        >
          <div class="flex h-7 w-9 shrink-0 items-center text-xs text-muted-foreground">
            {{ day }}
          </div>
          <div class="flex min-w-0 flex-1 flex-wrap gap-1.5">
            <div
              v-for="(period, pi) in form.periods[idx]"
              :key="pi"
              class="flex h-7 items-center gap-1 rounded-md bg-muted px-1.5"
              :class="canEdit ? '' : 'opacity-60'"
            >
              <input
                v-model="period.startTime"
                type="time"
                :disabled="!canEdit"
                class="w-[84px] rounded border border-border/60 bg-transparent px-1 text-xs outline-none focus:border-primary/60 disabled:cursor-not-allowed"
                title="开始时间"
              />
              <span class="text-xs text-muted-foreground">~</span>
              <input
                v-model="period.endTime"
                type="time"
                :disabled="!canEdit"
                class="w-[84px] rounded border border-border/60 bg-transparent px-1 text-xs outline-none focus:border-primary/60 disabled:cursor-not-allowed"
                title="结束时间"
              />
              <button
                v-if="canEdit"
                class="flex size-5 items-center justify-center rounded text-muted-foreground hover:bg-background hover:text-red-400"
                title="删除时段"
                @click="removePeriod(idx, pi)"
              >
                <IconX class="size-3" />
              </button>
            </div>
            <button
              v-if="canEdit"
              class="flex h-7 items-center gap-1 rounded-md border border-dashed border-border px-2 text-xs text-muted-foreground hover:border-primary/50 hover:text-foreground"
              @click="addPeriod(idx)"
            >
              <IconPlus class="size-3" />
              添加
            </button>
          </div>
        </div>
      </div>

      <div v-if="detailLoading" class="mt-3 flex justify-center">
        <IconLoader2 class="size-4 animate-spin text-muted-foreground" />
      </div>
    </section>

    <!-- 保存 -->
    <div v-if="canEdit" class="mt-5 flex justify-end gap-2">
      <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
      <Button
        v-access="creating ? PERM.GB_RECORD_PLAN_ADD : PERM.GB_RECORD_PLAN_EDIT"
        size="sm"
        :loading="saving"
        @click="onSave"
      >
        保存
      </Button>
    </div>

    <!-- 危险操作 -->
    <template v-if="!creating && plan">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除后计划对应的周期录制将立即停止。
        </p>
        <Button
          v-access="PERM.GB_RECORD_PLAN_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="size-3.5" />
          删除该计划
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  IconCalendarTime,
  IconX,
  IconPlus,
  IconTrash,
  IconLoader2,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import GbDeviceSelect from './GbDeviceSelect.vue'
import { PERM } from '@/features/access/permissions'
import { useAccess } from '@/features/access/useAccess'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import {
  getRecordPlanApi,
  saveRecordPlanApi,
  updateRecordPlanApi,
  deleteRecordPlanApi,
  getGbDeviceChannelPageApi,
} from '@/api/gb28181'
import type { GbRecordPlan, GbRecordPlanPeriod, GbDeviceChannel } from '@/types/api'

const props = defineProps<{
  plan?: GbRecordPlan
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  created: []
  saved: []
  deleted: [plan: GbRecordPlan]
}>()

const { has } = useAccess()
// 无编辑权限时表单只读（仅靠 v-access 隐藏保存按钮不够，输入框也得锁住）
const canEdit = computed(() =>
  props.creating ? has(PERM.GB_RECORD_PLAN_ADD) : has(PERM.GB_RECORD_PLAN_EDIT),
)

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

interface FormState {
  id?: string
  name: string
  deviceId: string | undefined
  channelId: string | undefined
  transport: string
  streamNumber: string
  retentionDays: number
  enabled: number
  // 按周一~周日分桶，提交时再拍平
  periods: GbRecordPlanPeriod[][]
}

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
const channels = ref<GbDeviceChannel[]>([])
const channelsLoading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)

/* ---------------- 初始化 / 详情加载 ---------------- */
const hydrateFromPlan = async (plan: GbRecordPlan) => {
  Object.assign(form, emptyForm())
  channels.value = []
  form.id = plan.id
  form.name = plan.name || ''
  form.deviceId = plan.deviceId
  form.channelId = plan.channelId
  form.transport = plan.transport || 'TCP-passive'
  form.streamNumber = plan.streamNumber || ''
  form.retentionDays = plan.retentionDays ?? 0
  form.enabled = plan.enabled ?? 1
  // 列表不带 periods，拉详情
  if (!plan.id) return
  detailLoading.value = true
  try {
    const { data } = await getRecordPlanApi(plan.id)
    const buckets: GbRecordPlanPeriod[][] = [[], [], [], [], [], [], []]
    for (const period of data.periods || []) {
      const idx = (period.dayOfWeek || 1) - 1
      buckets[idx]?.push({ ...period })
    }
    form.periods = buckets
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    detailLoading.value = false
  }
}

watch(
  () => [props.creating, props.plan?.id] as const,
  ([creating]) => {
    if (creating) {
      Object.assign(form, emptyForm())
      channels.value = []
    } else if (props.plan) {
      hydrateFromPlan(props.plan)
    }
  },
  { immediate: true },
)

/* 设备选定后拉叶子通道 */
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

/* ---------------- 时段编辑 ---------------- */
const addPeriod = (dayIdx: number) => {
  form.periods[dayIdx].push({ dayOfWeek: dayIdx + 1, startTime: '00:00', endTime: '23:59' })
}

// “全天”：七天各补一段 00:00~23:59，已有时段的天跳过
const fillAllDay = () => {
  form.periods.forEach((list, idx) => {
    if (list.length === 0) {
      list.push({ dayOfWeek: idx + 1, startTime: '00:00', endTime: '23:59' })
    }
  })
}

const removePeriod = (dayIdx: number, pi: number) => {
  form.periods[dayIdx].splice(pi, 1)
}

/* ---------------- 保存 / 删除 ---------------- */
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
    if (props.creating) {
      await saveRecordPlanApi(payload)
      toast.success('新增成功')
      emit('created')
    } else {
      await updateRecordPlanApi(payload)
      toast.success('保存成功')
      emit('saved')
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.plan?.id) return
  const ok = await confirm({
    title: `确定删除计划「${props.plan.name || ''}」吗？`,
    description: '删除后计划对应的周期录制将立即停止。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteRecordPlanApi(props.plan.id)
  toast.success('删除成功')
  emit('deleted', props.plan)
}
</script>

<style scoped>
/* GbDeviceSelect 内部 trigger 是定宽按钮，这里拉通成表单项宽度 */
.device-select :deep(button) {
  width: 100%;
  height: 2rem;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 0 8px;
}
</style>
