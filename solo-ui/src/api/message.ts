import { get, post } from '@/utils/request'
import type { SysMessage } from '@/stores/message'

/**
 * 获取当前用户消息列表
 */
export function getMessageList(limit = 50) {
  return get<SysMessage[]>('/api/message/list', { params: { limit } })
}

/**
 * 获取未读消息数
 */
export function getUnreadCount() {
  return get<number>('/api/message/unread-count')
}

/**
 * 标记单条消息为已读
 */
export function markMessageRead(id: number) {
  return post(`/api/message/${id}/read`)
}

/**
 * 标记所有消息为已读
 */
export function markAllMessagesRead() {
  return post('/api/message/read-all')
}

/**
 * 后台：发送消息给指定用户
 */
export function sendMessage(data: {
  username: string
  title: string
  content: string
  type?: string
  level?: string
  route?: string
}) {
  return post<void>('/api/message/send', data)
}

/**
 * 后台：广播消息给所有在线用户
 */
export function broadcastMessage(data: {
  title: string
  content: string
  level?: string
}) {
  return post<number>('/api/message/broadcast', data)
}

/**
 * 获取 WebSocket 在线用户数
 */
export function getWsOnlineCount() {
  return get<number>('/api/message/online-count')
}
