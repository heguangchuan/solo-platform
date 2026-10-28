import { get, post } from '@/utils/request'
import service from '@/utils/request'
import type {
  OpenApp,
  OpenAppCreatedSecret,
  OpenAppPageRequest,
  OpenAppSaveRequest,
  OpenApiLog,
  OpenApiLogPageRequest,
  OpenApiScope,
  PageResult,
} from '@/types/api'

/* ==================== 开放平台第三方应用 ==================== */

export const getOpenAppPageApi = (data: OpenAppPageRequest) => {
  return post<{ total: number; rows: OpenApp[] }>('/api/openapi/app/page', data)
}

export const getOpenAppDetailApi = (id: string) => {
  return get<OpenApp>(`/api/openapi/app/${id}`)
}

export const createOpenAppApi = (data: OpenAppSaveRequest) => {
  return post<OpenAppCreatedSecret>('/api/openapi/app/create', data)
}

export const updateOpenAppApi = (data: OpenAppSaveRequest) => {
  return post<void>('/api/openapi/app/update', data)
}

export const enableOpenAppApi = (id: string) => {
  return post<void>(`/api/openapi/app/${id}/enable`)
}

export const disableOpenAppApi = (id: string) => {
  return post<void>(`/api/openapi/app/${id}/disable`)
}

export const rotateOpenAppSecretApi = (id: string) => {
  return post<OpenAppCreatedSecret>(`/api/openapi/app/${id}/rotate-secret`)
}

/** scope 目录，申请授权时供前端多选 */
export const getOpenApiScopeListApi = () => {
  return get<OpenApiScope[]>('/api/openapi/scope/list')
}

/* ==================== 开放平台调用日志 ==================== */

export const getOpenApiLogPageApi = (data: OpenApiLogPageRequest) => {
  return post<PageResult<OpenApiLog>>('/api/openapi/log/page', data)
}

/** 按当前筛选条件导出调用日志（blob） */
export const exportOpenApiLogApi = (data: OpenApiLogPageRequest) => {
  return service.post('/api/openapi/log/export', data, { responseType: 'blob' })
}
