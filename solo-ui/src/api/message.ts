import { get, post } from '@/utils/request'
import type { SysMessage } from '@/types/api'

/** 发送消息入参 */
export interface SendMessageRequest {
  username: string
  title: string
  content: string
  type: 'SYSTEM' | 'NOTICE' | 'REMINDER' | 'BROADCAST'
  level: 'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'
}

/** 广播消息入参 */
export interface BroadcastMessageRequest {
  title: string
  content: string
  level: 'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'
}

/** 获取当前用户消息列表 */
export const getMessageListApi = (limit = 50) => {
  return get<SysMessage[]>('/api/message/list', { params: { limit } })
}

/** 获取未读消息数 */
export const getUnreadCountApi = () => {
  return get<number>('/api/message/unread-count')
}

/** 获取 WebSocket 在线人数 */
export const getWsOnlineCountApi = () => {
  return get<number>('/api/message/online-count')
}

/** 发送消息给指定用户 */
export const sendMessageApi = (data: SendMessageRequest) => {
  return post('/api/message/send', data)
}

/** 广播消息给所有在线用户 */
export const broadcastMessageApi = (data: BroadcastMessageRequest) => {
  return post('/api/message/broadcast', data)
}

/** 标记单条消息为已读 */
export const markMessageReadApi = (id: number) => {
  return post(`/api/message/${id}/read`)
}

/** 标记所有消息为已读 */
export const markAllMessagesReadApi = () => {
  return post('/api/message/read-all')
}
