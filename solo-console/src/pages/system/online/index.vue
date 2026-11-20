<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  IconSearch,
  IconLoader2,
  IconUsers,
  IconX,
} from '@tabler/icons-vue'
import { getOnlineListApi, kickOnlineApi } from '@/api/online'
import type { OnlineUser } from '@/types/api'
import { confirm } from '@/lib/confirm'
import { toast } from '@/lib/toast'
import { useAuthStore } from '@/stores/auth'
import Button from '@/components/ui/button/Button.vue'
import OnlineInspector from '@/features/online/OnlineInspector.vue'
import RefreshButton from '@/components/RefreshButton.vue'
import { parseUa } from '@/features/online/uaMeta'

const authStore = useAuthStore()
const currentUsername = computed(() => authStore.user?.username)

/* ---------- 数据（后端不分页，一次拉全量） ---------- */
const loading = ref(false)
const refreshing = ref(false)
const users = ref<OnlineUser[]>([])

const fetchData = async (silent = false) => {
  if (silent) refreshing.value = true
  else loading.value = true
  try {
    const res = await getOnlineListApi()
    users.value = res.data
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

/* ---------- 本地即时筛选 ---------- */
const usernameKw = ref('')
const nicknameKw = ref('')

const hasCondition = computed(
  () => !!usernameKw.value.trim() || !!nicknameKw.value.trim(),
)

const filtered = computed<OnlineUser[]>(() => {
  const u = usernameKw.value.trim().toLowerCase()
  const n = nicknameKw.value.trim().toLowerCase()
  return users.value.filter(
    (row) =>
      (!u || row.username.toLowerCase().includes(u)) &&
      (!n || (row.nickname || '').toLowerCase().includes(n)),
  )
})

const onClear = () => {
  usernameKw.value = ''
  nicknameKw.value = ''
  cursor.value = 0
}

/* ---------- 光标与选择 ---------- */
const cursor = ref(0)
const selected = ref<OnlineUser | null>(null)

const scrollerRef = ref<HTMLElement>()
const usernameInputRef = ref<HTMLInputElement>()

const isSelf = (row: OnlineUser) => row.username === currentUsername.value

const scrollCursorIntoView = () => {
  scrollerRef.value
    ?.querySelector(`[data-row="${cursor.value}"]`)
    ?.scrollIntoView({ block: 'nearest' })
}

const onSelect = (row: OnlineUser) => {
  selected.value = row
}

const onRowClick = (row: OnlineUser, i: number) => {
  cursor.value = i
  onSelect(row)
}

const onCloseInspector = () => {
  selected.value = null
}

/* ---------- 强制下线 ---------- */
const onKick = async (row: OnlineUser) => {
  if (isSelf(row)) {
    toast.warning('不能强制下线当前登录账号')
    return
  }
  const ok = await confirm({
    title: '强制下线',
    description: `确定要强制下线用户 “${row.nickname || row.username}” 吗？该用户的下一次请求将返回 401。`,
    confirmText: '强制下线',
    danger: true,
  })
  if (!ok) return
  await kickOnlineApi(row.username)
  toast.success('已强制下线')
  if (selected.value?.username === row.username) selected.value = null
  await fetchData(true)
}

/* ---------- 键盘流 ---------- */
const isTypingTarget = (el: EventTarget | null): boolean => {
  if (!(el instanceof HTMLElement)) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (isTypingTarget(e.target)) {
    if (e.key === 'Escape') (e.target as HTMLElement).blur()
    return
  }

  const list = filtered.value
  switch (e.key) {
    case '/':
      e.preventDefault()
      usernameInputRef.value?.focus()
      break
    case 'j':
    case 'J':
      e.preventDefault()
      if (!list.length) break
      cursor.value = Math.min(list.length - 1, cursor.value + 1)
      scrollCursorIntoView()
      break
    case 'k':
    case 'K':
      e.preventDefault()
      cursor.value = Math.max(0, cursor.value - 1)
      scrollCursorIntoView()
      break
    case 'Enter':
      e.preventDefault()
      if (list[cursor.value]) onSelect(list[cursor.value])
      break
    case 'Delete':
    case 'Backspace':
      e.preventDefault()
      if (list[cursor.value]) onKick(list[cursor.value])
      break
    case 'r':
    case 'R':
      e.preventDefault()
      fetchData(true)
      break
    case 'Escape':
      onCloseInspector()
      break
  }
}

/* 筛选结果变化：光标收敛；选中项同步最新数据 */
watch(filtered, (list) => {
  if (cursor.value > list.length - 1) {
    cursor.value = Math.max(0, list.length - 1)
  }
  if (selected.value) {
    const fresh = list.find((r) => r.username === selected.value!.username)
    if (fresh) selected.value = fresh
  }
})

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
  fetchData()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<template>
  <div class="flex h-full gap-3 overflow-hidden p-3">
    <!-- 左侧 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-2 border-b border-border px-3">
        <span class="text-[13px] font-medium">在线用户</span>
        <span class="flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[11px] text-emerald-600 dark:text-emerald-400">
          {{ users.length }} 人在线
        </span>
        <div class="flex-1" />
        <RefreshButton :loading="refreshing" :on-refresh="() => fetchData(true)" />
      </div>

      <!-- 搜索行 -->
      <div class="flex h-12 shrink-0 items-center gap-2 border-b border-border px-3">
        <IconSearch class="size-3.5 shrink-0 text-muted-foreground" />
        <input
          ref="usernameInputRef"
          v-model="usernameKw"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          placeholder="请输入用户账号"
        />
        <input
          v-model="nicknameKw"
          class="h-7 w-44 rounded-md bg-transparent px-2 text-[13px] outline-none focus:bg-muted/70"
          placeholder="请输入用户昵称"
        />
        <span v-if="hasCondition" class="text-xs text-muted-foreground">
          匹配 {{ filtered.length }} 人
        </span>
        <div class="flex-1" />
        <Button
          v-if="hasCondition"
          variant="ghost"
          size="sm"
          class="h-7 text-xs text-muted-foreground"
          @click="onClear"
        >
          <IconX class="size-3.5" />
          清除
        </Button>
      </div>

      <!-- 列表 -->
      <div ref="scrollerRef" class="min-h-0 flex-1 overflow-auto">
        <table class="w-full table-fixed border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="w-[180px] px-3 py-2 font-normal">用户账号</th>
              <th class="w-[180px] px-3 py-2 font-normal">用户昵称</th>
              <th class="w-[160px] px-3 py-2 font-normal">登录地址</th>
              <th class="w-24 px-3 py-2 font-normal">来源</th>
              <th class="w-56 px-3 py-2 font-normal">客户端</th>
              <th class="w-44 px-3 py-2 font-normal">登录时间</th>
            </tr>
          </thead>
          <tbody v-if="!loading">
            <tr
              v-for="(row, i) in filtered"
              :key="row.username"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                selected?.username === row.username
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(row, i)"
            >
              <td class="px-3 py-2">
                <div class="flex items-center gap-2">
                  <span class="font-mono text-xs">{{ row.username }}</span>
                  <span
                    v-if="isSelf(row)"
                    class="rounded bg-primary/10 px-1 py-px text-[10px] text-primary"
                  >我</span>
                </div>
              </td>
              <td class="px-3 py-2">
                {{ row.nickname || '—' }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ row.loginIp || '—' }}
              </td>
              <td class="px-3 py-2">
                <span
                  v-if="row.clientSource"
                  class="font-mono text-[11px] text-muted-foreground"
                >{{ row.clientSource }}</span>
                <span v-else class="text-muted-foreground/50">—</span>
              </td>
              <td class="truncate px-3 py-2 text-xs text-muted-foreground" :title="row.userAgent">
                {{ parseUa(row.userAgent).short }}
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ row.loginTime || '—' }}
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>
        <div
          v-else-if="!filtered.length"
          class="flex flex-col items-center gap-2 py-16 text-muted-foreground"
        >
          <IconUsers class="size-8" />
          <div class="text-[13px]">
            {{ hasCondition ? '没有匹配的在线用户' : '当前没有在线用户' }}
          </div>
          <Button v-if="hasCondition" variant="ghost" size="sm" @click="onClear">
            清除搜索条件
          </Button>
        </div>
      </div>

      <!-- 底部快捷键 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>/</kbd> 搜索</span>
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <span><kbd>Enter</kbd> 查看</span>
        <span><kbd>Del</kbd> 下线</span>
        <span><kbd>R</kbd> 刷新</span>
        <span><kbd>Esc</kbd> 关闭</span>
        <div class="flex-1" />
        <span>共 {{ filtered.length }} 人</span>
      </div>
    </div>

    <!-- 右侧检视器 -->
    <Transition name="inspector">
      <aside
        v-if="selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <OnlineInspector
            :user="selected"
            :self="isSelf(selected)"
            @close="onCloseInspector"
            @kick="onKick"
          />
        </div>
      </aside>
    </Transition>
  </div>
</template>

<style scoped>
.inspector-enter-active,
.inspector-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.inspector-enter-from,
.inspector-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

kbd {
  display: inline-block;
  border: 1px solid var(--border);
  border-radius: 3px;
  background: var(--muted);
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: var(--muted-foreground);
  font-family: ui-monospace, monospace;
}
</style>
