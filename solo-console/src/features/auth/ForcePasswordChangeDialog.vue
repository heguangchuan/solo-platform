<template>
  <Dialog
    :open="required"
    @update:open="onOpenChange"
  >
    <DialogContent
      class="max-w-md"
      :show-close-button="false"
      disable-outside-pointer-events
      @escape-key-down.prevent
      @pointer-down-outside.prevent
      @interact-outside.prevent
    >
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 text-base">
          <ShieldAlert class="h-4 w-4 text-warning" />
          首次登录请修改密码
        </DialogTitle>
        <DialogDescription>
          检测到当前仍使用初始密码，为保障账号安全，请先修改密码后再使用系统。
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-3.5">
        <div class="space-y-1.5">
          <Label for="fpc-old">当前密码</Label>
          <Input
            id="fpc-old"
            v-model="pwd.oldPassword"
            type="password"
            class="h-9 text-xs"
            placeholder="请输入当前密码"
            autocomplete="current-password"
          />
          <p v-if="errors.oldPassword" class="text-[11px] text-destructive">{{ errors.oldPassword }}</p>
        </div>

        <div class="space-y-1.5">
          <Label for="fpc-new">新设密码</Label>
          <Input
            id="fpc-new"
            v-model="pwd.newPassword"
            type="password"
            class="h-9 text-xs"
            placeholder="请输入新密码"
            autocomplete="new-password"
          />
          <p v-if="errors.newPassword" class="text-[11px] text-destructive">{{ errors.newPassword }}</p>
        </div>

        <div class="space-y-1.5">
          <Label for="fpc-confirm">确认密码</Label>
          <Input
            id="fpc-confirm"
            v-model="pwd.confirmPassword"
            type="password"
            class="h-9 text-xs"
            placeholder="再次输入新密码"
            autocomplete="new-password"
          />
          <p v-if="errors.confirm" class="text-[11px] text-destructive">{{ errors.confirm }}</p>
        </div>

        <div class="rounded-md border border-border bg-muted/40 px-3 py-2 text-[11px] leading-relaxed text-muted-foreground">
          长度 8 - 32 位，大写字母、小写字母、数字、特殊字符至少包含三类，不得包含空格，且不能与原密码相同。
        </div>
      </div>

      <DialogFooter>
          <Button class="w-full" :loading="saving" @click="onSubmit">修改密码并重新登录</Button>
        </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ShieldAlert } from 'lucide-vue-next'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { changePasswordApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/lib/toast'

const auth = useAuthStore()
const router = useRouter()

/** 仅当后端标记需要重置时打开；不提供任何关闭通道。
 *  用 computed 而非一次性 ref，以兼容登录后 profile 异步加载完成的时刻。 */
const required = computed(() => auth.user?.pwdResetRequired === 1)

const saving = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const errors = reactive({ oldPassword: '', newPassword: '', confirm: '' })

/** 受控弹窗：忽略一切来自遮罩 / ESC / 焦点逃逸的关闭请求 */
const onOpenChange = (open: boolean) => {
  if (!open) return
}

/** 与后端 requireStrong 保持一致：四类字符至少三类 */
const isStrong = (v: string) => {
  if (v.length < 8 || v.length > 32 || /\s/.test(v)) return false
  const kinds = [/[A-Z]/, /[a-z]/, /\d/, /[^A-Za-z0-9]/]
  return kinds.filter((pattern) => pattern.test(v)).length >= 3
}

const onSubmit = async () => {
  errors.oldPassword = pwd.oldPassword ? '' : '请输入当前密码'
  errors.newPassword = !pwd.newPassword
    ? '请输入新密码'
    : !isStrong(pwd.newPassword)
      ? '需 8-32 位，四类字符至少包含三类'
      : pwd.newPassword === pwd.oldPassword
        ? '新密码不能与原密码相同'
        : ''
  errors.confirm =
    !pwd.confirmPassword || pwd.confirmPassword !== pwd.newPassword ? '两次输入的密码不一致' : ''
  if (errors.oldPassword || errors.newPassword || errors.confirm) return

  saving.value = true
  try {
    await changePasswordApi({
      oldPassword: pwd.oldPassword,
      newPassword: pwd.newPassword,
    })
    toast.success('密码修改成功，请重新登录')
    auth.logout()
    await router.replace({ name: 'login' })
  } finally {
    saving.value = false
  }
}
</script>
