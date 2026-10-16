/** 文件管理相关 */
import type { PageRequest } from './common'

/** 文件元数据 */
export interface SysFile {
  id: string
  originalName: string
  storageName: string
  url: string
  ext: string
  mimeType: string
  size: number
  uploaderId?: string
  uploaderName?: string
  createTime: string
  updateTime: string
}

export interface FilePageRequest extends PageRequest {
  originalName?: string
  uploaderName?: string
  startTime?: string
  endTime?: string
}

/** 仅落库元数据（前端/独立服务已上传字节） */
export interface FileSaveRequest {
  originalName: string
  storageName?: string
  url: string
  ext?: string
  mimeType?: string
  size: number
}
