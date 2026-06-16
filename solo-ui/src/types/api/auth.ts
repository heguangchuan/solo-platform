/**
 * 认证 / 个人中心相关：登录、当前用户、菜单/权限、改密、统计。
 */

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 当前登录用户 VO（登录响应 / 个人档案接口共用） */
export interface UserInfoVO {
  id: string
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  /** 是否首次登录强制改密：0-否 1-是 */
  pwdResetRequired: number
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  user: UserInfoVO
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 个人资料更新请求（仅可改昵称/邮箱/手机号/头像） */
export interface ProfileUpdateRequest {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}

/** /auth/me 返回的菜单权限树节点 */
export interface UserAuthResponse {
  id: string
  permissionName: string
  icon: string
  parentId: string | number
  sort: number
  url: string
  children?: UserAuthResponse[]
}

/** 顶部统计（用户/角色/权限总数） */
export interface StatResponse {
  userCount: number
  roleCount: number
  permissionCount: number
}
