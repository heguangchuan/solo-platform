import type { OnlineUser } from '@/types/api'
import { get, del } from '@/utils/request'

/**
 * 在线用户列表（按账号 / 昵称模糊筛选）
 */
export function getOnlineListApi(params: { username?: string; nickname?: string } = {}) {
  return get<OnlineUser[]>('/system/online/list', { params })
}

/**
 * 强制下线
 */
export function kickOnlineApi(username: string) {
  return del<void>(`/system/online/kick/${encodeURIComponent(username)}`)
}
