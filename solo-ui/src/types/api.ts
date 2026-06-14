/**
 * 统一 API 响应类型
 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

/**
 * 分页结果类型
 */
export interface PageResult<T = any> {
  total: number
  rows: T[]
}

// ==================== 认证相关 ====================

/**
 * 登录请求
 */
export interface LoginRequest {
  username: string
  password: string
}

/**
 * 用户信息 VO
 */
export interface UserInfoVO {
  id: string
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
}

/**
 * 登录响应
 */
export interface LoginResponse {
  token: string
  user: UserInfoVO
}

/**
 * 权限树节点（认证返回）
 */
export interface UserAuthResponse {
  id: string
  permissionName: string
  icon: string
  parentId: string | number
  sort: number
  url: string
  children?: UserAuthResponse[]
}

/**
 * 统计响应
 */
export interface StatResponse {
  userCount: number
  roleCount: number
  permissionCount: number
}

// ==================== 用户管理 ====================

/**
 * 用户列表查询参数
 */
export interface UserPageRequest {
  page?: number
  pageSize?: number
  username?: string
  nickname?: string
  status?: number
}

/**
 * 用户表单（新增/编辑）
 */
export interface UserRequest {
  username?: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
}

/**
 * 用户列表项
 */
export interface SysUser {
  id: string
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  createTime: string
  updateTime: string
}

// ==================== 角色管理 ====================

/**
 * 角色列表查询参数
 */
export interface RolePageRequest {
  page?: number
  pageSize?: number
  roleCode?: string
  roleName?: string
}

/**
 * 角色表单（新增/编辑）
 */
export interface RoleRequest {
  roleCode: string
  roleName: string
  description: string
  status: number
}

/**
 * 角色列表项
 */
export interface SysRole {
  id: string
  roleCode: string
  roleName: string
  description: string
  status: number
  createTime: string
  updateTime: string
}

// ==================== 权限管理 ====================

/**
 * 权限表单（新增/编辑）
 */
export interface PermissionRequest {
  permissionName: string
  permissionCode: string
  url: string
  type: number
  parentId: string | number
  sort: number
  icon: string
  status: number
}

/**
 * 权限列表项
 */
export interface SysPermission {
  id: string
  permissionName: string
  permissionCode: string
  url: string
  type: number
  parentId: string | number
  sort: number
  icon: string
  status: number
  children?: SysPermission[]
}
