/** 用户管理相关 */
import type { PageRequest } from './common'

/** 用户列表查询参数 */
export interface UserPageRequest extends PageRequest {
  username?: string
  nickname?: string
  status?: number
}

/** 用户导出参数（支持按选中 id 导出） */
export interface UserExportRequest extends UserPageRequest {
  ids?: string[]
}

/** 用户表单（新增/编辑） */
export interface UserRequest {
  username?: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  /** 角色 ID 列表（仅新增时使用） */
  roleIds?: string[]
}

/** 用户列表项（后端原始模型） */
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

/** 用户状态统计（GET /user/status-count） */
export interface UserStatusCount {
  status: number
  count: number
}

/** 用户精简信息（评论等场景） */
