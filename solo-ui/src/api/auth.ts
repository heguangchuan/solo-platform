import type { LoginRequest, LoginResponse, UserAuthResponse, StatResponse } from '@/types/api'
import { post, get } from '@/utils/request'

export function loginApi(data: LoginRequest) {
  return post<LoginResponse>('/auth/login', data)
}

export function getAuthMenusApi() {
  return get<UserAuthResponse[]>('/auth/me')
}

export function getStatsApi() {
  return get<StatResponse>('/auth/stats')
}

export function logoutApi() {
  return Promise.resolve()
}
