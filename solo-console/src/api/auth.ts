import { get, post, put } from '@/utils/request'
import type {
  LoginRequest,
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  UserInfoVO,
  ChangePasswordRequest,
  ProfileUpdateRequest,
} from '@/types/api'

/** 登录 */
export function loginApi(data: LoginRequest) {
  return post<LoginResponse>('/api/auth/login', data)
}

/** 用 refresh token 换取新的双 token（滚动刷新） */
export function refreshTokenApi(data: RefreshTokenRequest) {
  return post<RefreshTokenResponse>('/api/auth/refresh', data)
}

/** 个人档案 */
export function getProfileApi() {
  return get<UserInfoVO>('/api/auth/profile')
}

/** 更新个人资料 */
export function updateProfileApi(data: ProfileUpdateRequest) {
  return put<void>('/api/auth/profile', data)
}

/** 修改密码 */
export function changePasswordApi(data: ChangePasswordRequest) {
  return post<void>('/api/auth/password', data)
}
