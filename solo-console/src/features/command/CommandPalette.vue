<template>
  <DialogRoot v-model:open="show" @update:open="onOpenChange">
    <DialogContent
      :show-close-button="false"
      class="top-[16%] translate-y-0 gap-0 overflow-hidden rounded-xl p-0 shadow-2xl sm:max-w-xl"
    >
      <!-- 输入行 -->
      <div class="flex items-center gap-3 border-b border-border px-4 py-4">
        <Search class="h-4.5 w-4.5 shrink-0 text-muted-foreground" />
        <input
          ref="inputRef"
          v-model="keyword"
          placeholder="搜索页面、用户或执行操作…"
          class="flex-1 bg-transparent text-[15px] outline-none placeholder:text-muted-foreground/70"
          @keydown.down.prevent="move(1)"
          @keydown.up.prevent="move(-1)"
          @keydown.enter.prevent="runActive"
        />
        <kbd class="rounded-md border border-border bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">ESC</kbd>
      </div>

      <!-- 结果列表 -->
      <div class="max-h-[380px] overflow-y-auto p-2">
        <template v-for="(group, gi) in grouped" :key="group.label">
          <div
            v-if="group.items.length"
            class="px-2.5 pb-1.5 text-[11px] font-medium tracking-wide text-muted-foreground/80"
            :class="gi > 0 ? 'pt-3' : 'pt-1'"
          >
            {{ group.label }}
          </div>
          <button
            v-for="entry in group.items"
            :key="entry.id"
            type="button"
            class="group flex w-full items-center gap-3 rounded-lg px-2.5 py-2 text-left text-sm transition-colors"
            :class="flatIndex(entry.id) === active ? 'bg-accent text-accent-foreground' : 'text-foreground'"
            @mouseenter="active = flatIndex(entry.id)"
            @click="run(entry)"
          >
            <span
              class="grid h-7 w-7 shrink-0 place-items-center rounded-md border border-border bg-muted/60 text-muted-foreground"
            >
              <component :is="entry.icon" class="h-3.5 w-3.5" />
            </span>
            <span class="flex-1 truncate">{{ entry.label }}</span>
            <span
              v-if="entry.hint"
              class="rounded border border-border/70 px-1.5 py-0.5 text-[10px] leading-none text-muted-foreground"
            >
              {{ entry.hint }}
            </span>
          </button>
        </template>

        <div
          v-if="totalCount === 0"
          class="flex flex-col items-center gap-2 py-12 text-muted-foreground"
        >
          <SearchX class="h-6 w-6" />
          <div class="text-sm">未找到「{{ keyword }}」相关的结果</div>
        </div>
      </div>

      <!-- 底部快捷键栏 -->
      <div
        class="flex h-9 items-center gap-3 border-t border-border bg-muted/30 px-4 text-[11px] text-muted-foreground"
      >
        <span class="flex items-center gap-1">
          <kbd class="rounded border border-border bg-background px-1">↑↓</kbd> 选择
        </span>
        <span class="flex items-center gap-1">
          <kbd class="rounded border border-border bg-background px-1">↵</kbd> 打开
        </span>
        <span class="flex items-center gap-1">
          <kbd class="rounded border border-border bg-background px-1">ESC</kbd> 关闭
        </span>
        <div class="flex-1" />
        <span v-if="keyword.trim()">{{ totalCount }} 个结果</span>
      </div>
    </DialogContent>
  </DialogRoot>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, SearchX, LogOut, UserRound } from 'lucide-vue-next'
import { DialogRoot, DialogContent } from '@/components/ui/dialog'
import { buildNavFromMenus, homeModule } from '@/app/nav'
import { useAuthStore } from '@/stores/auth'
import { getUserPageApi } from '@/api/user'
import type { SysUser } from '@/types/api'

const show = defineModel<boolean>('show', { required: true })
const router = useRouter()
const auth = useAuthStore()

interface CmdEntry {
  id: string
  label: string
  hint?: string
  icon: unknown
  group: string
  run: () => void
}

const keyword = ref('')
const active = ref(0)
const inputRef = ref<HTMLInputElement>()
const searchedUsers = ref<SysUser[]>([])

// 页面导航 + 固定动作
const staticCommands = computed<CmdEntry[]>(() => {
  const nav: CmdEntry[] = [homeModule, ...buildNavFromMenus(auth.menus)]
    .flatMap((m) => m.items)
    .map((i) => ({
      id: `nav:${i.path}`,
      label: i.label,
      hint: '跳转',
      icon: i.icon,
      group: '页面',
      run: () => router.push(i.path),
    }))

  const actions: CmdEntry[] = [
    {
      id: 'action:logout',
      label: '退出登录',
      hint: '操作',
      icon: LogOut,
      group: '操作',
      run: () => {
        auth.logout()
        router.push('/login')
      },
    },
  ]
  return [...nav, ...actions]
})

// 跨实体：用户搜索（验证“搜索即跳转”的命令范式）
let userTimer: number | undefined
watch(keyword, (kw) => {
  window.clearTimeout(userTimer)
  const q = kw.trim()
  if (!q) {
    searchedUsers.value = []
    return
  }
  userTimer = window.setTimeout(async () => {
    try {
      const res = await getUserPageApi({ page: 1, pageSize: 6, username: q })
      searchedUsers.value = res.data.rows
    } catch {
      searchedUsers.value = []
    }
  }, 220)
})

const userCommands = computed<CmdEntry[]>(() =>
  searchedUsers.value.map((u) => ({
    id: `user:${u.id}`,
    label: `${u.nickname}（@${u.username}）`,
    hint: '用户',
    icon: UserRound,
    group: '用户',
    run: () => router.push({ path: '/system/user', query: { select: u.id } }),
  }))
)

const grouped = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const all = kw
    ? [
        ...staticCommands.value.filter((c) => c.label.toLowerCase().includes(kw)),
        ...userCommands.value,
      ]
    : staticCommands.value

  const map = new Map<string, CmdEntry[]>()
  for (const c of all) {
    if (!map.has(c.group)) map.set(c.group, [])
    map.get(c.group)!.push(c)
  }
  return [...map.entries()].map(([label, items]) => ({ label, items }))
})

const flatList = computed(() => grouped.value.flatMap((g) => g.items))
const totalCount = computed(() => flatList.value.length)

const flatIndex = (id: string) => {
  return flatList.value.findIndex((e) => e.id === id)
}

watch([grouped, keyword], () => (active.value = 0))

const move = (step: number) => {
  if (totalCount.value === 0) return
  active.value = (active.value + step + totalCount.value) % totalCount.value
}

const run = (entry: CmdEntry) => {
  entry.run()
  show.value = false
}

const runActive = () => {
  const item = flatList.value[active.value]
  if (item) run(item)
}

const onOpenChange = (v: boolean) => {
  show.value = v
  if (v) {
    keyword.value = ''
    nextTick(() => inputRef.value?.focus())
  }
}
</script>
