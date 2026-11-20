<template>
  <div class="grid h-full grid-cols-[1.1fr_1fr] bg-muted/30">
    <!-- 品牌侧（纯装饰） -->
    <div class="relative overflow-hidden bg-neutral-950">
      <!-- 背景网格 -->
      <div class="absolute inset-0 opacity-[0.04]">
        <div
          class="h-full w-full"
          style="background-image: linear-gradient(rgba(255,255,255,.6) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.6) 1px, transparent 1px); background-size: 48px 48px;"
        />
      </div>

      <!-- 光晕 -->
      <div class="absolute top-1/4 -left-20 h-[400px] w-[400px] rounded-full bg-primary/25 blur-[100px]" />
      <div class="absolute bottom-1/4 -right-20 h-[500px] w-[500px] rounded-full bg-indigo-500/15 blur-[120px]" />

      <!-- 几何线条装饰 -->
      <div class="absolute inset-0">
        <div class="absolute top-1/3 left-1/4 h-px w-[300px] bg-gradient-to-r from-transparent via-white/10 to-transparent" />
        <div class="absolute top-1/2 left-1/3 h-px w-[400px] bg-gradient-to-r from-transparent via-white/8 to-transparent" />
        <div class="absolute top-2/3 left-1/5 h-px w-[350px] bg-gradient-to-r from-transparent via-white/6 to-transparent" />
        <div class="absolute left-1/3 top-1/4 w-px h-[200px] bg-gradient-to-b from-transparent via-white/8 to-transparent" />
        <div class="absolute left-1/2 top-1/3 w-px h-[250px] bg-gradient-to-b from-transparent via-white/6 to-transparent" />
      </div>

    </div>

    <!-- 表单侧 -->
    <div class="grid place-items-center px-10">
      <div class="w-full max-w-sm">
        <div class="mb-8 space-y-1.5">
          <h2 class="text-2xl font-semibold tracking-tight">欢迎回来</h2>
          <p class="text-sm text-muted-foreground">
            登录以继续管理你的基础设施
          </p>
        </div>

        <form class="space-y-4" @submit.prevent="onSubmit">
          <div class="space-y-2">
            <Label for="username">用户名</Label>
            <div class="relative">
              <User class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="username"
                v-model="form.username"
                type="text"
                placeholder="请输入用户名"
                class="h-10 pl-9"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <Label for="password">密码</Label>
              <a href="#" class="text-xs text-muted-foreground hover:text-foreground transition-colors">
                忘记密码？
              </a>
            </div>
            <div class="relative">
              <Lock class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="password"
                v-model="form.password"
                :type="showPwd ? 'text' : 'password'"
                placeholder="请输入密码"
                class="h-10 pl-9 pr-9"
                autocomplete="current-password"
                @keyup.enter="onSubmit"
              />
              <button
                type="button"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                @click="showPwd = !showPwd"
              >
                <Eye v-if="!showPwd" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="flex items-center space-x-2">
            <Checkbox id="remember" v-model:checked="remember" />
            <label for="remember" class="text-xs text-muted-foreground cursor-pointer select-none">
              记住登录状态
            </label>
          </div>

          <Button type="submit" size="lg" class="w-full" :loading="loading">
            登 录
          </Button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, Eye, EyeOff } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const showPwd = ref(false)
const remember = ref(true)
const form = ref({ username: '', password: '' })

const onSubmit = async () => {
  if (!form.value.username || !form.value.password) return
  loading.value = true
  try {
    await auth.login(form.value)
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@media (max-width: 860px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .grid > div:first-child {
    display: none;
  }
}
</style>
