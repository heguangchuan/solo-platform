/** 业务类型元数据：列表徽章与详情共用 */

export interface BusinessMeta {
  label: string
  /** 徽章样式类 */
  badge: string
}

export const BUSINESS_TYPE_MAP: Record<string, BusinessMeta> = {
  ADD: { label: '新增', badge: 'bg-emerald-500/10 text-emerald-600' },
  EDIT: { label: '修改', badge: 'bg-primary/10 text-primary' },
  DELETE: { label: '删除', badge: 'bg-destructive/10 text-destructive' },
  EXPORT: { label: '导出', badge: 'bg-amber-500/10 text-amber-600' },
  IMPORT: { label: '导入', badge: 'bg-amber-500/10 text-amber-600' },
  LOGIN: { label: '登录', badge: 'bg-muted text-muted-foreground' },
  LOGOUT: { label: '登出', badge: 'bg-muted text-muted-foreground' },
  OTHER: { label: '其他', badge: 'bg-muted text-muted-foreground' },
}

export const BUSINESS_TYPE_OPTIONS = Object.entries(BUSINESS_TYPE_MAP).map(([value, m]) => ({
  value,
  label: m.label,
}))

export const businessMetaOf = (type: string): BusinessMeta =>
  BUSINESS_TYPE_MAP[type] ?? { label: type, badge: 'bg-muted text-muted-foreground' }
