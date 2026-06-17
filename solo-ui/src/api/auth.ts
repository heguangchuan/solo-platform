import type {
  LoginRequest,
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  UserAuthResponse,
  UserInfoVO,
  StatResponse,
  ChangePasswordRequest,
  ProfileUpdateRequest,
  SysRole,
} from '@/types/api'
import { post, get, put } from '@/utils/request'

export function loginApi(data: LoginRequest) {
  return post<LoginResponse>('/auth/login', data)
}

/** 用 refresh token 换取新的 access / refresh token（滚动刷新） */
export function refreshTokenApi(data: RefreshTokenRequest) {
  return post<RefreshTokenResponse>('/auth/refresh', data)
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

export function updateProfileApi(data: ProfileUpdateRequest) {
  return put<void>('/auth/profile', data)
}

export function getMyRolesApi() {
  return get<SysRole[]>('/auth/roles')
}

export function changePasswordApi(data: ChangePasswordRequest) {
  return post<void>('/auth/password', data)
}

export function logoutApi() {
  return Promise.resolve()
}
