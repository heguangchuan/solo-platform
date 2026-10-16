import { get, del } from '@/utils/request'
import type { OnlineUser } from '@/types/api'

/** 在线用户列表（按账号 / 昵称模糊筛选） */
export function getOnlineListApi(params: { username?: string; nickname?: string } = {}) {
  return get<OnlineUser[]>('/api/online/list', { params })
}

/** 强制下线 */
export function kickOnlineApi(username: string) {
  return del<void>(`/api/online/kick/${encodeURIComponent(username)}`)
}
