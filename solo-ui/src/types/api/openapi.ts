/* ========== 开放平台第三方应用 ========== */

export interface OpenApp {
  id: string
  /** 应用标识，对接方签名时用的 appKey */
  appKey: string
  appName: string
  contact?: string
  contactPhone?: string
  /** 授权范围，逗号分隔，如 gb:device:read,gb:play:ctrl */
  scopes?: string
  /** IP 白名单，逗号分隔，支持 CIDR；空代表不限 */
  ipWhitelist?: string
  callbackUrl?: string
  /** 是否已配置 Webhook 回调签名密钥 */
  callbackSecretSet?: boolean
  /** 订阅事件类型，逗号分隔 */
  eventSubscriptions?: string
  /** QPS 上限，0 不限 */
  qpsLimit?: number
  /** 日调用量上限，0 不限 */
  dailyLimit?: number
  /** 0 停用 1 启用 */
  status: number
  expireTime?: string
  /** 旧密钥失效时间（换钥过渡期） */
  oldSecretExpireTime?: string
  remark?: string
  createTime?: string
}

export interface OpenAppPageRequest {
  page?: number
  pageSize?: number
  keyword?: string
  status?: number
}

/** 创建/编辑共用表单 */
export interface OpenAppSaveRequest {
  id?: string
  appName: string
  contact?: string
  contactPhone?: string
  scopes?: string
  ipWhitelist?: string
  callbackUrl?: string
  eventSubscriptions?: string
  qpsLimit?: number
  dailyLimit?: number
  expireTime?: string
  remark?: string
}

/** 明文密钥，只在创建/换钥响应里出现一次 */
export interface OpenAppCreatedSecret {
  id: string
  appKey: string
  appSecret: string
  callbackSecret?: string
}
