<template>
  <div class="h-full overflow-y-auto">
    <div class="px-6 py-6">
      <div class="flex items-start gap-5">
        <!-- 左侧：个人信息卡 -->
        <section class="w-[300px] shrink-0 rounded-xl border border-border bg-card">
          <header class="border-b border-border px-4 py-3">
            <h2 class="text-sm font-semibold">个人信息</h2>
          </header>
          <div class="px-4 py-4">
            <!-- 头像 -->
            <div class="flex flex-col items-center pb-3.5">
              <Avatar :size="64" class="ring-2 ring-border">
                <AvatarImage v-if="profile.avatar" :src="profile.avatar" />
                <AvatarFallback>{{ (profile.nickname || 'U').charAt(0).toUpperCase() }}</AvatarFallback>
              </Avatar>
              <p class="mt-2 text-sm font-medium">{{ profile.nickname }}</p>
              <p class="text-[11px] text-muted-foreground">@{{ profile.username }}</p>
            </div>

            <div class="h-px bg-border" />

            <!-- 个人信息 -->
            <div class="pt-3.5">
              <p class="mb-2.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">个人信息</p>
              <ul class="space-y-2.5">
                <li class="flex items-center justify-between text-xs">
                  <span class="flex items-center gap-1.5 text-muted-foreground">
                    <UserRound class="h-3.5 w-3.5" />
                    登录账号
                  </span>
                  <span class="font-mono">{{ profile.username || '—' }}</span>
                </li>
                <li class="flex items-center justify-between text-xs">
                  <span class="flex items-center gap-1.5 text-muted-foreground">
                    <UserCircle class="h-3.5 w-3.5" />
                    用户昵称
                  </span>
                  <span>{{ profile.nickname || '—' }}</span>
                </li>
                <li class="flex items-center justify-between text-xs">
                  <span class="flex items-center gap-1.5 text-muted-foreground">
                    <Smartphone class="h-3.5 w-3.5" />
                    手机号码
                  </span>
                  <span>{{ profile.phone || '—' }}</span>
                </li>
                <li class="flex items-center justify-between text-xs">
                  <span class="flex items-center gap-1.5 text-muted-foreground">
                    <Mail class="h-3.5 w-3.5" />
                    联系邮箱
                  </span>
                  <span>{{ profile.email || '—' }}</span>
                </li>
                <li class="flex items-center justify-between text-xs">
                  <span class="flex items-center gap-1.5 text-muted-foreground">
                    <CircleDot class="h-3.5 w-3.5" />
                    账号状态
                  </span>
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-2 py-px text-[10px]"
                    :class="
                      profile.status === 1
                        ? 'bg-success/15 text-success'
                        : 'bg-muted text-muted-foreground'
                    "
                  >
                    <span
                      class="h-1.5 w-1.5 rounded-full"
                      :class="profile.status === 1 ? 'bg-success' : 'bg-muted-foreground'"
                    />
                    {{ profile.status === 1 ? '正常' : '停用' }}
                  </span>
                </li>
              </ul>
            </div>

            <div class="my-3.5 h-px bg-border" />

            <!-- 角色信息 -->
            <div>
              <p class="mb-2.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">角色信息</p>
              <div v-if="rolesLoading" class="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                <LoaderCircle class="h-3 w-3 animate-spin" />
                加载中…
              </div>
              <div v-else-if="roles.length" class="flex flex-col gap-1.5">
                <span
                  v-for="r in roles"
                  :key="r.id"
                  class="inline-flex items-center gap-2 rounded-md border border-border bg-secondary/60 px-2.5 py-1.5 text-[11px]"
                  :title="r.roleCode"
                >
                  <ShieldCheck class="h-3.5 w-3.5 text-muted-foreground" />
                  {{ r.roleName }}
                </span>
              </div>
              <p v-else class="text-[11px] text-muted-foreground">暂未分配角色</p>
            </div>

            <!-- 初始密码警示 -->
            <div
              v-if="profile.pwdResetRequired === 1"
              class="mt-4 flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/10 px-3 py-2 text-[11px] leading-relaxed"
            >
              <TriangleAlert class="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" />
              <span>当前为初始密码，请在右侧尽快修改。</span>
            </div>
          </div>
        </section>

        <!-- 右侧：编辑卡 -->
        <section class="min-w-0 flex-1 rounded-xl border border-border bg-card">
          <Tabs v-model="activeTab" class="gap-0">
            <div class="px-4 pt-3">
              <TabsList>
                <TabsTrigger value="profile">
                  <UserRound class="h-3.5 w-3.5" />
                  用户资料
                </TabsTrigger>
                <TabsTrigger value="password">
                  <KeyRound class="h-3.5 w-3.5" />
                  修改密码
                </TabsTrigger>
              </TabsList>
            </div>

            <!-- 基本资料 -->
            <TabsContent value="profile" class="px-5 py-5">
              <div class="space-y-4">
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    <span class="mr-0.5 text-destructive">*</span>用户昵称
                  </label>
                  <div class="flex-1">
                    <Input v-model="form.nickname" class="h-8 text-xs" placeholder="请输入昵称" />
                    <p v-if="errors.nickname" class="mt-1 text-[11px] text-destructive">{{ errors.nickname }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    <span class="mr-0.5 text-destructive">*</span>手机号码
                  </label>
                  <div class="flex-1">
                    <Input v-model="form.phone" class="h-8 text-xs" placeholder="请输入手机号" />
                  </div>
                </div>
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    联系邮箱
                  </label>
                  <div class="flex-1">
                    <Input v-model="form.email" class="h-8 text-xs" placeholder="name@example.com" @blur="validateEmail" />
                    <p v-if="errors.email" class="mt-1 text-[11px] text-destructive">{{ errors.email }}</p>
                  </div>
                </div>
              </div>
              <div class="mt-5 flex justify-end border-t border-border pt-4">
                <Button size="sm" :loading="savingProfile" @click="onSaveProfile">保存资料</Button>
              </div>
            </TabsContent>

            <!-- 修改密码 -->
            <TabsContent value="password" class="px-5 py-5">
              <div class="mb-4 flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-[11px] leading-relaxed">
                <ShieldAlert class="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
                <div>
                  <p class="font-medium text-destructive">密码强度要求</p>
                  <ul class="mt-1 list-inside list-disc space-y-0.5 text-muted-foreground">
                    <li>长度 8 - 32 位</li>
                    <li>必须包含大写字母、小写字母、数字、特殊字符</li>
                    <li>不得包含空格，且不能与原密码相同</li>
                  </ul>
                </div>
              </div>
              <div class="space-y-4">
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    <span class="mr-0.5 text-destructive">*</span>当前密码
                  </label>
                  <div class="flex-1">
                    <Input v-model="pwd.oldPassword" type="password" class="h-8 text-xs" placeholder="请输入当前密码" />
                    <p v-if="pwdErrors.oldPassword" class="mt-1 text-[11px] text-destructive">{{ pwdErrors.oldPassword }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    <span class="mr-0.5 text-destructive">*</span>新设密码
                  </label>
                  <div class="flex-1">
                    <Input v-model="pwd.newPassword" type="password" class="h-8 text-xs" placeholder="请输入新密码" @input="pwdErrors.confirm = ''" />
                    <p v-if="pwdErrors.newPassword" class="mt-1 text-[11px] text-destructive">{{ pwdErrors.newPassword }}</p>
                  </div>
                </div>
                <div class="flex items-start gap-3">
                  <label class="w-20 shrink-0 pt-2 text-right text-xs text-muted-foreground">
                    <span class="mr-0.5 text-destructive">*</span>确认密码
                  </label>
                  <div class="flex-1">
                    <Input v-model="pwd.confirmPassword" type="password" class="h-8 text-xs" placeholder="再次输入新密码" />
                    <p v-if="pwdErrors.confirm" class="mt-1 text-[11px] text-destructive">{{ pwdErrors.confirm }}</p>
                  </div>
                </div>
              </div>
              <div class="mt-5 flex justify-end border-t border-border pt-4">
                <Button size="sm" :loading="savingPwd" @click="onChangePassword">确认修改</Button>
              </div>
            </TabsContent>
          </Tabs>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  TriangleAlert,
  ShieldCheck,
  ShieldAlert,
  LoaderCircle,
  UserRound,
  UserCircle,
  KeyRound,
  Mail,
  Smartphone,
  CircleDot,
} from 'lucide-vue-next'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import AvatarImage from '@/components/ui/avatar/AvatarImage.vue'
import AvatarFallback from '@/components/ui/avatar/AvatarFallback.vue'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  getProfileApi,
  updateProfileApi,
  changePasswordApi,
  getMyRolesApi,
} from '@/api/auth'
import type { UserInfoVO, SysRole } from '@/types/api'
import { toast } from '@/lib/toast'

const profile = ref<UserInfoVO>({
  id: '',
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  status: 1,
  pwdResetRequired: 0,
})
const roles = ref<SysRole[]>([])
const rolesLoading = ref(false)
const savingProfile = ref(false)
const savingPwd = ref(false)

const activeTab = ref<string>('profile')

const form = reactive({ nickname: '', email: '', phone: '' })
const errors = reactive({ nickname: '', email: '' })

const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdErrors = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const validateEmail = () => {
  errors.email =
    form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '邮箱格式不正确' : ''
}

const onSaveProfile = async () => {
  errors.nickname = form.nickname.trim() ? '' : '昵称不能为空'
  validateEmail()
  if (errors.nickname || errors.email) return
  savingProfile.value = true
  try {
    await updateProfileApi({
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
    })
    toast.success('个人资料已更新')
    profile.value = (await getProfileApi()).data
  } finally {
    savingProfile.value = false
  }
}

/** 与后端 requireStrong 保持一致的强度校验 */
const isStrong = (v: string) => {
  if (v.length < 8 || v.length > 32 || /\s/.test(v)) return false
  let upper = false
  let lower = false
  let digit = false
  let special = false
  for (const ch of v) {
    if (ch >= 'A' && ch <= 'Z') upper = true
    else if (ch >= 'a' && ch <= 'z') lower = true
    else if (ch >= '0' && ch <= '9') digit = true
    else special = true
  }
  return upper && lower && digit && special
}

const onChangePassword = async () => {
  pwdErrors.oldPassword = pwd.oldPassword ? '' : '请输入原密码'
  pwdErrors.newPassword = !pwd.newPassword
    ? '请输入新密码'
    : !isStrong(pwd.newPassword)
      ? '需 8-32 位，包含大小写字母、数字与特殊字符'
      : pwd.newPassword === pwd.oldPassword
        ? '新密码不能与原密码相同'
        : ''
  pwdErrors.confirm =
    !pwd.confirmPassword || pwd.confirmPassword !== pwd.newPassword ? '两次输入的密码不一致' : ''
  if (pwdErrors.oldPassword || pwdErrors.newPassword || pwdErrors.confirm) return

  savingPwd.value = true
  try {
    await changePasswordApi({
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
    })
    toast.success('密码修改成功')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirmPassword = ''
    profile.value = (await getProfileApi()).data
  } finally {
    savingPwd.value = false
  }
}

onMounted(async () => {
  profile.value = (await getProfileApi()).data
  form.nickname = profile.value.nickname
  form.email = profile.value.email
  form.phone = profile.value.phone

  rolesLoading.value = true
  try {
    roles.value = (await getMyRolesApi()).data ?? []
  } finally {
    rolesLoading.value = false
  }
})
</script>
