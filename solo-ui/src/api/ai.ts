import type { PageResult } from '@/types/api'
import { get, post, del, put } from '@/utils/request'

export interface AiConversation {
  id: string
  userId: string
  title: string
  conversationType: string
  modelConfigId: string
  lastMessageTime: string
  messageCount: number
  createTime: string
  updateTime: string
}

export interface AiMessage {
  id: string
  conversationId: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  messageType: string
  modelName: string
  tokens: number
  createTime: string
}

export interface AiGenerationTask {
  id: string
  userId: string
  conversationId: string
  messageId: string
  taskType: string
  prompt: string
  enhancedPrompt: string
  modelName: string
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
  resultType: string
  resultContent: string
  errorMsg: string
  tokens: number
  cost: number
  startTime: string
  finishTime: string
  createTime: string
}

export interface ChatSendRequest {
  conversationId?: string
  content: string
  templateCode?: string
  variables?: Record<string, any>
}

export interface ConversationCreateRequest {
  title?: string
  conversationType?: string
}

/**
 * 发送消息
 */
export function sendChatApi(data: ChatSendRequest) {
  return post<string>('/api/ai/chat/send', data)
}

/**
 * 获取会话列表
 */
export function getConversationsApi(params: { page?: number; pageSize?: number }) {
  return get<PageResult<AiConversation>>('/api/ai/chat/conversations', params)
}

/**
 * 获取会话消息
 */
export function getConversationMessagesApi(conversationId: string) {
  return get<AiMessage[]>(`/api/ai/chat/conversations/${conversationId}/messages`)
}

/**
 * 创建会话
 */
export function createConversationApi(data: ConversationCreateRequest) {
  return post<AiConversation>('/api/ai/chat/conversations', data)
}

/**
 * 删除会话
 */
export function deleteConversationApi(conversationId: string) {
  return del<void>(`/api/ai/chat/conversations/${conversationId}`)
}

/**
 * 更新会话标题
 */
export function updateConversationTitleApi(conversationId: string, title: string) {
  return put<void>(`/api/ai/chat/conversations/${conversationId}/title?title=${encodeURIComponent(title)}`)
}

/**
 * 获取任务列表
 */
export function getTaskListApi(params: { page?: number; pageSize?: number; taskType?: string; status?: string }) {
  return get<PageResult<AiGenerationTask>>('/api/ai/task/list', params)
}

/**
 * 获取任务详情
 */
export function getTaskDetailApi(taskId: string) {
  return get<AiGenerationTask>(`/api/ai/task/${taskId}`)
}
