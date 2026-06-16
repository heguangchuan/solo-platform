import type { LoginRequest, LoginResponse, UserAuthResponse, UserInfoVO, StatResponse, ChangePasswordRequest } from '@/types/api'
import { post, get } from '@/utils/request'

export function loginApi(data: LoginRequest) {
  return post<LoginResponse>('/auth/login', data)
}

export function getAuthMenusApi() {
  return get<UserAuthResponse[]>('/auth/me')
}

export function getAuthPermissionsApi() {
  return get<string[]>('/auth/permissions')
}

export function getStatsApi() {
  return get<StatResponse>('/auth/stats')
}

export function getProfileApi() {
  return get<UserInfoVO>('/auth/profile')
}

export function changePasswordApi(data: ChangePasswordRequest) {
  return post<void>('/auth/password', data)
}

export function logoutApi() {
  return Promise.resolve()
}
