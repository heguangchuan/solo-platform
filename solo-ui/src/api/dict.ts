import type {
  SysDictType,
  DictTypePageRequest,
  DictTypeRequest,
  SysDictData,
  DictDataPageRequest,
  DictDataRequest,
  PageResult,
} from '@/types/api'
import { get, post, put, del } from '@/utils/request'

// 字典类型
export function getDictTypePageApi(data: DictTypePageRequest) {
  return post<PageResult<SysDictType>>('/system/dict/type/page', data)
}

export function getDictTypeListApi() {
  return get<SysDictType[]>('/system/dict/type/list')
}

export function addDictTypeApi(data: DictTypeRequest) {
  return post<void>('/system/dict/type', data)
}

export function editDictTypeApi(id: string, data: DictTypeRequest) {
  return put<void>(`/system/dict/type/${id}`, data)
}

export function deleteDictTypeApi(id: string) {
  return del<void>(`/system/dict/type/${id}`)
}

export function deleteDictTypeBatchApi(ids: string[]) {
  return del<void>('/system/dict/type/batch', { data: ids })
}

// 字典数据
export function getDictDataPageApi(data: DictDataPageRequest) {
  return post<PageResult<SysDictData>>('/system/dict/data/page', data)
}

export function getDictDataByTypeApi(dictType: string) {
  return get<SysDictData[]>(`/system/dict/data/by-type/${dictType}`)
}

export function addDictDataApi(data: DictDataRequest) {
  return post<void>('/system/dict/data', data)
}

export function editDictDataApi(id: string, data: DictDataRequest) {
  return put<void>(`/system/dict/data/${id}`, data)
}

export function deleteDictDataApi(id: string) {
  return del<void>(`/system/dict/data/${id}`)
}

export function deleteDictDataBatchApi(ids: string[]) {
  return del<void>('/system/dict/data/batch', { data: ids })
}
