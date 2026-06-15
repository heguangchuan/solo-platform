import type { LogPageRequest, LogExportRequest, SysLog, PageResult } from '@/types/api'
import { post, del } from '@/utils/request'
import service from '@/utils/request'

export function getLogPageApi(data: LogPageRequest) {
  return post<PageResult<SysLog>>('/system/log/page', data)
}

export function exportLogApi(data: LogExportRequest) {
  return service.post('/system/log/export', data, { responseType: 'blob' })
}

export function deleteLogApi(id: string) {
  return del<void>(`/system/log/${id}`)
}

export function deleteLogBatchApi(ids: string[]) {
  return del<void>('/system/log/batch', { data: ids })
}
