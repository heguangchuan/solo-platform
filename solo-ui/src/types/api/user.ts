/** 用户管理相关 */
import type { PageRequest } from './common'

/** 用户列表查询参数 */
export interface UserPageRequest extends PageRequest {
  username?: string
  nickname?: string
  status?: number
}

/** 用户表单（新增/编辑） */
export interface UserRequest {
  username?: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
}

/** 用户列表项 */
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
