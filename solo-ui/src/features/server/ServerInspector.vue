<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconServer class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新建服务器' : server?.serverName }}
        </div>
        <div v-if="!creating" class="truncate font-mono text-xs text-muted-foreground">
          {{ server?.host }}:{{ server?.port }}
        </div>
        <div v-else class="text-xs text-muted-foreground">填写服务器连接信息</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 基本信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '服务器信息' : '基本信息' }}</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.serverName"
              class="h-8 text-xs"
              placeholder="请输入服务器名称"
              @blur="errors.serverName = !form.serverName.trim() ? '请输入服务器名称' : ''"
            />
            <p v-if="errors.serverName" class="mt-1 text-[11px] text-destructive">
              {{ errors.serverName }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            主机<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.host"
              class="h-8 text-xs"
              placeholder="请输入IP或域名"
              @blur="errors.host = !form.host.trim() ? '请输入IP或域名' : ''"
            />
            <p v-if="errors.host" class="mt-1 text-[11px] text-destructive">
              {{ errors.host }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            端口<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model.number="form.port"
              type="number"
              min="1"
              max="65535"
              class="h-8 w-28 text-xs"
              placeholder="22"
              @blur="validatePort"
            />
            <p v-if="errors.port" class="mt-1 text-[11px] text-destructive">
              {{ errors.port }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            用户<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.username"
              class="h-8 text-xs"
              placeholder="请输入登录用户名"
              @blur="errors.username = !form.username.trim() ? '请输入登录用户名' : ''"
            />
            <p v-if="errors.username" class="mt-1 text-[11px] text-destructive">
              {{ errors.username }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">认证</Label>
          <Select v-model="form.authType">
            <SelectTrigger class="h-8 w-28 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PASSWORD">
                <span class="flex items-center gap-1.5">
                  <IconLock class="h-3.5 w-3.5" />
                  密码
                </span>
              </SelectItem>
              <SelectItem value="PRIVATE_KEY">
                <span class="flex items-center gap-1.5">
                  <IconKey class="h-3.5 w-3.5" />
                  密钥
                </span>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- 凭据提示：编辑时不回显 -->
        <div
          v-if="!creating"
          class="flex items-start gap-2 rounded-md border border-border bg-muted/50 px-3 py-2 text-[11px] leading-relaxed text-muted-foreground"
        >
          <IconInfoCircle class="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>认证信息不会回显；{{ form.authType === 'PASSWORD' ? '密码' : '私钥' }}留空表示保持原认证信息不变。</span>
        </div>

        <!-- 密码 -->
        <div v-if="form.authType === 'PASSWORD'" class="flex items-start gap-3">
          <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">
            密码<span v-if="creating" class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.password"
              type="password"
              class="h-8 text-xs"
              autocomplete="new-password"
              :placeholder="creating ? '请输入登录密码' : '留空表示不修改'"
              @blur="errors.auth = creating && !form.password ? '请输入登录密码' : ''"
            />
            <p v-if="errors.auth" class="mt-1 text-[11px] text-destructive">
              {{ errors.auth }}
            </p>
          </div>
        </div>

        <!-- 私钥 -->
        <div v-else class="flex items-start gap-3">
          <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">
            私钥<span v-if="creating" class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <textarea
              v-model="form.privateKey"
              rows="5"
              class="min-h-24 w-full rounded-md border border-input bg-transparent px-2 py-1.5 font-mono text-[11px] leading-relaxed outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
              :placeholder="creating ? '请粘贴私钥内容（-----BEGIN ...）' : '留空表示不修改'"
              @blur="errors.auth = creating && !form.privateKey?.trim() ? '请输入私钥内容' : ''"
            />
            <p v-if="errors.auth" class="mt-1 text-[11px] text-destructive">
              {{ errors.auth }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">系统</Label>
          <Input
            v-model="form.osType"
            class="h-8 w-28 text-xs"
            placeholder="如 Linux"
          />
        </div>

        <div v-if="!creating" class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">状态</Label>
          <Switch
            :model-value="form.status === 1"
            @update:model-value="(v: boolean) => (form.status = v ? 1 : 0)"
          />
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-14 shrink-0 text-xs text-muted-foreground">描述</Label>
          <textarea
            v-model="form.description"
            rows="2"
            class="min-h-12 w-full flex-1 rounded-md border border-input bg-transparent px-2 py-1.5 text-xs outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/15"
            placeholder="请输入描述"
          />
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建服务器' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 终端入口 / 危险操作（仅已有服务器） -->
    <template v-if="!creating && server">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">连接操作</h4>
        <Button
          v-access="PERM.SERVER_TERMINAL"
          class="w-full gap-1.5"
          size="sm"
          @click="emit('terminal', server)"
        >
          <IconTerminal class="h-4 w-4" />
          打开 Web 终端
        </Button>
      </section>

      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.SERVER_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该服务器
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconServer,
  IconX,
  IconLock,
  IconKey,
  IconTerminal,
  IconTrash,
  IconInfoCircle,
} from '@tabler/icons-vue'
import type { SysServer, ServerRequest } from '@/types/api'
import { addServerApi, editServerApi, deleteServerApi } from '@/api/server'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Label from '@/components/ui/label/Label.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const props = defineProps<{ server?: SysServer; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [server: SysServer]
  terminal: [server: SysServer]
}>()

const buildForm = (): ServerRequest => ({
  serverName: props.server?.serverName ?? '',
  host: props.server?.host ?? '',
  port: props.server?.port ?? 22,
  username: props.server?.username ?? '',
  authType: (props.server?.authType as ServerRequest['authType']) ?? 'PASSWORD',
  password: '',
  privateKey: '',
  osType: props.server?.osType ?? '',
  status: props.server?.status ?? 1,
  description: props.server?.description ?? '',
})

const form = reactive<ServerRequest>(buildForm())
const saving = ref(false)
const errors = reactive<{ serverName: string; host: string; port: string; username: string; auth: string }>({
  serverName: '',
  host: '',
  port: '',
  username: '',
  auth: '',
})

watch(
  () => props.server?.id,
  () => {
    Object.assign(form, buildForm())
    errors.serverName = ''
    errors.host = ''
    errors.port = ''
    errors.username = ''
    errors.auth = ''
  },
)

const validatePort = () => {
  errors.port =
    !form.port || form.port < 1 || form.port > 65535 ? '请输入 1~65535 的端口' : ''
}

const validate = (): boolean => {
  errors.serverName = form.serverName.trim() ? '' : '请输入服务器名称'
  errors.host = form.host.trim() ? '' : '请输入IP或域名'
  validatePort()
  errors.username = form.username.trim() ? '' : '请输入登录用户名'
  if (props.creating) {
    if (form.authType === 'PASSWORD') {
      errors.auth = form.password ? '' : '请输入登录密码'
    } else {
      errors.auth = form.privateKey?.trim() ? '' : '请输入私钥内容'
    }
  } else {
    errors.auth = ''
  }
  return !errors.serverName && !errors.host && !errors.port && !errors.username && !errors.auth
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addServerApi({
        ...form,
        serverName: form.serverName.trim(),
        host: form.host.trim(),
        username: form.username.trim(),
      })
      toast.success('服务器创建成功')
      emit('created')
    } else if (props.server) {
      // 编辑时凭据留空不下发，后端保持原值
      const payload: ServerRequest = { ...form }
      if (payload.authType === 'PASSWORD') {
        payload.password = payload.password || undefined
        payload.privateKey = undefined
      } else {
        payload.privateKey = payload.privateKey?.trim() || undefined
        payload.password = undefined
      }
      await editServerApi(props.server.id, payload)
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
    title: '删除服务器',
    description: `确认删除服务器「${props.server.serverName}」？配置删除后不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteServerApi(props.server.id)
  toast.success('服务器已删除')
  emit('deleted', props.server)
}
</script>
