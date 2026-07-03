/** AI模型配置相关 */
import type { PageRequest } from './common'

export interface AiModelConfigPageRequest extends PageRequest {
  provider?: string
  modelType?: string
  modelName?: string
  status?: number
}

export interface AiModelConfigRequest {
  provider: string
  modelName: string
  modelType: string
  apiKey?: string
  baseUrl?: string
  modelParams?: string
  supportVision?: number
  status: number
  sort?: number
}

export interface AiModelConfig {
  id: string
  provider: string
  modelName: string
  modelType: string
  baseUrl?: string
  modelParams?: string
  supportVision?: number
  status: number
  sort?: number
  hasApiKey?: boolean
  createTime: string
  updateTime: string
}

export interface AiModelProviderOption {
  value: string
  label: string
  protocol: string
  defaultBaseUrl?: string
  supportVision?: boolean
}

/** 提示词模板相关 */
export interface AiPromptTemplatePageRequest extends PageRequest {
  templateName?: string
  templateCode?: string
  templateType?: string
  status?: number
}

export interface AiPromptTemplateRequest {
  templateName: string
  templateCode: string
  templateType: string
  content: string
  variables?: string
  example?: string
  status: number
  sort?: number
  remark?: string
}

export interface AiPromptTemplate {
  id: string
  templateName: string
  templateCode: string
  templateType: string
  content: string
  variables?: string
  example?: string
  status: number
  sort?: number
  remark?: string
  createTime: string
  updateTime: string
}

/** AI素材相关 */
export interface AiAssetPageRequest extends PageRequest {
  fileName?: string
  assetType?: string
  source?: string
}

export interface AiAssetRequest {
  assetType: string
  source?: string
  fileName: string
  fileUrl: string
  coverUrl?: string
}

export interface AiAsset {
  id: string
  userId?: string
  taskId?: string
  assetType: string
  source?: string
  fileName: string
  fileUrl: string
  coverUrl?: string
  createTime: string
}
