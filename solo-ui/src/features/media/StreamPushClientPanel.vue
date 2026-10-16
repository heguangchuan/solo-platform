<template>
  <div class="flex h-full gap-3 overflow-hidden">
    <!-- 左侧：列表 -->
    <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card">
      <!-- 工具栏 -->
      <div class="flex h-12 shrink-0 items-center gap-1 border-b border-border px-3">
        <span class="text-[13px] font-medium">推流方授权</span>
        <div class="flex-1" />
        <Button
          v-access="PERM.MEDIA_STREAM_PUSH_CLIENT_ADD"
          variant="ghost"
          size="sm"
          class="gap-1.5"
          @click="onCreate"
        >
          <Plus class="size-3.5" />
          新建
          <kbd class="rounded border border-border bg-muted px-1 text-[10px] text-muted-foreground"
            >N</kbd
          >
        </Button>
      </div>

      <!-- 列表 -->
      <div class="min-h-0 flex-1 overflow-auto">
        <table class="w-full border-collapse text-left text-[13px]">
          <thead class="sticky top-0 z-10">
            <tr class="bg-card text-xs text-muted-foreground">
              <th class="w-52 px-3 py-2 font-normal">推流方</th>
              <th class="px-3 py-2 font-normal">Token</th>
              <th class="w-20 px-3 py-2 font-normal">状态</th>
              <th class="w-40 px-3 py-2 font-normal">更新时间</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="(c, i) in items"
              :key="c.id"
              :data-row="i"
              class="group cursor-pointer"
              :class="
                creating || selected?.id === c.id
                  ? 'bg-primary/[0.07]'
                  : cursor === i
                    ? 'bg-muted/50'
                    : 'hover:bg-muted/30'
              "
              @click="onRowClick(c, i)"
            >
              <td class="relative px-3 py-2">
                <span
                  v-if="!creating && selected?.id === c.id"
                  class="absolute inset-y-0 left-0 w-0.5 bg-primary"
                />
                <span class="truncate font-mono" :title="c.clientId">{{ c.clientId }}</span>
              </td>
              <td class="px-3 py-2">
                <span class="flex items-center gap-1">
                  <span
                    class="block max-w-[420px] truncate font-mono text-xs text-muted-foreground"
                    :title="c.token"
                    >{{ c.token }}</span
                  >
                  <button
                    type="button"
                    class="hidden size-5 shrink-0 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground group-hover:flex"
                    title="复制 token"
                    @click.stop="copyToken(c.token)"
                  >
                    <IconCopy class="size-3.5" />
                  </button>
                </span>
              </td>
              <td class="px-3 py-2">
                <span class="flex items-center gap-1.5 text-xs">
                  <span
                    class="h-1.5 w-1.5 rounded-full"
                    :class="c.enabled === 1 ? 'bg-emerald-500' : 'bg-muted-foreground/50'"
                  />
                  {{ c.enabled === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td class="px-3 py-2 font-mono text-xs text-muted-foreground">
                {{ c.updateTime || '—' }}
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="grid place-items-center py-16">
          <IconLoader2 class="size-5 animate-spin text-muted-foreground" />
        </div>

        <div v-else-if="items.length === 0" class="flex flex-col items-center gap-2 py-16 text-muted-foreground">
          <IconKey class="size-8" />
          <div class="text-[13px]">还没有创建推流方</div>
        </div>
      </div>

      <!-- 快捷键条 -->
      <div
        class="flex h-8 shrink-0 items-center gap-3 border-t border-border px-3 text-[11px] text-muted-foreground/70"
      >
        <span><kbd>J</kbd><kbd>K</kbd> 移动</span>
        <span><kbd>Enter</kbd> 查看</span>
        <span><kbd>N</kbd> 新建</span>
        <div class="flex-1" />
        <span>共 {{ items.length }} 个推流方</span>
      </div>
    </div>

    <!-- 右侧：检视器 -->
    <Transition name="inspector">
      <aside
        v-if="creating || selected"
        class="flex w-[420px] shrink-0 flex-col overflow-hidden rounded-xl border border-border bg-card"
      >
        <div class="min-h-0 flex-1 overflow-y-auto p-5">
          <StreamPushClientInspector
            :client="selected ?? undefined"
            :creating="creating"
            @close="onCloseInspector"
            @saved="load"
            @created="onCreated"
            @token-reset="onTokenReset"
            @deleted="onDeleted"
          />
        </div>
      </aside>
    </Transition>

    <!-- Token 结果弹窗（创建 / 重置后），挂在面板层，不随 inspector 卸载 -->
    <DialogRoot :open="tokenVisible">
      <DialogContent class="max-w-lg gap-3">
        <div class="flex items-center gap-3">
          <div class="grid h-10 w-10 place-items-center rounded-full bg-primary/10 text-primary">
            <IconKey class="h-5 w-5" />
          </div>
          <div>
            <div class="text-sm font-semibold">{{ resultClientId }} 的 Token</div>
            <div class="text-xs text-muted-foreground">请妥善保存，关闭后仍可在列表中复制</div>
          </div>
        </div>

        <div class="rounded-md border bg-muted/40 p-2.5">
          <div class="flex items-start gap-2">
            <code class="min-w-0 flex-1 break-all font-mono text-xs leading-5">
              {{ resultToken }}
            </code>
            <Button
              variant="ghost"
              size="icon-sm"
              class="size-7 shrink-0"
              title="复制 token"
              @click="copyToken(resultToken)"
            >
              <IconCopy class="size-3.5" />
            </Button>
          </div>
        </div>

        <div class="rounded-md border border-dashed p-2.5 text-xs">
          <span class="text-muted-foreground">推流 URL 示例：</span>
          <code class="mt-1 block break-all font-mono text-primary">
            rtmp://&lt;zlm&gt;:1935/&lt;app&gt;/&lt;stream&gt;?token={{ resultToken }}
          </code>
        </div>

        <div class="mt-1 flex justify-end">
          <Button size="sm" @click="tokenVisible = false">我已保存</Button>
        </div>
      </DialogContent>
    </DialogRoot>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { IconCopy, IconLoader2, IconKey } from '@tabler/icons-vue'
import { Plus } from 'lucide-vue-next'
import StreamPushClientInspector from '@/features/media/StreamPushClientInspector.vue'
import { getStreamPushClientPageApi } from '@/api/media'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'
import { DialogRoot, DialogContent } from '@/components/ui/dialog'
import type { StreamPushClient } from '@/types/api'

const props = defineProps<{ active: boolean }>()

const items = ref<StreamPushClient[]>([])
const loading = ref(false)
const cursor = ref(0)
const selected = ref<StreamPushClient | null>(null)
const creating = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await getStreamPushClientPageApi({ page: 1, pageSize: 1000 })
    items.value = res.data.rows
  } finally {
    loading.value = false
  }
}

const onRowClick = (c: StreamPushClient, i: number) => {
  cursor.value = i
  selected.value = c
}

const onCreate = () => {
  selected.value = null
  creating.value = true
}

const onCloseInspector = () => {
  selected.value = null
  creating.value = false
}

/* Token 结果弹窗 */
const tokenVisible = ref(false)
const resultToken = ref('')
const resultClientId = ref('')
const showTokenResult = (c: StreamPushClient) => {
  resultToken.value = c.token
  resultClientId.value = c.clientId
  tokenVisible.value = true
}

const onCreated = (c: StreamPushClient) => {
  creating.value = false
  showTokenResult(c)
  load()
}

const onTokenReset = (c: StreamPushClient) => {
  showTokenResult(c)
  load()
}

const onDeleted = (c: StreamPushClient) => {
  items.value = items.value.filter((x) => x.id !== c.id)
  selected.value = null
  creating.value = false
}

const copyToken = async (token: string) => {
  try {
    await navigator.clipboard.writeText(token)
    toast.success('已复制')
  } catch {
    toast.warning('复制失败，请手动选择文本')
  }
}

/* 键盘流：仅面板激活时响应 */
const isTypingTarget = (el: EventTarget | null) =>
  el instanceof HTMLElement &&
  (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)

const onGlobalKeydown = (e: KeyboardEvent) => {
  if (!props.active) return
  if (isTypingTarget(e.target)) return
  switch (e.key) {
    case 'j':
    case 'J':
      e.preventDefault()
      if (items.value.length)
        cursor.value = Math.min(items.value.length - 1, cursor.value + 1)
      break
    case 'k':
    case 'K':
      e.preventDefault()
      cursor.value = Math.max(0, cursor.value - 1)
      break
    case 'Enter':
      e.preventDefault()
      if (items.value[cursor.value]) selected.value = items.value[cursor.value]
      break
    case 'n':
    case 'N':
      e.preventDefault()
      onCreate()
      break
    case 'Escape':
      onCloseInspector()
      break
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

defineExpose({ load })
</script>

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
