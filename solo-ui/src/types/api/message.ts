/** 系统消息 */
export interface SysMessage {
  id: number
  username: string
  title: string
  content: string
  type: 'SYSTEM' | 'NOTICE' | 'REMINDER' | 'BROADCAST' | 'DEVICE_ALARM'
  level: 'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'
  readFlag: number
  route?: string
  createTime: string
}
