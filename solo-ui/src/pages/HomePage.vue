<template>
  <div class="h-full overflow-y-auto">
    <div class="mx-auto max-w-5xl px-8 py-8">
      <!-- 问候 -->
      <div class="mb-7">
        <h1 class="text-2xl font-semibold tracking-tight">{{ greeting }}，{{ auth.user?.nickname }}</h1>
        <p class="mt-1 text-sm text-muted-foreground">
          今天是 {{ today }}，从下面的入口或按 <kbd class="rounded border border-border bg-secondary px-1.5 py-0.5 text-xs">⌘K</kbd> 开始
        </p>
      </div>

      <!-- 统计 -->
      <div class="mb-6 grid grid-cols-3 gap-4">
        <div
          v-for="s in stats"
          :key="s.label"
          class="flex items-center gap-4 rounded-xl border border-border bg-card p-5"
        >
          <div class="grid h-10 w-10 place-items-center rounded-lg bg-primary/10 text-primary">
            <component :is="s.icon" class="h-5 w-5" />
          </div>
          <div>
            <div class="font-mono text-2xl font-bold leading-none tabular-nums">{{ s.value }}</div>
            <div class="mt-1 text-xs text-muted-foreground">{{ s.label }}</div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <!-- 快捷入口 -->
        <section class="rounded-xl border border-border bg-card p-5">
          <h2 class="mb-4 text-sm font-semibold">快捷入口</h2>
          <div class="grid grid-cols-3 gap-2">
            <RouterLink
              v-for="s in shortcuts"
              :key="s.path"
              :to="s.path"
              class="flex flex-col items-center gap-2 rounded-lg border border-border px-2 py-4 text-xs transition-colors hover:border-primary/50 hover:bg-accent"
            >
              <component :is="s.icon" class="h-5 w-5 text-muted-foreground" />
              {{ s.label }}
            </RouterLink>
          </div>
        </section>

        <!-- 操作提示 -->
        <section class="rounded-xl border border-border bg-card p-5">
          <h2 class="mb-4 text-sm font-semibold">工作方式</h2>
          <ul class="space-y-3 text-sm text-muted-foreground">
            <li class="flex items-start gap-2.5">
              <Command class="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <span><strong class="font-medium text-foreground">命令面板</strong>：⌘K 快速跳转页面、搜索用户或执行动作</span>
            </li>
            <li class="flex items-start gap-2.5">
              <MousePointerClick class="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <span><strong class="font-medium text-foreground">分栏检视</strong>：点击列表对象，右侧检视器内查看与编辑</span>
            </li>
            <li class="flex items-start gap-2.5">
              <Keyboard class="mt-0.5 h-4 w-4 shrink-0 text-primary" />
              <span><strong class="font-medium text-foreground">键盘优先</strong>：方向键移动、回车确认、ESC 关闭</span>
            </li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Users, ShieldCheck, Lock, Command, MousePointerClick, Keyboard } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { getStatsApi } from '@/api/auth'
import { useAccess } from '@/features/access/useAccess'
import { PERM } from '@/features/access/permissions'

const auth = useAuthStore()
const { can } = useAccess()

const statData = ref({ userCount: 0, roleCount: 0, permissionCount: 0 })

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

const stats = computed(() => [
  { label: '用户', value: statData.value.userCount, icon: Users },
  { label: '角色', value: statData.value.roleCount, icon: ShieldCheck },
  { label: '权限', value: statData.value.permissionCount, icon: Lock },
])

const allShortcuts = [
  { label: '用户管理', path: '/system/user', icon: Users, perm: PERM.USER },
  { label: '角色管理', path: '/system/role', icon: ShieldCheck, perm: PERM.ROLE },
  { label: '权限管理', path: '/system/permission', icon: Lock, perm: PERM.PERMISSION },
]
const shortcuts = computed(() => allShortcuts.filter((s) => can(s.perm)))

onMounted(async () => {
  try {
    statData.value = (await getStatsApi()).data
  } catch {
    /* 统计失败不阻塞页面 */
  }
})
</script>
