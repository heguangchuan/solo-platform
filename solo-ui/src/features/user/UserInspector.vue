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
        class="mb-3.5 flex items-start gap-2 rounded-md border border-primary/25 bg-primary/10 px-3 py-2 text-xs leading-relaxed text-foreground"
      >
        <Info class="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
        <span>
          初始密码为「<strong>{{ (form.username || '账号').trim() }}@123456</strong
          >」，首次登录将强制修改密码。
        </span>
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
          <Input v-model="form.phone" class="h-8 flex-1 text-xs" placeholder="请输入手机号码" />
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

    <!-- 角色分配（创建态内联：随保存一并提交） -->
    <section v-if="creating" class="mt-4">
      <h4 class="mb-3.5 text-[13px] font-medium">
        分配角色<span class="ml-0.5 text-destructive">*</span>
      </h4>
      <div v-if="rolesLoading" class="grid place-items-center py-5">
        <svg class="h-5 w-5 animate-spin text-muted-foreground" viewBox="0 0 24 24" fill="none">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
        </svg>
      </div>
      <template v-else>
        <div class="space-y-2.5">
          <label v-for="r in allRoles" :key="r.id" class="flex cursor-pointer items-center gap-2.5 text-xs">
            <Checkbox
              :model-value="roleIds.includes(r.id)"
              @update:model-value="(v: boolean | 'indeterminate') => toggleRole(r.id, v === true)"
            />
            <span>{{ r.roleName }}</span>
          </label>
          <p v-if="allRoles.length === 0" class="text-xs text-muted-foreground">暂无角色</p>
        </div>
        <p v-if="errors.roles" class="mt-1.5 text-[11px] text-destructive">{{ errors.roles }}</p>
        <p class="mt-2 text-[11px] leading-relaxed text-muted-foreground">
          未分配角色的账号创建后将无法登录，至少选择一个角色。
        </p>
      </template>
    </section>

    <section class="mt-4">
      <div class="flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="$emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">保存</Button>
      </div>
    </section>

    <!-- 角色分配 / 危险操作（仅编辑已有用户） -->
    <template v-if="!creating && user">
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium">已分配角色</h4>
        <div v-if="rolesLoading" class="grid place-items-center py-5">
          <svg class="h-5 w-5 animate-spin text-muted-foreground" viewBox="0 0 24 24" fill="none">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
        </div>
        <div v-else class="space-y-2.5">
          <label
            v-for="r in allRoles"
            :key="r.id"
            class="flex items-center gap-2.5 text-xs"
            :class="canAssign ? 'cursor-pointer' : 'cursor-not-allowed opacity-60'"
          >
            <Checkbox
              :model-value="roleIds.includes(r.id)"
              :disabled="!canAssign"
              @update:model-value="(v: boolean | 'indeterminate') => toggleRole(r.id, v === true)"
            />
            <span>{{ r.roleName }}</span>
          </label>
          <p v-if="allRoles.length === 0" class="text-xs text-muted-foreground">暂无角色</p>
        </div>
        <Button
          v-if="canAssign"
          variant="outline"
          size="sm"
          class="mt-3.5"
          :loading="assignLoading"
          @click="onAssignRoles"
        >
          保存角色
        </Button>
        <p v-else class="mt-2 text-[11px] text-muted-foreground">无角色分配权限</p>
      </section>

      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.USER_DELETE"
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
import { computed, reactive, ref, watch } from 'vue'
import { X, Trash2, Info } from 'lucide-vue-next'
import type { SysUser, UserRequest, SysRole } from '@/types/api'
import {
  addUserApi,
  editUserApi,
  deleteUserApi,
  getUserAssignedRolesApi,
  assignUserRolesApi,
} from '@/api/user'
import { getRoleListApi } from '@/api/role'
import { PERM } from '@/features/access/permissions'
import { useAccess } from '@/features/access/useAccess'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
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

const { can } = useAccess()
const canAssign = computed(() => can(PERM.USER_R2U))

const form = reactive<UserRequest>({
  username: props.user?.username ?? '',
  nickname: props.user?.nickname ?? '',
  email: props.user?.email ?? '',
  phone: props.user?.phone ?? '',
  avatar: props.user?.avatar ?? '',
  status: props.user?.status ?? 1,
})
const saving = ref(false)
const errors = reactive<{ username: string; nickname: string; email: string; roles: string }>({
  username: '',
  nickname: '',
  email: '',
  roles: '',
})

const allRoles = ref<SysRole[]>([])
const roleIds = ref<string[]>([])
const rolesLoading = ref(false)
const assignLoading = ref(false)
/* 角色请求序号：防止快速切换用户时旧响应覆盖新数据 */
let roleReqSeq = 0

const validateEmail = () => {
  errors.email =
    form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '邮箱格式不正确' : ''
}

const loadRoles = async () => {
  const reqId = ++roleReqSeq
  rolesLoading.value = true
  try {
    // 创建态只需全部角色；编辑态同时拉取该用户已勾选的角色
    const all = await getRoleListApi()
    if (reqId !== roleReqSeq) return
    allRoles.value = all.data
    if (!props.creating && props.user) {
      const assigned = await getUserAssignedRolesApi(props.user.id)
      if (reqId !== roleReqSeq) return
      // 与 role.id（字符串）归一化后再比对勾选
      roleIds.value = (assigned.data || []).map((id) => String(id))
    }
  } finally {
    if (reqId === roleReqSeq) rolesLoading.value = false
  }
}

watch(
  () => props.user?.id,
  async () => {
    if (props.creating || !props.user) {
      // 使尚未返回的编辑态角色请求失效
      roleReqSeq++
      // 新建：重置为空白表单与空角色
      Object.assign(form, {
        username: '',
        nickname: '',
        email: '',
        phone: '',
        avatar: '',
        status: 1,
      })
      errors.username = ''
      errors.nickname = ''
      errors.email = ''
      errors.roles = ''
      roleIds.value = []
      // 创建态也加载角色列表供勾选
      await loadRoles()
      return
    }
    Object.assign(form, {
      username: props.user.username,
      nickname: props.user.nickname,
      email: props.user.email,
      phone: props.user.phone,
      avatar: props.user.avatar,
      status: props.user.status,
    })
    errors.username = ''
    errors.nickname = ''
    errors.email = ''
    errors.roles = ''
    await loadRoles()
  },
  { immediate: true }
)

const toggleRole = (id: string, checked: boolean) => {
  if (checked) {
    if (!roleIds.value.includes(id)) roleIds.value.push(id)
  } else {
    roleIds.value = roleIds.value.filter((r) => r !== id)
  }
  if (roleIds.value.length > 0) errors.roles = ''
}

const validate = (): boolean => {
  errors.username = form.username?.trim() ? '' : '请输入登录账号'
  errors.nickname = form.nickname?.trim() ? '' : '请输入昵称'
  validateEmail()
  // 创建时必须至少选择一个角色，否则账号无法登录
  if (props.creating && roleIds.value.length === 0) {
    errors.roles = '请至少选择一个角色'
  }
  return !errors.username && !errors.nickname && !errors.email && !errors.roles
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addUserApi({ ...form, roleIds: roleIds.value })
      toast.success('用户创建成功')
      emit('created')
    } else if (props.user) {
      await editUserApi(props.user.id, form)
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onAssignRoles = async () => {
  if (!props.user) return
  assignLoading.value = true
  try {
    await assignUserRolesApi(props.user.id, roleIds.value)
    toast.success('角色已更新')
  } finally {
    assignLoading.value = false
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
