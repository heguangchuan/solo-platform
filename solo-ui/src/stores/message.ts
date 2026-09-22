/**
 * 系统消息 store
 * 整合 WebSocket 实时推送和 API 拉取
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElNotification } from 'element-plus'
import { wsClient } from '@/utils/websocket'
import { getMessageList, getUnreadCount, markMessageRead, markAllMessagesRead } from '@/api/message'
import router from '@/router'

export interface SysMessage {
  id: number
  username: string
  title: string
  content: string
  type: 'SYSTEM' | 'NOTICE' | 'REMINDER' | 'BROADCAST'
  level: 'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'
  readFlag: number
  route?: string
  createTime: string
}

export const useMessageStore = defineStore('message', () => {
  const messages = ref<SysMessage[]>([])
  const unreadCount = ref(0)
  const isInitialized = ref(false)

  // 消息图标徽标数（超过99显示99+）
  const badgeCount = computed(() => unreadCount.value > 99 ? '99+' : unreadCount.value)

  /**
   * 初始化消息中心（登录成功后调用）
   */
  function init() {
    if (isInitialized.value) return

    console.log('[MessageStore] 开始初始化消息中心...')

    // 连接 WebSocket
    wsClient.connect()

    // 订阅新消息
    wsClient.subscribe((msg) => {
      console.log('[MessageStore] 收到新消息:', msg)
      onNewMessage(msg)
    })

    // 拉取未读消息数和消息列表
    console.log('[MessageStore] 拉取消息数据...')
    fetchUnreadCount()
    fetchMessages()

    isInitialized.value = true
    console.log('[MessageStore] 初始化完成')
  }

  /**
   * 清理（退出登录时调用）
   */
  function clear() {
    messages.value = []
    unreadCount.value = 0
    isInitialized.value = false
    wsClient.disconnect()
  }

  /**
   * 拉取未读消息数
   */
  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      unreadCount.value = res.data
    } catch (e) {
      console.error('获取未读消息数失败:', e)
    }
  }

  /**
   * 拉取消息列表
   */
  async function fetchMessages(limit = 50) {
    try {
      const res = await getMessageList(limit)
      messages.value = res.data
    } catch (e) {
      console.error('获取消息列表失败:', e)
    }
  }

  /**
   * 标记单条消息为已读
   */
  async function markRead(id: number) {
    try {
      await markMessageRead(id)
      const msg = messages.value.find((m) => m.id === id)
      if (msg && msg.readFlag === 0) {
        msg.readFlag = 1
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch (e) {
      console.error('标记已读失败:', e)
    }
  }

  /**
   * 标记所有消息为已读
   */
  async function markAllRead() {
    try {
      await markAllMessagesRead()
      messages.value.forEach((m) => (m.readFlag = 1))
      unreadCount.value = 0
    } catch (e) {
      console.error('全部标记已读失败:', e)
    }
  }

  /**
   * 处理新消息到达
   */
  function onNewMessage(msg: any) {
    // 设备报警：不落 sys_message 表，弹通知引导跳转报警中心，不插入消息列表
    if (msg?.type === 'DEVICE_ALARM') {
      ElNotification({
        title: msg.title || '设备报警',
        message: msg.content || '收到一条新的设备报警',
        type: 'warning',
        duration: 8000,
        onClick: () => {
          if (msg.route) router.push(msg.route)
        },
      })
      showNotification(msg)
      return
    }

    const message = msg as SysMessage
    // 插入到列表头部
    messages.value.unshift(message)
    // 未读数 +1
    if (message.readFlag === 0) {
      unreadCount.value++
    }
    // 播放通知声音或显示桌面通知（可选）
    showNotification(message)
  }

  /**
   * 浏览器桌面通知
   */
  function showNotification(msg: SysMessage) {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(msg.title, {
        body: msg.content,
        icon: '/favicon.ico',
      })
    }
  }

  /**
   * 请求通知权限（登录成功后调用一次）
   */
  function requestNotificationPermission() {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission()
    }
  }

  return {
    messages,
    unreadCount,
    badgeCount,
    isInitialized,
    init,
    clear,
    fetchUnreadCount,
    fetchMessages,
    markRead,
    markAllRead,
    requestNotificationPermission,
  }
})
