<template>
  <div class="space-y-6">
    <!-- 发送测试 -->
    <div>
      <div class="flex items-center gap-2">
        <h4 class="text-sm font-medium">消息发送测试</h4>
        <Badge variant="outline" class="text-[10px] text-muted-foreground">WebSocket 实时推送</Badge>
      </div>

      <div class="mt-3 space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">目标用户</Label>
          <Input
            v-model="targetUsername"
            class="h-8 w-56 text-xs"
            placeholder="接收消息的用户名"
          />
          <button class="text-xs text-primary hover:underline" @click="targetUsername = currentUsername">
            设为当前用户
          </button>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">标题</Label>
          <Input v-model="msgTitle" class="h-8 w-56 text-xs" placeholder="消息标题" />
        </div>

        <div class="flex items-start gap-3">
          <Label class="mt-1.5 w-16 shrink-0 text-xs text-muted-foreground">内容</Label>
          <textarea
            v-model="msgContent"
            rows="3"
            class="w-80 resize-none rounded-md border border-input bg-transparent px-3 py-2 text-xs outline-none focus:border-primary"
            placeholder="消息内容"
          />
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">类型</Label>
          <div class="flex gap-1">
            <button
              v-for="t in typeOptions"
              :key="t.value"
              class="rounded-md px-2.5 py-1 text-xs transition-colors"
              :class="
                msgType === t.value
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted'
              "
              @click="msgType = t.value"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-16 shrink-0 text-xs text-muted-foreground">级别</Label>
          <div class="flex gap-1">
            <button
              v-for="l in levelOptions"
              :key="l.value"
              class="rounded-md px-2.5 py-1 text-xs transition-colors"
              :class="
                msgLevel === l.value
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted'
              "
              @click="msgLevel = l.value"
            >
              {{ l.label }}
            </button>
          </div>
        </div>

        <div class="flex gap-2 pt-1">
          <Button size="sm" :loading="sending" @click="onSend">发送给指定用户</Button>
          <Button size="sm" variant="outline" :loading="broadcasting" @click="onBroadcast">
            广播给所有在线用户
          </Button>
        </div>
      </div>
    </div>

    <div class="h-px bg-border" />

    <!-- 消息状态 -->
    <div>
      <div class="flex items-center gap-2">
        <h4 class="text-sm font-medium">消息状态</h4>
        <Badge variant="secondary" class="text-[10px]">在线 {{ onlineCount }} 人</Badge>
        <Badge variant="outline" class="text-[10px] text-muted-foreground">
          未读 {{ messageStore.unreadCount }} 条
        </Badge>
        <div class="flex-1" />
        <Button variant="ghost" size="sm" class="h-7 text-xs" @click="loadAll">刷新</Button>
        <Button variant="ghost" size="sm" class="h-7 text-xs" @click="onMarkAllRead">
          全部已读
        </Button>
      </div>

      <div class="mt-3 max-h-80 min-h-0 overflow-auto rounded-lg border border-border">
        <table class="w-full table-fixed text-left text-xs">
          <thead class="sticky top-0 bg-muted/60 text-muted-foreground">
            <tr>
              <th class="w-16 px-3 py-2 font-normal">ID</th>
              <th class="w-40 px-3 py-2 font-normal">标题</th>
              <th class="w-64 px-3 py-2 font-normal">内容</th>
              <th class="w-20 px-3 py-2 font-normal">级别</th>
              <th class="w-20 px-3 py-2 font-normal">状态</th>
              <th class="w-44 px-3 py-2 font-normal">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in list" :key="m.id" class="border-t border-border hover:bg-muted/30">
              <td class="px-3 py-2 font-mono text-muted-foreground">{{ m.id }}</td>
              <td class="truncate px-3 py-2 font-medium" :title="m.title">{{ m.title }}</td>
              <td class="truncate px-3 py-2 text-muted-foreground" :title="m.content">{{ m.content }}</td>
              <td class="px-3 py-2">
                <span :class="levelClass(m.level)">{{ m.level }}</span>
              </td>
              <td class="px-3 py-2 text-muted-foreground">
                {{ m.readFlag === 1 ? '已读' : '未读' }}
              </td>
              <td class="whitespace-nowrap px-3 py-2 font-mono text-[11px] text-muted-foreground">
                {{ m.createTime }}
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!list.length" class="py-8 text-center text-xs text-muted-foreground">
          暂无消息
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  sendMessageApi,
  broadcastMessageApi,
  getWsOnlineCountApi,
  getMessageListApi,
} from '@/api/message'
import { useMessageStore } from '@/stores/message'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import { Badge } from '@/components/ui/badge'
import type { SysMessage } from '@/types/api'

const messageStore = useMessageStore()
const authStore = useAuthStore()

const currentUsername = computed(() => authStore.user?.username ?? '')

const targetUsername = ref('')
const msgTitle = ref('测试消息')
const msgContent = ref('这是一条测试消息内容')
const msgType = ref<'SYSTEM' | 'NOTICE' | 'REMINDER' | 'BROADCAST'>('NOTICE')
const msgLevel = ref<'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'>('INFO')

const sending = ref(false)
const broadcasting = ref(false)
const onlineCount = ref(0)
const list = ref<SysMessage[]>([])

const typeOptions = [
  { value: 'SYSTEM', label: '系统通知' },
  { value: 'NOTICE', label: '普通通知' },
  { value: 'REMINDER', label: '提醒' },
  { value: 'BROADCAST', label: '广播' },
] as const

const levelOptions = [
  { value: 'INFO', label: '普通' },
  { value: 'WARNING', label: '警告' },
  { value: 'ERROR', label: '错误' },
  { value: 'IMPORTANT', label: '重要' },
] as const

const levelClass = (level: string) => {
  const map: Record<string, string> = {
    INFO: 'text-muted-foreground',
    WARNING: 'text-amber-600 dark:text-amber-500',
    ERROR: 'text-destructive',
    IMPORTANT: 'text-primary',
  }
  return map[level] ?? 'text-muted-foreground'
}

const onSend = async () => {
  if (!targetUsername.value.trim()) return toast.warning('请输入目标用户名')
  if (!msgTitle.value.trim()) return toast.warning('请输入消息标题')

  sending.value = true
  try {
    await sendMessageApi({
      username: targetUsername.value.trim(),
      title: msgTitle.value,
      content: msgContent.value,
      type: msgType.value,
      level: msgLevel.value,
    })
    toast.success('消息发送成功')
    await loadAll()
  } finally {
    sending.value = false
  }
}

const onBroadcast = async () => {
  if (!msgTitle.value.trim()) return toast.warning('请输入消息标题')

  broadcasting.value = true
  try {
    await broadcastMessageApi({
      title: msgTitle.value,
      content: msgContent.value,
      level: msgLevel.value,
    })
    toast.success('广播发送成功')
    await loadAll()
  } finally {
    broadcasting.value = false
  }
}

const onMarkAllRead = async () => {
  await messageStore.markAllRead()
  await loadList()
}

const loadOnline = async () => {
  onlineCount.value = (await getWsOnlineCountApi()).data
}

const loadList = async () => {
  list.value = (await getMessageListApi(20)).data
}

const loadAll = () => Promise.all([loadOnline(), loadList()])

onMounted(() => {
  targetUsername.value = currentUsername.value
  loadAll()
})
</script>
