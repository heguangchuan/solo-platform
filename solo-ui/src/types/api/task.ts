import type { PageRequest } from './common'

export interface TaskPageRequest extends PageRequest {
  taskName?: string
  taskCode?: string
  status?: number
}

export interface TaskRequest {
  taskName: string
  taskCode: string
  cron: string
  handlerName: string
  taskParams?: string
  status?: number
  remark?: string
}

export interface SysTask {
  id: string
  taskName: string
  taskCode: string
  cron: string
  handlerName: string
  taskParams?: string
  status: number
  remark?: string
  createTime: string
  updateTime: string
}

export interface TaskLogPageRequest extends PageRequest {
  taskId?: string
  taskName?: string
  taskCode?: string
  resultStatus?: number
}

export interface SysTaskLog {
  id: string
  taskId: string
  taskName: string
  taskCode: string
  handlerName: string
  taskParams?: string
  executionTime: string
  durationMs?: number
  resultStatus: number
  errorMsg?: string
}
