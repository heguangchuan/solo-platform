import { get, post } from '@/utils/request'

/* ---------- 限流测试 ---------- */
export type RateLimitScene = 'basic' | 'strict' | 'high-pressure' | 'user' | 'global'

export const rateLimitApi = (scene: RateLimitScene) => {
  return get<Record<string, unknown>>(`/api/test/rate-limit/${scene}`)
}

/* ---------- 防重复提交测试 ---------- */
export type RepeatSubmitScene =
  | 'quick-unlock'
  | 'fixed-window'
  | 'include-params'
  | 'global'

export interface RepeatSubmitRequest {
  bizNo: string
  content: string
  delayMs: number
}

export const repeatSubmitApi = (scene: RepeatSubmitScene, data: RepeatSubmitRequest) => {
  return post<Record<string, unknown>>(`/api/test/repeat-submit/${scene}`, data)
}

/* ---------- XSS 清洗测试 ---------- */
export interface XssBodyRequest {
  title: string
  content: string
  nested: { value: string }
  tags: string[]
}

export const xssBodyApi = (data: XssBodyRequest) => {
  return post<Record<string, unknown>>('/api/test/xss/body', data)
}

export const xssParamApi = (keyword: string, remark: string) => {
  return get<Record<string, unknown>>('/api/test/xss/param', {
    params: { keyword, remark },
  })
}
