/**
 * 跨模块共用：统一响应、分页、通用查询参数。
 */

/** 后端统一响应包装 */
export interface Result<T = any> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T = any> {
  total: number
  rows: T[]
}

/** 分页查询的通用入参（其它模块的 *PageRequest 都基于此扩展） */
export interface PageRequest {
  page?: number
  pageSize?: number
}
