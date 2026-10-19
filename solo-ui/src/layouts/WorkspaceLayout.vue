<template>
  <div class="flex h-full overflow-hidden">
    <!-- 一级图标导航栏 -->
    <nav class="flex w-14 shrink-0 flex-col items-center gap-1 border-r border-sidebar-border bg-sidebar py-3">
      <div class="mb-2 grid h-8 w-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
        S
      </div>

      <button
        v-for="mod in navModules"
        :key="mod.id"
        class="group relative grid h-9 w-9 place-items-center rounded-lg text-sidebar-foreground transition-colors hover:bg-sidebar-accent"
        :class="{ 'bg-sidebar-accent text-primary': activeModuleId === mod.id }"
        @click="selectModule(mod.id)"
      >
        <component :is="mod.icon" :size="19" />
        <!-- 悬浮提示 -->
        <span class="pointer-events-none absolute left-12 z-50 whitespace-nowrap rounded-md border border-border bg-popover px-2 py-1 text-xs text-popover-foreground opacity-0 shadow-lg transition-opacity group-hover:opacity-100">
          {{ mod.label }}
        </span>
      </button>

      <div class="flex-1" />
    </nav>

    <!-- 常驻二级菜单栏 -->
    <aside class="flex w-56 shrink-0 flex-col border-r border-border bg-card/40">
      <div class="flex h-12 shrink-0 items-center border-b border-border px-4">
        <span class="truncate text-sm font-semibold">{{ activeModule.label }}</span>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto p-2">
        <RouterLink
          v-for="item in activeModule.items"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          :class="{ 'bg-primary/10 font-medium text-primary': isActive(item.path) }"
        >
          <component :is="item.icon" :size="16" />
          {{ item.label }}
        </RouterLink>
      </div>
    </aside>

    <!-- 主区：顶部上下文条 + 内容 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-12 shrink-0 items-center gap-3 border-b border-border bg-card/60 px-4 backdrop-blur">
        <!-- 面包屑 -->
        <div class="flex items-center gap-1.5 text-sm">
          <template v-if="currentGroup">
            <span class="text-muted-foreground">{{ currentGroup }}</span>
            <ChevronRight class="h-3.5 w-3.5 text-muted-foreground" />
          </template>
          <span class="font-medium">{{ currentLabel }}</span>
        </div>

        <!-- 搜索 / 命令入口（大搜索框，占位文本轮播展示工作方式提示） -->
        <button
          class="flex h-8 w-[22rem] items-center gap-2 rounded-md border border-border bg-muted/40 px-3 text-muted-foreground transition-colors hover:border-primary/40 hover:bg-muted hover:text-foreground"
          @click="paletteOpen = true"
        >
          <Search class="h-3.5 w-3.5 shrink-0" />
          <span class="min-w-0 flex-1 truncate text-left text-xs">{{ searchHint }}</span>
          <kbd class="shrink-0 rounded border border-border bg-background px-1 py-0.5 text-[10px] leading-none">⌘K</kbd>
        </button>

        <div class="flex-1" />

        <!-- 工具按钮组 -->
        <div class="flex items-center gap-0.5">
          <!-- 消息中心 -->
          <MessageCenter />

          <!-- 全屏 -->
          <button
            class="grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            :title="isFullscreen ? '退出全屏' : '全屏'"
            @click="toggleFullscreen"
          >
            <Minimize2 v-if="isFullscreen" class="h-4 w-4" />
            <Maximize2 v-else class="h-4 w-4" />
          </button>

          <!-- 主题切换 -->
          <button
            class="grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            :title="ui.dark ? '切换亮色' : '切换暗色'"
            @click="ui.toggleTheme()"
          >
            <Sun v-if="ui.dark" class="h-4 w-4" />
            <Moon v-else class="h-4 w-4" />
          </button>
        </div>

        <!-- 用户 -->
        <DropdownMenuRoot>
          <DropdownMenuTrigger class="grid h-8 w-8 place-items-center rounded-md transition-colors hover:bg-accent focus:outline-none">
            <Avatar :size="28">
              <AvatarImage v-if="auth.user?.avatar" :src="auth.user.avatar" />
              <AvatarFallback>{{ (auth.user?.nickname || 'U').charAt(0).toUpperCase() }}</AvatarFallback>
            </Avatar>
          </DropdownMenuTrigger>
          <DropdownMenuPortal>
            <DropdownMenuContent
              align="end"
              class="min-w-[200px] overflow-hidden rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-lg"
            >
              <!-- 当前账号信息 -->
              <div class="flex items-center gap-2.5 rounded-md px-2 py-2">
                <Avatar :size="34">
                  <AvatarImage v-if="auth.user?.avatar" :src="auth.user.avatar" />
                  <AvatarFallback>{{ (auth.user?.nickname || 'U').charAt(0).toUpperCase() }}</AvatarFallback>
                </Avatar>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium leading-tight">{{ auth.user?.nickname }}</p>
                  <p class="truncate text-xs leading-tight text-muted-foreground">@{{ auth.user?.username }}</p>
                </div>
              </div>

              <div class="my-1 h-px bg-border" />

              <DropdownMenuItem
                class="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground"
                @select="goProfile"
              >
                <UserRound class="h-4 w-4 text-muted-foreground" />
                个人中心
              </DropdownMenuItem>
              <DropdownMenuItem
                class="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground"
                @select="onLogout"
              >
                <LogOut class="h-4 w-4 text-muted-foreground" />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenuPortal>
        </DropdownMenuRoot>
      </header>

      <main class="min-h-0 flex-1">
        <RouterView v-slot="{ Component }">
          <component :is="Component" />
        </RouterView>
      </main>
    </div>

    <CommandPalette v-model:show="paletteOpen" />
    <ForcePasswordChangeDialog />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'reka-ui'
import { Search, Sun, Moon, ChevronRight, LogOut, UserRound, Maximize2, Minimize2 } from 'lucide-vue-next'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import AvatarImage from '@/components/ui/avatar/AvatarImage.vue'
import AvatarFallback from '@/components/ui/avatar/AvatarFallback.vue'
import CommandPalette from '@/features/command/CommandPalette.vue'
import ForcePasswordChangeDialog from '@/features/auth/ForcePasswordChangeDialog.vue'
import MessageCenter from '@/features/message/MessageCenter.vue'
import { buildNavFromMenus, homeModule } from '@/app/nav'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/message'
import { useUiStore } from '@/stores/ui'
import { confirm } from '@/lib/confirm'

const auth = useAuthStore()
const ui = useUiStore()
const messageStore = useMessageStore()
const route = useRoute()
const router = useRouter()

const paletteOpen = ref(false)
const isFullscreen = ref(false)

/* 搜索框占位提示轮播：顺带传达"工作方式" */
const searchHints = [
  '搜索页面、用户或执行动作…',
  '点击列表对象，在右侧检视器中查看与编辑',
  '列表支持 J / K 移动，回车查看，ESC 关闭',
]
const hintIndex = ref(0)
const searchHint = computed(() => searchHints[hintIndex.value])
let hintTimer: ReturnType<typeof setInterval> | null = null

const menuModules = computed(() => buildNavFromMenus(auth.menus))
const navModules = computed(() => [homeModule, ...menuModules.value])

/** 判断路由是否命中某个导航项：首页 '/' 精确匹配；其余按路径段匹配，避免 /device 前缀命中 /device-channel */
const matchItem = (path: string, itemPath: string) =>
  itemPath === '/'
    ? path === '/'
    : path === itemPath || path.startsWith(`${itemPath}/`)

/** 根据当前路由找到所属模块 id */
const resolveModuleIdByRoute = (path: string): string => {
  const mod = navModules.value.find((m) => m.items.some((i) => matchItem(path, i.path)))
  return mod?.id ?? homeModule.id
}

const activeModuleId = ref(homeModule.id)
const activeModule = computed(
  () => navModules.value.find((m) => m.id === activeModuleId.value) ?? homeModule,
)

const selectModule = (id: string) => {
  const mod = navModules.value.find((m) => m.id === id)
  if (!mod || mod.items.length === 0) return
  activeModuleId.value = id
  // 默认进入该模块第一个子菜单（已在该项时无需重复跳转）
  const first = mod.items[0]
  if (!matchItem(route.path, first.path)) router.push(first.path)
}

const currentModule = computed(
  () => navModules.value.find((m) => m.id === activeModuleId.value) ?? homeModule,
)
const currentLabel = computed(
  () =>
    currentModule.value.items.find((i) => matchItem(route.path, i.path))?.label ??
    activeModule.value.label,
)
// 首页只显示一级标题，避免“工作台 › 工作台”重复
const currentGroup = computed(() => (route.path === '/' ? '' : currentModule.value.label))

// 路由变化时同步高亮所属模块（支持浏览器前进后退、面包屑、外部跳转）
watch(
  () => route.path,
  (path) => {
    activeModuleId.value = resolveModuleIdByRoute(path)
  },
  { immediate: true },
)

const isActive = (path: string) => matchItem(route.path, path)

const goProfile = () => {
  router.push('/profile')
}

const onLogout = async () => {
  const ok = await confirm({
    title: '退出登录',
    description: '退出后需要重新登录才能继续使用，确认退出当前账号？',
    confirmText: '退出',
    danger: true,
  })
  if (!ok) return
  messageStore.clear()
  auth.logout()
  router.push('/login')
}

const toggleFullscreen = () => {
  if (document.fullscreenElement) {
    document.exitFullscreen()
    return
  }
  document.documentElement.requestFullscreen()
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

const onKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    paletteOpen.value = !paletteOpen.value
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  messageStore.init()
  hintTimer = setInterval(() => {
    hintIndex.value = (hintIndex.value + 1) % searchHints.length
  }, 4000)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (hintTimer) clearInterval(hintTimer)
})
</script>
