import type { LogPageRequest, LogExportRequest, SysLog, PageResult } from '@/types/api'
import { post, del } from '@/utils/request'
import service from '@/utils/request'

export function getLogPageApi(data: LogPageRequest) {
  return post<PageResult<SysLog>>('/api/log/page', data)
}

export function exportLogApi(data: LogExportRequest) {
  return service.post('/api/log/export', data, { responseType: 'blob' })
}

export function deleteLogApi(id: string) {
  return del<void>(`/api/log/${id}`)
}

export function deleteLogBatchApi(ids: string[]) {
  return del<void>('/api/log/batch', { data: ids })
}
