import { get, post, put } from '@/utils/request'
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

/** 登录 */
export function loginApi(data: LoginRequest) {
  return post<LoginResponse>('/api/auth/login', data)
}

/** 用 refresh token 换取新的双 token（滚动刷新） */
export function refreshTokenApi(data: RefreshTokenRequest) {
  return post<RefreshTokenResponse>('/api/auth/refresh', data)
}

/** 当前用户菜单树 */
export function getAuthMenusApi() {
  return get<UserAuthResponse[]>('/api/auth/me')
}

/** 当前用户权限标识集合 */
export function getAuthPermissionsApi() {
  return get<string[]>('/api/auth/permissions')
}

/** 顶部统计 */
export function getStatsApi() {
  return get<StatResponse>('/api/auth/stats')
}

/** 个人档案 */
export function getProfileApi() {
  return get<UserInfoVO>('/api/auth/profile')
}

/** 更新个人资料 */
export function updateProfileApi(data: ProfileUpdateRequest) {
  return put<void>('/api/auth/profile', data)
}

/** 当前用户角色 */
export function getMyRolesApi() {
  return get<SysRole[]>('/api/auth/roles')
}

/** 修改密码 */
export function changePasswordApi(data: ChangePasswordRequest) {
  return post<void>('/api/auth/password', data)
}
