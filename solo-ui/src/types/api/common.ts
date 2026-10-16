/** 跨模块共用：统一响应、分页、通用查询参数 */

/** 后端统一响应包装 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T = unknown> {
  total: number
  rows: T[]
}

/** 分页查询的通用入参 */
export interface PageRequest {
  page?: number
  pageSize?: number
}
