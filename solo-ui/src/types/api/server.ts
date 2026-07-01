/** 服务器管理相关 */
import type { PageRequest } from './common'

/** 服务器列表查询参数 */
export interface ServerPageRequest extends PageRequest {
  serverName?: string
  host?: string
  serverGroup?: string
  status?: number
  owner?: string
}

/** 服务器表单（新增/编辑） */
export interface ServerRequest {
  serverName: string
  host: string
  port: number
  username: string
  authType: 'PASSWORD' | 'PRIVATE_KEY'
  password?: string
  privateKey?: string
  serverGroup?: string
  osType?: string
  status: number
  owner?: string
  description?: string
}

/** 服务器列表项 */
export interface SysServer {
  id: string
  serverName: string
  host: string
  port: number
  username: string
  authType: string
  serverGroup?: string
  osType?: string
  status: number
  owner?: string
  description?: string
  createTime: string
  updateTime: string
}
