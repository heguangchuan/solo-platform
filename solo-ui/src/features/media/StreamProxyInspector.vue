<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconShare class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增拉流代理' : `${proxy?.app}/${proxy?.stream}` }}
        </div>
        <div v-if="creating" class="text-xs text-muted-foreground">
          代理一路外部源流到媒体节点
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 代理信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">代理信息</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">
            应用名<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.app"
              class="h-8 font-mono text-xs"
              placeholder="ZLM app，默认 proxy"
            />
            <p v-if="errors.app" class="mt-1 text-[11px] text-destructive">{{ errors.app }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">
            流标识<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.stream"
              class="h-8 font-mono text-xs"
              placeholder="ZLM stream 名，唯一标识一路流"
            />
            <p v-if="errors.stream" class="mt-1 text-[11px] text-destructive">{{ errors.stream }}</p>
          </div>
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-16 shrink-0 text-xs text-muted-foreground">
            源地址<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <textarea
              v-model="form.srcUrl"
              rows="2"
              class="min-h-14 w-full rounded-md border border-input bg-transparent px-2 py-1.5 font-mono text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
              placeholder="rtsp:// rtmp:// http://...flv http://...m3u8"
            />
            <p v-if="errors.srcUrl" class="mt-1 text-[11px] text-destructive">{{ errors.srcUrl }}</p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">目标节点</Label>
          <div class="flex-1">
            <Select v-model="form.mediaServerId">
              <SelectTrigger class="h-8 w-full text-xs">
                <SelectValue placeholder="留空自动挑选" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__auto__">自动挑选</SelectItem>
                <SelectItem
                  v-for="s in servers"
                  :key="s.serverId"
                  :value="s.serverId"
                >
                  {{ s.name || s.serverId }}（{{ s.hostIp }}:{{ s.httpPort }}）
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-16 shrink-0 text-xs text-muted-foreground">备注</Label>
          <textarea
            v-model="form.remark"
            rows="2"
            class="min-h-14 w-full rounded-md border border-input bg-transparent px-2 py-1.5 text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
            placeholder="备注（可选）"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建代理' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 运行状态 -->
    <template v-if="!creating && proxy">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">运行状态</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">代理状态</span>
            <span :class="proxy.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
              {{ proxy.status === 1 ? '运行中' : '已停止' }}
            </span>
          </div>
          <div v-if="proxy.status !== 1 && proxy.lastError" class="flex items-start justify-between gap-3">
            <span class="shrink-0 text-muted-foreground">错误原因</span>
            <span class="text-right text-destructive">{{ proxy.lastError }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">更新时间</span>
            <span class="font-mono">{{ proxy.updateTime || '—' }}</span>
          </div>
        </div>

        <div class="mt-3 flex gap-2">
          <Button
            v-if="proxy.status === 1"
            variant="outline"
            size="sm"
            class="flex-1"
            @click="emit('disable', proxy)"
          >
            <IconPlayerStop class="size-3.5" />
            停用
          </Button>
          <Button
            v-else
            size="sm"
            class="flex-1"
            @click="emit('enable', proxy)"
          >
            <IconPlayerPlay class="size-3.5" />
            启用
          </Button>
        </div>
      </section>

      <!-- 危险操作 -->
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.MEDIA_STREAM_PROXY_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该代理
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconShare,
  IconX,
  IconTrash,
  IconPlayerPlay,
  IconPlayerStop,
} from '@tabler/icons-vue'
import type {
  MediaServer,
  StreamProxy,
  StreamProxyRequest,
} from '@/types/api'
import {
  saveStreamProxyApi,
  updateStreamProxyApi,
  deleteStreamProxyApi,
} from '@/api/media'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const props = defineProps<{
  proxy?: StreamProxy
  servers: MediaServer[]
  creating?: boolean
}>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [proxy: StreamProxy]
  enable: [proxy: StreamProxy]
  disable: [proxy: StreamProxy]
}>()

const STREAM_RE = /^[A-Za-z0-9_\-.]+$/

const buildForm = (): StreamProxyRequest & { mediaServerId: string } => ({
  id: props.proxy?.id,
  app: props.proxy?.app ?? 'proxy',
  stream: props.proxy?.stream ?? '',
  srcUrl: props.proxy?.srcUrl ?? '',
  mediaServerId: props.proxy?.mediaServerId ?? '__auto__',
  rtpType: props.proxy?.rtpType ?? 0,
  remark: props.proxy?.remark ?? '',
})

const form = reactive(buildForm())
const saving = ref(false)
const errors = reactive<{ app: string; stream: string; srcUrl: string }>({
  app: '',
  stream: '',
  srcUrl: '',
})

watch(
  () => props.proxy?.id,
  () => {
    Object.assign(form, buildForm())
    errors.app = ''
    errors.stream = ''
    errors.srcUrl = ''
  },
)

const validate = (): boolean => {
  errors.app = form.app.trim() ? '' : '请输入应用名'
  errors.stream = !form.stream.trim()
    ? '请输入流标识'
    : STREAM_RE.test(form.stream)
      ? ''
      : '只允许字母/数字/下划线/中划线/点'
  errors.srcUrl = form.srcUrl.trim() ? '' : '请输入源地址'
  return !errors.app && !errors.stream && !errors.srcUrl
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    const payload = {
      ...form,
      app: form.app.trim(),
      stream: form.stream.trim(),
      srcUrl: form.srcUrl.trim(),
      mediaServerId: form.mediaServerId === '__auto__' ? '' : form.mediaServerId,
    }
    if (props.creating) {
      await saveStreamProxyApi(payload)
      toast.success('代理创建成功')
      emit('created')
    } else if (props.proxy) {
      await updateStreamProxyApi({ ...payload, id: props.proxy.id })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.proxy) return
  const ok = await confirm({
    title: '删除拉流代理',
    description: `确认删除代理「${props.proxy.app}/${props.proxy.stream}」？删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteStreamProxyApi(props.proxy.id)
  toast.success('代理已删除')
  emit('deleted', props.proxy)
}
</script>
