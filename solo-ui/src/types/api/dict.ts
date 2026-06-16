/** 字典管理相关：字典类型 + 字典数据 */
import type { PageRequest } from './common'

/* ===== 字典类型 ===== */
export interface DictTypePageRequest extends PageRequest {
  dictName?: string
  dictType?: string
  status?: number
}

export interface DictTypeRequest {
  dictName: string
  dictType: string
  status: number
  remark?: string
}

export interface SysDictType {
  id: string
  dictName: string
  dictType: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

/* ===== 字典数据 ===== */
export interface DictDataPageRequest extends PageRequest {
  dictType?: string
  dictLabel?: string
  status?: number
}

export interface DictDataRequest {
  dictType: string
  dictLabel: string
  dictValue: string
  sort?: number
  cssClass?: string
  status: number
  remark?: string
}

export interface SysDictData {
  id: string
  dictType: string
  dictLabel: string
  dictValue: string
  sort: number
  cssClass: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}
