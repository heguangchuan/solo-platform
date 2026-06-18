import type { FilePageRequest, FileSaveRequest, PageResult, SysFile } from '@/types/api'
import { del, post } from '@/utils/request'
import service from '@/utils/request'

/** 分页 */
export function getFilePageApi(data: FilePageRequest) {
  return post<PageResult<SysFile>>('/api/file/page', data)
}

/** 上传文件并返回文件对象 */
export function uploadFileApi(file: File) {
  const form = new FormData()
  form.append('file', file)
  return service.post<SysFile>('/api/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 仅落库元数据（前端 / 独立服务已上传字节） */
export function saveFileMetaApi(data: FileSaveRequest) {
  return post<SysFile>('/api/file/meta', data)
}

/** 删除 */
export function deleteFileApi(id: string) {
  return del<void>(`/api/file/${id}`)
}

/** 批量删除 */
export function deleteFileBatchApi(ids: string[]) {
  return del<void>('/api/file/batch', { data: ids })
}
