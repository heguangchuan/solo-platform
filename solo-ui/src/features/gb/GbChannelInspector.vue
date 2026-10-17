<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconVideo class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增通道' : channelName }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          手动录入国标通道或分组
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 通道信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">通道信息</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            所属设备<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.deviceId"
              class="h-8 font-mono text-xs"
              :disabled="!creating"
              placeholder="设备国标编码（20位）"
            />
            <p v-if="errors.deviceId" class="mt-1 text-[11px] text-destructive">
              {{ errors.deviceId }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            通道编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.channelId"
              class="h-8 font-mono text-xs"
              :disabled="!creating"
              placeholder="通道国标编码（20位）"
            />
            <p v-if="errors.channelId" class="mt-1 text-[11px] text-destructive">
              {{ errors.channelId }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            通道名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input v-model="form.name" class="h-8 text-xs" placeholder="自定义显示名称" />
            <p v-if="errors.name" class="mt-1 text-[11px] text-destructive">{{ errors.name }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >设备厂商</Label
          >
          <Input v-model="form.manufacturer" class="h-8 text-xs" placeholder="厂商" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >设备型号</Label
          >
          <Input v-model="form.model" class="h-8 text-xs" placeholder="型号" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >设备归属</Label
          >
          <Input v-model="form.owner" class="h-8 text-xs" placeholder="归属" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >行政编码</Label
          >
          <Input v-model="form.civilCode" class="h-8 font-mono text-xs" placeholder="行政区域编码" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >安装地址</Label
          >
          <Input v-model="form.address" class="h-8 text-xs" placeholder="安装地址" />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground"
            >云台类型</Label
          >
          <div class="flex-1">
            <Select v-model="form.ptzType">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="o in PTZ_OPTIONS" :key="o.value" :value="String(o.value)">
                  {{ o.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">经度</Label>
          <Input
            v-model.number="form.longitude"
            type="number"
            step="0.000001"
            class="h-8 flex-1 font-mono text-xs"
            placeholder="WGS84 经度"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">纬度</Label>
          <Input
            v-model.number="form.latitude"
            type="number"
            step="0.000001"
            class="h-8 flex-1 font-mono text-xs"
            placeholder="WGS84 纬度"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button
          v-access="creating ? PERM.GB_DEVICE_CHANNEL_ADD : PERM.GB_DEVICE_CHANNEL_EDIT"
          size="sm"
          :loading="saving"
          @click="onSave"
        >
          {{ creating ? '新增通道' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 视频预览（已有通道） -->
    <template v-if="!creating && channel && channel.parental !== 1">
      <div class="my-4 h-px bg-border" />

      <section>
        <div class="relative aspect-video w-full overflow-hidden rounded-md border border-border bg-black">
          <GbPlayer
            v-if="playUrl"
            :src="playUrl"
            :video-buffer="0.2"
            :show-transport="true"
            :transport="transport"
            :show-zoom="false"
            :show-fullscreen-btn="false"
            :show-screenshot-btn="false"
            :show-mute-btn="false"
            :show-info-btn="false"
            @error="onPlayerError"
            @stream-end="onStreamEnd"
            @timeout="onPlayerTimeout"
            @switch-transport="onSwitchTransport"
          >
            <template #toolbar-extra>
              <button
                v-access="PERM.GB_PLAY"
                title="回放"
                class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                @click="onOpenPlayback"
              >
                <IconHistory class="h-4 w-4" />
              </button>
              <button
                v-access="PERM.GB_PLAY"
                title="大窗口监控"
                class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
                @click="onOpenMonitor"
              >
                <IconArrowsMaximize class="h-4 w-4" />
              </button>
            </template>
          </GbPlayer>

          <!-- 播放入口（未点播时） -->
          <template v-else>
            <button
              v-access="PERM.GB_PLAY"
              title="播放"
              class="absolute inset-0 m-auto grid size-12 place-items-center rounded-full bg-black/50 text-white/85 transition-colors hover:bg-black/70 hover:text-white disabled:opacity-60"
              :disabled="playLoading"
              @click="startPlay"
            >
              <IconPlayerPlay v-if="!playLoading" class="ml-0.5 size-6" />
              <IconLoader2 v-else class="size-5 animate-spin" />
            </button>

            <!-- 回放：无需先点播即可进入 -->
            <button
              v-access="PERM.GB_PLAY"
              title="回放"
              class="absolute bottom-2 right-12 grid h-8 w-8 place-items-center rounded text-white/70 transition-colors hover:text-white"
              @click="onOpenPlayback"
            >
              <IconHistory class="h-4 w-4" />
            </button>

            <!-- 大窗口监控：无需先点播即可进入 -->
            <button
              v-access="PERM.GB_PLAY"
              title="大窗口监控"
              class="absolute bottom-2 right-2 grid h-8 w-8 place-items-center rounded text-white/70 transition-colors hover:text-white"
              @click="onOpenMonitor"
            >
              <IconArrowsMaximize class="h-4 w-4" />
            </button>
          </template>
        </div>

        <p v-if="playError" class="mt-1.5 text-[11px] text-destructive">{{ playError }}</p>
      </section>
    </template>

    <!-- 目录 / 上报信息（已有通道） -->
    <template v-if="!creating && channel">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">目录信息</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">资源类型</span>
            <span>{{ channel.parental === 1 ? '分组' : '通道' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">通道状态</span>
            <span
              :class="
                channel.status === 'ON'
                  ? 'text-emerald-500'
                  : channel.status === 'OFF'
                    ? 'text-red-500'
                    : 'text-muted-foreground'
              "
            >
              {{ statusText(channel) }}
            </span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">数据来源</span>
            <span>{{ sourceText(channel.source) }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">云台类型</span>
            <span>{{ ptzText(channel.ptzType) }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">上报名称</span>
            <span class="truncate">{{ channel.reportName || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">父分组编码</span>
            <span class="truncate font-mono">{{ channel.parentId || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">所属警区</span>
            <span class="truncate">{{ channel.block || '—' }}</span>
          </div>
          <div class="flex items-center justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">创建时间</span>
            <span class="truncate font-mono">{{ channel.createTime || '—' }}</span>
          </div>
        </div>
      </section>

      <!-- 危险操作 -->
      <section class="mt-6">
        <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
        <p class="mb-3 text-[11px] text-muted-foreground">
          删除通道记录不影响设备实际目录，设备重新同步后该通道可能再次出现。
        </p>
        <Button
          v-access="PERM.GB_DEVICE_CHANNEL_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="size-3.5" />
          删除该通道
        </Button>
      </section>
    </template>

    <!-- 大窗口监控：小预览在播时复用同一路流，不重复 stop/start -->
    <GbMonitorDialog
      v-if="channel"
      v-model:open="monitorOpen"
      :channel="channel"
      :reuse-stream="!!playUrl"
    />

    <!-- 回放弹窗 -->
    <GbPlaybackDialog
      v-if="channel"
      v-model:open="playbackOpen"
      :channel="channel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  IconVideo,
  IconX,
  IconTrash,
  IconPlayerPlay,
  IconLoader2,
  IconArrowsMaximize,
  IconHistory,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  saveGbDeviceChannelApi,
  updateGbDeviceChannelApi,
  deleteGbDeviceChannelApi,
  startGbPlayApi,
  stopGbPlayApi,
} from '@/api/gb28181'
import { PERM } from '@/features/access/permissions'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import GbPlayer from '@/features/gb/GbPlayer.vue'
import GbMonitorDialog from '@/features/gb/GbMonitorDialog.vue'
import GbPlaybackDialog from '@/features/gb/GbPlaybackDialog.vue'
import type { GbDeviceChannel, GbPlayResult } from '@/types/api'

const props = defineProps<{
  channel?: GbDeviceChannel
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [channel: GbDeviceChannel]
}>()

const PTZ_OPTIONS = [
  { value: 0, label: '未知' },
  { value: 1, label: '球机' },
  { value: 2, label: '半球' },
  { value: 3, label: '固定枪机' },
  { value: 4, label: '遥控枪机' },
]

type FormState = {
  id?: string
  deviceId: string
  channelId: string
  name: string
  manufacturer: string
  model: string
  owner: string
  civilCode: string
  address: string
  /** reka-ui Select 仅接受字符串，提交时转回数字 */
  ptzType: string
  longitude?: number
  latitude?: number
}

const buildForm = (): FormState => ({
  id: props.channel?.id,
  deviceId: props.channel?.deviceId ?? '',
  channelId: props.channel?.channelId ?? '',
  name: props.channel?.name ?? '',
  manufacturer: props.channel?.manufacturer ?? '',
  model: props.channel?.model ?? '',
  owner: props.channel?.owner ?? '',
  civilCode: props.channel?.civilCode ?? '',
  address: props.channel?.address ?? '',
  ptzType: String(props.channel?.ptzType ?? 0),
  longitude: props.channel?.longitude,
  latitude: props.channel?.latitude,
})

const form = reactive<FormState>(buildForm())

watch(
  () => props.channel?.id,
  () => {
    Object.assign(form, buildForm())
    errors.deviceId = ''
    errors.channelId = ''
    errors.name = ''
    stopPlay()
  },
)

const channelName = computed(
  () => props.channel?.name || props.channel?.reportName || props.channel?.channelId || '',
)

const errors = reactive({ deviceId: '', channelId: '', name: '' })

const validate = () => {
  errors.deviceId = ''
  errors.channelId = ''
  errors.name = ''
  let ok = true

  const devId = form.deviceId.trim()
  if (!devId) {
    errors.deviceId = '请输入设备国标编码'
    ok = false
  } else if (!/^\d{20}$/.test(devId)) {
    errors.deviceId = '设备国标编码为 20 位数字'
    ok = false
  }

  const cid = form.channelId.trim()
  if (!cid) {
    errors.channelId = '请输入通道国标编码'
    ok = false
  } else if (!/^\d{20}$/.test(cid)) {
    errors.channelId = '通道国标编码为 20 位数字'
    ok = false
  }

  if (!form.name.trim()) {
    errors.name = '请输入通道名称'
    ok = false
  }
  return ok
}

/** number 输入框清空时 v-model.number 会得到空串，统一归一为 undefined */
const normNum = (v: number | undefined) =>
  typeof v === 'number' && !Number.isNaN(v) ? v : undefined

const saving = ref(false)

const onSave = async () => {
  if (!validate()) return

  const payload = {
    deviceId: form.deviceId.trim(),
    channelId: form.channelId.trim(),
    name: form.name.trim(),
    manufacturer: form.manufacturer.trim(),
    model: form.model.trim(),
    owner: form.owner.trim(),
    civilCode: form.civilCode.trim(),
    address: form.address.trim(),
    ptzType: Number(form.ptzType),
    longitude: normNum(form.longitude),
    latitude: normNum(form.latitude),
  }

  saving.value = true
  try {
    if (props.creating) {
      await saveGbDeviceChannelApi(payload)
      toast.success('新增成功')
      emit('created')
    } else if (props.channel) {
      await updateGbDeviceChannelApi({ id: props.channel.id, ...payload })
      toast.success('修改成功')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.channel) return
  const ok = await confirm({
    title: `确定要删除通道 "${props.channel.channelId}" 吗？`,
    description: '此操作不可恢复。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteGbDeviceChannelApi(props.channel.id)
  toast.success('删除成功')
  emit('deleted', props.channel)
}

/* ---------------- 视频点播 ---------------- */

const playLoading = ref(false)
const playUrl = ref('')
const playError = ref('')
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')
/** 后端本次点播会话，停止时回传 */
let playSession: GbPlayResult | null = null

const startPlay = async () => {
  if (!props.channel || playLoading.value) return
  playLoading.value = true
  playError.value = ''
  try {
    const { data } = await startGbPlayApi({
      deviceId: props.channel.deviceId ?? '',
      channelId: props.channel.channelId,
      transport: transport.value,
    })
    playSession = data
    // 优先 WS-FLV（走 TCP，专网/NAT 穿透好），其次 HTTP-FLV
    const url = data.wsFlvUrl || data.flvUrl
    if (!url) {
      playError.value = '后端未返回播放地址'
      return
    }
    playUrl.value = url
  } catch (e) {
    playError.value = e instanceof Error ? e.message : '点播失败'
  } finally {
    playLoading.value = false
  }
}

const stopPlay = () => {
  const session = playSession
  playSession = null
  playUrl.value = ''
  playError.value = ''
  if (session) {
    stopGbPlayApi({ streamId: session.streamId }).catch(() => {})
  }
}

const onPlayerError = (message: string) => {
  playError.value = message
}

const onPlayerTimeout = () => {
  playError.value = '拉流超时，已尝试重连；若持续失败请检查设备或改用 TCP'
}

const onStreamEnd = () => {
  playUrl.value = ''
  playSession = null
}

/** 切换收流模式：停掉当前会话后用新模式重新点播（保持播放器挂载，不退出全屏） */
const onSwitchTransport = (value: 'UDP' | 'TCP-passive' | 'TCP-active') => {
  transport.value = value
  if (playSession) {
    const session = playSession
    playSession = null
    stopGbPlayApi({ streamId: session.streamId })
      .catch(() => {})
      .then(() => startPlay())
  }
}

onBeforeUnmount(stopPlay)

/* ---------------- 大窗口监控 ---------------- */
const monitorOpen = ref(false)

const onOpenMonitor = () => {
  // 不停止当前流：大窗口直接复用共享流，避免 stop→start 竞态，也不影响其他观看者
  monitorOpen.value = true
}

/* ---------------- 回放 ---------------- */
const playbackOpen = ref(false)

const onOpenPlayback = () => {
  // 回放是独立的录像会话（与实时点播不冲突），无需停止当前点播
  playbackOpen.value = true
}

/* ---------------- 展示辅助 ---------------- */
const statusText = (ch: GbDeviceChannel) => {
  if (ch.parental === 1) return '—'
  if (ch.status === 'ON') return '在线'
  if (ch.status === 'OFF') return '离线'
  return '未注册'
}

const SOURCE_TEXT: Record<number, string> = {
  1: '系统新增',
  2: '表格导入',
  3: '设备注册',
  4: '级联平台(历史)',
}
const sourceText = (source?: number) =>
  source == null ? '—' : SOURCE_TEXT[source] ?? '—'

const ptzText = (ptz?: number) =>
  PTZ_OPTIONS.find((o) => o.value === ptz)?.label ?? '未知'
</script>
