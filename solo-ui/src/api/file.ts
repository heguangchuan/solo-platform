import { post, del } from '@/utils/request'
import service from '@/utils/request'
import type { FilePageRequest, FileSaveRequest, PageResult, Result, SysFile } from '@/types/api'

/** 分页 */
export function getFilePageApi(data: FilePageRequest) {
  return post<PageResult<SysFile>>('/api/file/page', data)
}

/** 上传文件并返回文件对象（拦截器会解包 Result） */
export function uploadFileApi(file: File, onProgress?: (percent: number) => void) {
  const form = new FormData()
  form.append('file', file)
  return service.post('/api/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 上传放宽超时（实例默认 15s）
    timeout: 60_000,
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.min(100, Math.round((e.loaded / e.total) * 100)))
    },
  }) as unknown as Promise<Result<SysFile>>
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
