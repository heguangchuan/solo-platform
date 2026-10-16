/**
 * 系统消息 store
 * 整合 WebSocket 实时推送与 REST 拉取
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { wsClient } from '@/utils/websocket'
import { toast } from '@/lib/toast'
import router from '@/app/router'
import {
  getMessageListApi,
  getUnreadCountApi,
  markMessageReadApi,
  markAllMessagesReadApi,
} from '@/api/message'
import type { SysMessage } from '@/types/api'

export const useMessageStore = defineStore('message', () => {
  const messages = ref<SysMessage[]>([])
  const unreadCount = ref(0)
  const isInitialized = ref(false)

  /** 徽标文案（超过 99 显示 99+） */
  const badgeCount = computed(() =>
    unreadCount.value > 99 ? '99+' : unreadCount.value
  )

  /** 处理 WebSocket 新消息 */
  const onNewMessage = (data: unknown) => {
    const msg = data as Partial<SysMessage>
    if (msg?.type === 'DEVICE_ALARM') {
      // 设备报警不落消息表，仅弹通知，点击跳转到报警中心
      const route = (msg as { route?: string }).route || '/gb28181/alarm'
      toast.warning(msg.title || '收到一条新的设备报警', {
        label: '查看',
        onClick: () => router.push(route),
      })
      return
    }

    const message = data as SysMessage
    messages.value.unshift(message)
    if (message.readFlag === 0) {
      unreadCount.value++
    }
    toast.info(message.title)
  }

  /** 初始化（登录成功后调用） */
  const init = () => {
    if (isInitialized.value) return

    wsClient.connect()
    wsClient.subscribe(onNewMessage)

    const fetchUnreadCount = async () => {
      try {
        const res = await getUnreadCountApi()
        unreadCount.value = res.data
      } catch (e) {
        console.error('获取未读消息数失败:', e)
      }
    }

    const fetchMessages = async (limit = 50) => {
      try {
        const res = await getMessageListApi(limit)
        messages.value = res.data
      } catch (e) {
        console.error('获取消息列表失败:', e)
      }
    }

    fetchUnreadCount()
    fetchMessages()
    isInitialized.value = true
  }

  /** 清理（退出登录时调用） */
  const clear = () => {
    messages.value = []
    unreadCount.value = 0
    isInitialized.value = false
    wsClient.disconnect()
  }

  /** 标记单条已读 */
  const markRead = async (id: number) => {
    try {
      await markMessageReadApi(id)
      const msg = messages.value.find((m) => m.id === id)
      if (msg && msg.readFlag === 0) {
        msg.readFlag = 1
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch (e) {
      console.error('标记已读失败:', e)
    }
  }

  /** 全部标记已读 */
  const markAllRead = async () => {
    try {
      await markAllMessagesReadApi()
      messages.value.forEach((m) => (m.readFlag = 1))
      unreadCount.value = 0
    } catch (e) {
      console.error('全部标记已读失败:', e)
    }
  }

  return {
    messages,
    unreadCount,
    badgeCount,
    isInitialized,
    init,
    clear,
    markRead,
    markAllRead,
  }
})
