import type { PageResult, AiModelConfig, AiModelConfigPageRequest, AiModelConfigRequest, AiModelProviderOption, AiPromptTemplate, AiPromptTemplatePageRequest, AiPromptTemplateRequest, AiAsset, AiAssetPageRequest, AiAssetRequest } from '@/types/api'
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
  attachments?: string
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

export interface ChatAttachmentInput {
  url: string
  fileName?: string
  mimeType?: string
}

export interface ChatSendRequest {
  conversationId?: string
  content: string
  modelConfigId?: string
  templateCode?: string
  variables?: Record<string, any>
  images?: ChatAttachmentInput[]
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

/* ==================== AI模型配置管理 ==================== */

/**
 * 获取模型配置分页列表
 */
export function getModelConfigPageApi(data: AiModelConfigPageRequest) {
  return post<PageResult<AiModelConfig>>('/api/ai/model-config/page', data)
}

/**
 * 获取启用的模型配置列表
 */
export function getModelConfigListApi() {
  return get<AiModelConfig[]>('/api/ai/model-config/list')
}

/**
 * 获取模型来源选项
 */
export function getModelProviderOptionsApi() {
  return get<AiModelProviderOption[]>('/api/ai/model-config/provider-options')
}

/**
 * 获取模型配置详情
 */
export function getModelConfigApi(id: string) {
  return get<AiModelConfig>(`/api/ai/model-config/${id}`)
}

/**
 * 新增模型配置
 */
export function addModelConfigApi(data: AiModelConfigRequest) {
  return post<void>('/api/ai/model-config', data)
}

/**
 * 编辑模型配置
 */
export function editModelConfigApi(id: string, data: AiModelConfigRequest) {
  return put<void>(`/api/ai/model-config/${id}`, data)
}

/**
 * 删除模型配置
 */
export function deleteModelConfigApi(id: string) {
  return del<void>(`/api/ai/model-config/${id}`)
}

/**
 * 批量删除模型配置
 */
export function deleteModelConfigBatchApi(ids: string[]) {
  return del<void>('/api/ai/model-config/batch', { data: ids })
}

/* ==================== 提示词模板管理 ==================== */

/**
 * 获取模板分页列表
 */
export function getPromptTemplatePageApi(data: AiPromptTemplatePageRequest) {
  return post<PageResult<AiPromptTemplate>>('/api/ai/prompt-template/page', data)
}

/**
 * 获取启用的模板列表
 */
export function getPromptTemplateListApi() {
  return get<AiPromptTemplate[]>('/api/ai/prompt-template/list')
}

/**
 * 获取模板详情
 */
export function getPromptTemplateApi(id: string) {
  return get<AiPromptTemplate>(`/api/ai/prompt-template/${id}`)
}

/**
 * 新增模板
 */
export function addPromptTemplateApi(data: AiPromptTemplateRequest) {
  return post<void>('/api/ai/prompt-template', data)
}

/**
 * 编辑模板
 */
export function editPromptTemplateApi(id: string, data: AiPromptTemplateRequest) {
  return put<void>(`/api/ai/prompt-template/${id}`, data)
}

/**
 * 删除模板
 */
export function deletePromptTemplateApi(id: string) {
  return del<void>(`/api/ai/prompt-template/${id}`)
}

/**
 * 批量删除模板
 */
export function deletePromptTemplateBatchApi(ids: string[]) {
  return del<void>('/api/ai/prompt-template/batch', { data: ids })
}

/**
 * 渲染模板
 */
export function renderPromptTemplateApi(templateCode: string, variables: Record<string, any>) {
  return post<string>(`/api/ai/prompt-template/render/${templateCode}`, variables)
}

/* ==================== AI素材管理 ==================== */

/**
 * 获取素材分页列表
 */
export function getAssetPageApi(data: AiAssetPageRequest) {
  return post<PageResult<AiAsset>>('/api/ai/asset/page', data)
}

/**
 * 获取素材详情
 */
export function getAssetApi(id: string) {
  return get<AiAsset>(`/api/ai/asset/${id}`)
}

/**
 * 新增素材
 */
export function addAssetApi(data: AiAssetRequest) {
  return post<void>('/api/ai/asset', data)
}

/**
 * 编辑素材
 */
export function editAssetApi(id: string, data: AiAssetRequest) {
  return put<void>(`/api/ai/asset/${id}`, data)
}

/**
 * 删除素材
 */
export function deleteAssetApi(id: string) {
  return del<void>(`/api/ai/asset/${id}`)
}

/**
 * 批量删除素材
 */
export function deleteAssetBatchApi(ids: string[]) {
  return del<void>('/api/ai/asset/batch', { data: ids })
}
