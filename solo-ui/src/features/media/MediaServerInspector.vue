<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconServer2 class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增节点' : (server?.name || server?.serverId) }}
        </div>
        <div v-if="!creating" class="truncate font-mono text-xs text-muted-foreground">
          {{ server?.serverId }}
        </div>
        <div v-else class="text-xs text-muted-foreground">接入 ZLMediaKit 节点</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 节点信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '节点信息' : '基本信息' }}</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            节点标识<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.serverId"
              class="h-8 font-mono text-xs"
              :disabled="!creating"
              placeholder="与 ZLM mediaServerId 一致"
            />
            <p v-if="errors.serverId" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverId }}
            </p>
            <p v-else-if="!creating" class="mt-1 text-[11px] text-muted-foreground">
              节点标识创建后不可修改
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">节点名称</Label>
          <Input
            v-model="form.name"
            class="h-8 text-xs"
            placeholder="节点显示名（可选）"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            管理地址<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.hostIp"
              class="h-8 font-mono text-xs"
              placeholder="ZLM 管理面 IP，用于 REST API"
            />
            <p v-if="errors.hostIp" class="mt-1 text-[11px] text-destructive">
              {{ errors.hostIp }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">
            管理端口<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model.number="form.httpPort"
              type="number"
              min="1"
              max="65535"
              class="h-8 font-mono text-xs"
            />
            <p v-if="errors.httpPort" class="mt-1 text-[11px] text-destructive">
              {{ errors.httpPort }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">接口密钥</Label>
          <Input
            v-model="form.secret"
            class="h-8 font-mono text-xs"
            placeholder="与 ZLM [api].secret 一致"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">播放地址</Label>
          <Input
            v-model="form.streamIp"
            class="h-8 font-mono text-xs"
            placeholder="留空则同管理地址"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-20 shrink-0 whitespace-nowrap text-xs text-muted-foreground">端口范围</Label>
          <div class="flex flex-1 items-center gap-2">
            <Input
              v-model.number="form.rtpPortStart"
              type="number"
              min="1024"
              max="65535"
              class="h-8 flex-1 font-mono text-xs"
            />
            <span class="text-xs text-muted-foreground">-</span>
            <Input
              v-model.number="form.rtpPortEnd"
              type="number"
              min="1024"
              max="65535"
              class="h-8 flex-1 font-mono text-xs"
            />
          </div>
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建节点' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 非编辑态：状态与心跳 -->
    <template v-if="!creating && server">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">运行状态</h4>
        <div class="space-y-2 text-xs">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">节点状态</span>
            <span :class="server.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
              {{ server.status === 1 ? '在线' : '离线' }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">最近心跳</span>
            <span class="font-mono">{{ server.hookAliveTime || '—' }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">更新时间</span>
            <span class="font-mono">{{ server.updateTime || '—' }}</span>
          </div>
        </div>
      </section>

      <!-- 危险操作 -->
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.MEDIA_SERVER_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该节点
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { IconServer2, IconX, IconTrash } from '@tabler/icons-vue'
import type { MediaServer, MediaServerRequest } from '@/types/api'
import { saveMediaServerApi, deleteMediaServerApi } from '@/api/media'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'

const props = defineProps<{ server?: MediaServer; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [server: MediaServer]
}>()

const buildForm = (): MediaServerRequest => ({
  id: props.server?.id,
  serverId: props.server?.serverId ?? '',
  name: props.server?.name ?? '',
  hostIp: props.server?.hostIp ?? '',
  httpPort: props.server?.httpPort ?? 80,
  secret: props.server?.secret ?? '',
  streamIp: props.server?.streamIp ?? '',
  rtpPortStart: props.server?.rtpPortStart ?? 30000,
  rtpPortEnd: props.server?.rtpPortEnd ?? 35000,
})

const form = reactive<MediaServerRequest>(buildForm())
const saving = ref(false)
const errors = reactive<{ serverId: string; hostIp: string; httpPort: string }>({
  serverId: '',
  hostIp: '',
  httpPort: '',
})

watch(
  () => props.server?.id,
  () => {
    Object.assign(form, buildForm())
    errors.serverId = ''
    errors.hostIp = ''
    errors.httpPort = ''
  },
)

const validate = (): boolean => {
  errors.serverId = form.serverId.trim() ? '' : '请输入节点标识'
  errors.hostIp = form.hostIp.trim() ? '' : '请输入管理地址'
  errors.httpPort = form.httpPort >= 1 && form.httpPort <= 65535 ? '' : '端口范围 1 - 65535'
  return !errors.serverId && !errors.hostIp && !errors.httpPort
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await saveMediaServerApi({ ...form, serverId: form.serverId.trim() })
      toast.success('节点创建成功')
      emit('created')
    } else if (props.server) {
      await saveMediaServerApi({ ...form, id: props.server.id })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.server) return
  const ok = await confirm({
    title: '删除节点',
    description: `确认删除节点「${props.server.serverId}」？删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteMediaServerApi(props.server.id)
  toast.success('节点已删除')
  emit('deleted', props.server)
}
</script>
