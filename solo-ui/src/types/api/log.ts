/** 操作日志相关 */
import type { PageRequest } from './common'

export interface LogPageRequest extends PageRequest {
  operatorName?: string
  module?: string
  businessType?: string
  responseStatus?: number
  clientSource?: string
  startTime?: string
  endTime?: string
}

export interface LogExportRequest extends LogPageRequest {
  ids?: string[]
}

export interface SysLog {
  id: string
  module: string
  businessType: string
  description: string
  requestMethod: string
  requestUrl: string
  requestParam: string
  responseStatus: number
  errorMsg: string
  costMs: number
  operatorId: string
  operatorName: string
  clientSource: string
  clientIp: string
  userAgent: string
  createTime: string
}
