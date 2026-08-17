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
  /** 通道聚合 */
  channel: DashboardChannel
  /** ZLM 节点列表 */
  mediaServer: DashboardMediaServer[]
  /** 流聚合 */
  stream: DashboardStream
  /** 告警聚合 */
  alert: DashboardAlert
  /** 24h 趋势 */
  trends: DashboardTrends
  /** 厂商分布 */
  manufacturerDist: DashboardDistribution[]
  /** 较 1h 前的差值（KPI 卡 +"-" 箭头用） */
  compareHour: DashboardCompareHour
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

export interface DashboardChannel {
  total: number
  online: number
}

export interface DashboardMediaServer {
  serverId: string
  name: string | null
  /** 0 离线 1 在线 */
  status: 0 | 1
  loadScore: number
  hostIp: string
  httpPort: number
}

export interface DashboardStream {
  live: number
  push: number
  proxy: number
}

export interface DashboardAlert {
  unreadCount: number
  todayCount: number
  recent: DashboardAlertRecent[]
}

export interface DashboardAlertRecent {
  id: number
  title: string
  level: 'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT' | string
  /** 已格式化 "MM-dd HH:mm" */
  createTime: string
  route?: string | null
}

export interface DashboardTrends {
  deviceOnline24h: DashboardTrendPoint[]
  activeStream24h: DashboardTrendPoint[]
}

export interface DashboardTrendPoint {
  /** "HH:00" */
  hour: string
  value: number
}

export interface DashboardDistribution {
  name: string
  value: number
}

export interface DashboardCompareHour {
  /** 正数=增加，负数=减少；null=无 1h 前数据 */
  deviceOnline: number | null
  activeStream: number | null
  alertCount: number | null
}
