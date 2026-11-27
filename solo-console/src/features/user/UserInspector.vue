<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <Avatar :size="44">
        <AvatarImage v-if="user?.avatar" :src="user.avatar" />
        <AvatarFallback>
          {{ (creating ? (form.username || '新') : user?.nickname || '?').charAt(0).toUpperCase() }}
        </AvatarFallback>
      </Avatar>
      <div class="min-w-0 flex-1">
        <div class="text-base font-semibold">{{ creating ? '新建用户' : user?.nickname }}</div>
        <div class="mono text-xs text-muted-foreground">
          {{ creating ? '填写账号信息' : `@${user?.username}` }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="$emit('close')">
        <X class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 基本信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '账号信息' : '基本信息' }}</h4>

      <div
        v-if="creating"
        class="mb-3.5 rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
      >
        请设置初始密码，用户首次登录后必须修改。
      </div>

      <div class="space-y-3">
        <div v-if="creating" class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            账号<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.username"
              class="h-8 text-xs"
              placeholder="请输入登录账号"
              @blur="errors.username = !form.username?.trim() ? '请输入登录账号' : ''"
            />
            <p v-if="errors.username" class="mt-1 text-[11px] text-destructive">
              {{ errors.username }}
            </p>
          </div>
        </div>

        <div v-if="creating" class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            初始密码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.initialPassword"
              type="password"
              class="h-8 text-xs"
              placeholder="8-32 位，四类字符至少包含三类"
              autocomplete="new-password"
            />
            <p v-if="errors.initialPassword" class="mt-1 text-[11px] text-destructive">
              {{ errors.initialPassword }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            昵称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.nickname"
              class="h-8 text-xs"
              placeholder="请输入昵称"
              @blur="errors.nickname = !form.nickname.trim() ? '请输入昵称' : ''"
            />
            <p v-if="errors.nickname" class="mt-1 text-[11px] text-destructive">
              {{ errors.nickname }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">邮箱</Label>
          <div class="flex-1">
            <Input
              v-model="form.email"
              class="h-8 text-xs"
              placeholder="请输入邮箱地址"
              @blur="validateEmail"
            />
            <p v-if="errors.email" class="mt-1 text-[11px] text-destructive">
              {{ errors.email }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">手机</Label>
          <div class="flex-1">
            <Input v-model="form.phone" class="h-8 flex-1 text-xs" placeholder="请输入手机号码" />
          </div>
        </div>

        <div v-if="!creating" class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">状态</Label>
          <Switch
            :model-value="form.status === 1"
            @update:model-value="(v: boolean) => (form.status = v ? 1 : 0)"
          />
        </div>
      </div>
    </section>

    <section class="mt-4">
      <div class="flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="$emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">保存</Button>
      </div>
    </section>

    <!-- 危险操作（仅编辑已有用户） -->
    <template v-if="!creating && user">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDelete"
        >
          <Trash2 class="h-3.5 w-3.5" />
          删除该用户
        </Button>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { X, Trash2 } from 'lucide-vue-next'
import type { SysUser, UserRequest } from '@/types/api'
import { addUserApi, editUserApi, deleteUserApi } from '@/api/user'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Label from '@/components/ui/label/Label.vue'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import AvatarImage from '@/components/ui/avatar/AvatarImage.vue'
import AvatarFallback from '@/components/ui/avatar/AvatarFallback.vue'

const props = defineProps<{ user?: SysUser; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [user: SysUser]
}>()

const form = reactive<UserRequest>({
  username: props.user?.username ?? '',
  initialPassword: '',
  nickname: props.user?.nickname ?? '',
  email: props.user?.email ?? '',
  phone: props.user?.phone ?? '',
  avatar: props.user?.avatar ?? '',
  status: props.user?.status ?? 1,
})
const saving = ref(false)
const errors = reactive({
  username: '',
  initialPassword: '',
  nickname: '',
  email: '',
})

const validateEmail = () => {
  errors.email =
    form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '邮箱格式不正确' : ''
}

const isStrong = (value: string) => {
  if (value.length < 8 || value.length > 32 || /\s/.test(value)) return false
  const kinds = [/[A-Z]/, /[a-z]/, /\d/, /[^A-Za-z0-9]/]
  return kinds.filter((pattern) => pattern.test(value)).length >= 3
}

watch(
  () => props.user?.id,
  () => {
    if (props.creating || !props.user) {
      Object.assign(form, {
        username: '',
        initialPassword: '',
        nickname: '',
        email: '',
        phone: '',
        avatar: '',
        status: 1,
      })
      errors.username = ''
      errors.initialPassword = ''
      errors.nickname = ''
      errors.email = ''
      return
    }
    Object.assign(form, {
      username: props.user.username,
      initialPassword: '',
      nickname: props.user.nickname,
      email: props.user.email,
      phone: props.user.phone,
      avatar: props.user.avatar,
      status: props.user.status,
    })
    errors.username = ''
    errors.initialPassword = ''
    errors.nickname = ''
    errors.email = ''
  },
  { immediate: true }
)

const validate = (): boolean => {
  errors.username = form.username?.trim() ? '' : '请输入登录账号'
  errors.initialPassword = props.creating && !isStrong(form.initialPassword || '')
    ? '需 8-32 位且无空格，四类字符至少包含三类'
    : ''
  errors.nickname = form.nickname?.trim() ? '' : '请输入昵称'
  validateEmail()
  return !errors.username && !errors.initialPassword && !errors.nickname && !errors.email
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addUserApi(form)
      toast.success('用户创建成功')
      emit('created')
    } else if (props.user) {
      const payload = { ...form }
      delete payload.initialPassword
      await editUserApi(props.user.id, payload)
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  if (!props.user) return
  const ok = await confirm({
    title: '删除用户',
    description: `删除后该账号无法登录，确认删除「${props.user.nickname}」？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteUserApi(props.user.id)
  toast.success('用户已删除')
  emit('deleted', props.user)
}
</script>
