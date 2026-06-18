import type { SysTask, SysTaskLog, TaskLogPageRequest, TaskPageRequest, TaskRequest, PageResult } from '@/types/api'
import { post, put, del } from '@/utils/request'

export function getTaskPageApi(data: TaskPageRequest) {
  return post<PageResult<SysTask>>('/api/task/page', data)
}

export function addTaskApi(data: TaskRequest) {
  return post<void>('/api/task', data)
}

export function editTaskApi(id: string, data: TaskRequest) {
  return put<void>(`/api/task/${id}`, data)
}

export function deleteTaskApi(id: string) {
  return del<void>(`/api/task/${id}`)
}

export function deleteTaskBatchApi(ids: string[]) {
  return del<void>('/api/task/batch', { data: ids })
}

export function switchTaskStatusApi(id: string, status: number) {
  return put<void>(`/api/task/${id}/switch/${status}`)
}

export function runTaskApi(id: string) {
  return post<void>(`/api/task/${id}/run`)
}

export function getTaskLogPageApi(data: TaskLogPageRequest) {
  return post<PageResult<SysTaskLog>>('/api/task/log/page', data)
}
