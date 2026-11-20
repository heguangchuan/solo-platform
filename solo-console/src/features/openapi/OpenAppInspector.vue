<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconKey class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新增应用' : (app?.appName || app?.appKey) }}
        </div>
        <div v-if="!creating" class="truncate font-mono text-xs text-muted-foreground">
          {{ app?.appKey }}
        </div>
        <div v-else class="text-xs text-muted-foreground">给第三方系统签发调用凭证</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 创建/换钥成功：明文密钥只展示这一次 -->
    <section v-if="createdSecret || rotatedSecret" class="mb-4">
      <div class="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3">
        <div class="flex items-center gap-1.5 text-[13px] font-medium text-amber-600 dark:text-amber-400">
          <IconAlertTriangle class="h-4 w-4" />
          请立即复制保存，关闭后无法再次查看
        </div>
        <div class="mt-3 space-y-2">
          <SecretRow
            v-if="createdSecret"
            label="appKey"
            :value="createdSecret.appKey"
          />
          <SecretRow
            label="appSecret"
            :value="createdSecret?.appSecret || rotatedSecret"
          />
          <SecretRow
            v-if="createdSecret?.callbackSecret"
            label="回调签名密钥"
            :value="createdSecret.callbackSecret"
          />
        </div>
      </div>
      <div class="mt-3 flex justify-end">
        <Button size="sm" variant="outline" @click="emit('close')">我已妥善保存</Button>
      </div>
    </section>

    <template v-else>
      <!-- 基本信息 -->
      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '应用信息' : '基本信息' }}</h4>
        <div class="space-y-3">
          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">
              应用名称<span class="ml-0.5 text-destructive">*</span>
            </Label>
            <div class="min-w-0 flex-1">
              <Input v-model="form.appName" class="h-8 text-xs" placeholder="便于后台识别，如 XX 小区平台" />
              <p v-if="errors.appName" class="mt-1 text-[11px] text-destructive">{{ errors.appName }}</p>
            </div>
          </div>

          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">授权范围</Label>
            <div class="min-w-0 flex-1">
              <OpenAppScopeSelect v-model="form.scopes" />
              <p class="mt-1 text-[11px] text-muted-foreground">
                来自开放接口注解，留空则无任何接口权限
              </p>
            </div>
          </div>

          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">IP 白名单</Label>
            <div class="min-w-0 flex-1">
              <textarea
                v-model="form.ipWhitelist"
                rows="2"
                class="w-full resize-none rounded-md border border-border bg-transparent px-2 py-1.5 font-mono text-xs outline-none focus:bg-muted/70"
                placeholder="203.0.113.10"
              />
              <p v-if="form.ipWhitelist?.trim()" class="mt-1 text-[11px] text-muted-foreground">
                多个用逗号分隔；整段网段可写 CIDR，如 203.0.113.0/24。只接受字面量 IP，不支持域名
              </p>
              <p v-else class="mt-1 text-[11px] text-amber-500">
                未限制来源 IP：密钥一旦泄露，任何地址都能调用
              </p>
            </div>
          </div>
        </div>
      </section>

      <div class="my-4 h-px bg-border" />

      <!-- 回调与配额 -->
      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">回调与配额</h4>
        <div class="space-y-3">
          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">回调地址</Label>
            <Input
              v-model="form.callbackUrl"
              class="h-8 flex-1 font-mono text-xs"
              placeholder="https://对方域名/webhook，留空不推送"
            />
          </div>

          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">订阅事件</Label>
            <Input
              v-model="form.eventSubscriptions"
              class="h-8 flex-1 font-mono text-xs"
              placeholder="device.online,device.offline"
            />
          </div>

          <div class="flex items-center gap-3">
            <Label class="w-20 shrink-0 text-xs text-muted-foreground">QPS 上限</Label>
            <Input
              v-model.number="form.qpsLimit"
              type="number"
              min="0"
              class="h-8 w-32 font-mono text-xs"
            />
            <span class="text-[11px] text-muted-foreground">0 表示不限</span>
          </div>

          <div class="flex items-center gap-3">
            <Label class="w-20 shrink-0 text-xs text-muted-foreground">日调用量</Label>
            <Input
              v-model.number="form.dailyLimit"
              type="number"
              min="0"
              class="h-8 w-32 font-mono text-xs"
            />
            <span class="text-[11px] text-muted-foreground">0 表示不限</span>
          </div>

          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">过期时间</Label>
            <div class="min-w-0 flex-1">
              <Input
                v-model="form.expireTime"
                class="h-8 font-mono text-xs"
                placeholder="yyyy-MM-dd HH:mm:ss"
              />
              <p class="mt-1 text-[11px] text-muted-foreground">留空永不过期</p>
            </div>
          </div>

          <div class="flex gap-3">
            <Label class="w-20 shrink-0 pt-1.5 text-xs text-muted-foreground">备注</Label>
            <Input v-model="form.remark" class="h-8 flex-1 text-xs" placeholder="备注（可选）" />
          </div>
        </div>
      </section>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button v-access="creating ? PERM.OPENAPI_APP_ADD : PERM.OPENAPI_APP_EDIT" size="sm" :loading="saving" @click="onSave">
          保存
        </Button>
      </div>

      <!-- 已有应用：状态与密钥操作 -->
      <template v-if="!creating && app">
        <div class="my-4 h-px bg-border" />

        <section>
          <h4 class="mb-3.5 text-[13px] font-medium">运行状态</h4>
          <div class="space-y-2 text-xs">
            <div class="flex items-center justify-between">
              <span class="text-muted-foreground">当前状态</span>
              <span :class="app.status === 1 ? 'text-emerald-500' : 'text-muted-foreground'">
                {{ app.status === 1 ? '启用中' : '已停用' }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-muted-foreground">过期时间</span>
              <span class="font-mono">{{ app.expireTime || '永不过期' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-muted-foreground">回调签名密钥</span>
              <span>{{ app.callbackSecretSet ? '已配置' : '未配置' }}</span>
            </div>
            <div v-if="app.oldSecretExpireTime" class="flex items-center justify-between">
              <span class="text-muted-foreground">旧密钥失效时间</span>
              <span class="font-mono text-amber-500">{{ app.oldSecretExpireTime }}</span>
            </div>
          </div>
        </section>

        <div class="my-4 h-px bg-border" />

        <section class="space-y-2">
          <h4 class="mb-1 text-[13px] font-medium">密钥与启停</h4>
          <Button
            v-access="PERM.OPENAPI_APP_ROTATE"
            variant="outline"
            size="sm"
            class="w-full"
            :loading="rotating"
            @click="onRotate"
          >
            <IconRefresh class="h-3.5 w-3.5" />
            更换接口密钥（旧密钥保留过渡期）
          </Button>

          <Button
            v-if="app.status === 1"
            v-access="PERM.OPENAPI_APP_EDIT"
            variant="outline"
            size="sm"
            class="w-full"
            @click="onToggle(false)"
          >
            <IconBan class="h-3.5 w-3.5" />
            停用应用
          </Button>
          <Button
            v-else
            v-access="PERM.OPENAPI_APP_EDIT"
            size="sm"
            class="w-full"
            @click="onToggle(true)"
          >
            <IconCircleCheck class="h-3.5 w-3.5" />
            启用应用
          </Button>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconKey,
  IconX,
  IconAlertTriangle,
  IconRefresh,
  IconBan,
  IconCircleCheck,
} from '@tabler/icons-vue'
import SecretRow from './SecretRow.vue'
import OpenAppScopeSelect from './OpenAppScopeSelect.vue'
import type { OpenApp, OpenAppCreatedSecret, OpenAppSaveRequest } from '@/types/api'
import {
  createOpenAppApi,
  updateOpenAppApi,
  enableOpenAppApi,
  disableOpenAppApi,
  rotateOpenAppSecretApi,
} from '@/api/openapi'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'

const props = defineProps<{ app?: OpenApp; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  changed: []
}>()

const blank = (): OpenAppSaveRequest => ({
  appName: '',
  scopes: '',
  ipWhitelist: '',
  callbackUrl: '',
  eventSubscriptions: '',
  qpsLimit: 0,
  dailyLimit: 0,
  expireTime: '',
  remark: '',
})

const buildForm = (): OpenAppSaveRequest => {
  if (!props.app) return blank()
  return {
    id: props.app.id,
    appName: props.app.appName ?? '',
    scopes: props.app.scopes ?? '',
    ipWhitelist: props.app.ipWhitelist ?? '',
    callbackUrl: props.app.callbackUrl ?? '',
    eventSubscriptions: props.app.eventSubscriptions ?? '',
    qpsLimit: props.app.qpsLimit ?? 0,
    dailyLimit: props.app.dailyLimit ?? 0,
    expireTime: props.app.expireTime ?? '',
    remark: props.app.remark ?? '',
  }
}

const form = reactive<OpenAppSaveRequest>(buildForm())
const errors = reactive<{ appName: string }>({ appName: '' })
const saving = ref(false)
const rotating = ref(false)
const createdSecret = ref<OpenAppCreatedSecret | null>(null)
const rotatedSecret = ref('')

watch(
  () => props.app?.id,
  () => {
    Object.assign(form, buildForm())
    errors.appName = ''
    rotatedSecret.value = ''
  },
)

const onSave = async () => {
  errors.appName = form.appName.trim() ? '' : '请输入应用名称'
  if (errors.appName) return
  saving.value = true
  try {
    if (props.creating) {
      // 创建成功后停在本面板展示明文密钥，由用户确认后再关闭刷新
      createdSecret.value = (await createOpenAppApi({ ...form, appName: form.appName.trim() })).data
      emit('changed')
    } else if (props.app) {
      await updateOpenAppApi({ ...form, id: props.app.id })
      toast.success('已保存')
      emit('changed')
    }
  } finally {
    saving.value = false
  }
}

const onRotate = async () => {
  if (!props.app) return
  const ok = await confirm({
    title: '更换接口密钥',
    description: '旧密钥会保留一段过渡期，到期后自动失效。确认现在换钥？',
    confirmText: '换钥',
  })
  if (!ok) return
  rotating.value = true
  try {
    const res = await rotateOpenAppSecretApi(props.app.id)
    rotatedSecret.value = res.data.appSecret
    toast.success('换钥成功')
    emit('changed')
  } finally {
    rotating.value = false
  }
}

const onToggle = async (enable: boolean) => {
  if (!props.app) return
  const ok = await confirm({
    title: enable ? '启用应用' : '停用应用',
    description: enable
      ? `确认启用「${props.app.appName}」？`
      : `停用后该应用的所有接口调用会立即被拒绝，确认停用「${props.app.appName}」？`,
    confirmText: enable ? '启用' : '停用',
    danger: !enable,
  })
  if (!ok) return
  if (enable) await enableOpenAppApi(props.app.id)
  else await disableOpenAppApi(props.app.id)
  toast.success(enable ? '已启用' : '已停用')
  emit('changed')
}
</script>
