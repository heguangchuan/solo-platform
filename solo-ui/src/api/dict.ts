import { get, post, put, del } from '@/utils/request'
import type {
  SysDictType,
  DictTypePageRequest,
  DictTypeRequest,
  SysDictData,
  DictDataPageRequest,
  DictDataRequest,
  PageResult,
} from '@/types/api'

/* ===== 字典类型 ===== */
export function getDictTypePageApi(data: DictTypePageRequest) {
  return post<PageResult<SysDictType>>('/api/dict/type/page', data)
}

export function getDictTypeListApi() {
  return get<SysDictType[]>('/api/dict/type/list')
}

export function addDictTypeApi(data: DictTypeRequest) {
  return post<void>('/api/dict/type', data)
}

export function editDictTypeApi(id: string, data: DictTypeRequest) {
  return put<void>(`/api/dict/type/${id}`, data)
}

export function deleteDictTypeApi(id: string) {
  return del<void>(`/api/dict/type/${id}`)
}

export function deleteDictTypeBatchApi(ids: string[]) {
  return del<void>('/api/dict/type/batch', { data: ids })
}

/* ===== 字典数据 ===== */
export function getDictDataPageApi(data: DictDataPageRequest) {
  return post<PageResult<SysDictData>>('/api/dict/data/page', data)
}

export function getDictDataByTypeApi(dictType: string) {
  return get<SysDictData[]>(`/api/dict/data/by-type/${dictType}`)
}

export function addDictDataApi(data: DictDataRequest) {
  return post<void>('/api/dict/data', data)
}

export function editDictDataApi(id: string, data: DictDataRequest) {
  return put<void>(`/api/dict/data/${id}`, data)
}

export function deleteDictDataApi(id: string) {
  return del<void>(`/api/dict/data/${id}`)
}

export function deleteDictDataBatchApi(ids: string[]) {
  return del<void>('/api/dict/data/batch', { data: ids })
}
