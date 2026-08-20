/**
 * 首页仪表盘聚合统计响应类型
 *
 * <p>与 {@code com.solo.dashboard.response.DashboardStatsResponse} 一一对应，
 * 见后端 {@code DashboardController#stats}。</p>
 */
export interface DashboardStats {
  /** 个性化问候 */
  greeting: DashboardGreeting
  /** 国标设备聚合 */
  device: DashboardDevice
  /** ZLM 节点列表 */
  mediaServer: DashboardMediaServer[]
  /** 流聚合 */
  stream: DashboardStream
  /** 消息中心聚合（未读 / 今日） */
  alert: DashboardAlert
  trends: DashboardTrends
  /** 厂商分布 */
  manufacturerDist: DashboardDistribution[]
}

export interface DashboardGreeting {
  username: string
  /** 0-23，本地小时数 */
  hour: number
}

export interface DashboardDevice {
  total: number
  online: number
  offline: number
  unregistered: number
  /** 0~1，前端可乘 100 渲染百分比 */
  onlineRate: number
}

export interface DashboardMediaServer {
  serverId: string
  name: string | null
  /** 0 离线 1 在线 */
  status: 0 | 1
  loadScore: number
  hostIp: string
  httpPort: number
  /** 该节点上的国标点播/回放会话数 */
  gbStream?: number
  /** 该节点上的第三方推流数 */
  pushStream?: number
  /** 该节点上的拉流代理数 */
  proxyStream?: number
}

export interface DashboardStream {
  live: number
  push: number
  proxy: number
}

export interface DashboardAlert {
  unreadCount: number
  todayCount: number
}

export interface DashboardTrends {
  deviceOnline24h: DashboardTrendPoint[]
  activeStream24h: DashboardTrendPoint[]
}

export interface DashboardTrendPoint {
  value: number
}

export interface DashboardDistribution {
  name: string
  value: number
}
