/** 系统参数相关 */
import type { PageRequest } from './common'

export interface ConfigPageRequest extends PageRequest {
  configName?: string
  configKey?: string
}

export interface ConfigRequest {
  configName: string
  configKey: string
  configValue: string
  remark?: string
}

export interface SysConfig {
  id: string
  configName: string
  configKey: string
  configValue: string
  remark: string
  createTime: string
  updateTime: string
}
