import { post, del } from '@/utils/request'
import service from '@/utils/request'
import type { SysLog, LogPageRequest, LogExportRequest, PageResult } from '@/types/api'

/** 日志分页 */
export function getLogPageApi(data: LogPageRequest) {
  return post<PageResult<SysLog>>('/api/log/page', data)
}

/** 导出日志（blob） */
export function exportLogApi(data: LogExportRequest) {
  return service.post('/api/log/export', data, { responseType: 'blob' })
}

/** 删除日志 */
export function deleteLogApi(id: string) {
  return del<void>(`/api/log/${id}`)
}

/** 批量删除日志 */
export function deleteLogBatchApi(ids: string[]) {
  return del<void>('/api/log/batch', { data: ids })
}
