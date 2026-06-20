/**
 * WebSocket 连接管理工具
 * 支持自动重连、心跳保活、消息订阅
 */
import { useUserStore } from '@/stores/user'

type MessageCallback = (msg: any) => void

interface RefreshResponse {
  code: number
  message?: string
  data?: {
    token: string
    refreshToken: string
  }
}

function isJwtExpired(token: string, skew = 5000): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (!payload.exp) return false
    return payload.exp * 1000 <= Date.now() + skew
  } catch {
    return false
  }
}

class WebSocketClient {
  private ws: WebSocket | null = null
  private url: string = ''
  private reconnectTimer: any = null
  private heartbeatTimer: any = null
  private reconnectCount: number = 0
  private maxReconnect: number = 10
  private isManualClose: boolean = false
  private callbacks: Set<MessageCallback> = new Set()

  /**
   * 建立连接
   */
  connect(token?: string) {
    const userStore = useUserStore()
    const authToken = token || userStore.token

    if (!authToken) {
      console.warn('[WebSocket] 无可用 token，跳过连接')
      return
    }

    if (isJwtExpired(authToken)) {
      console.warn('[WebSocket] token 已过期，尝试刷新后重连')
      this.refreshAndReconnect()
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const nextUrl = `${protocol}//${window.location.host}/api/ws/message?token=${authToken}`

    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING) && this.url === nextUrl) {
      return
    }

    // 关闭旧连接
    this.disconnect()

    this.url = nextUrl
    this.isManualClose = false

    try {
      const socket = new WebSocket(this.url)
      this.ws = socket

      socket.onopen = () => {
        if (socket !== this.ws) return
        console.log('[WebSocket] 连接已建立')
        this.reconnectCount = 0
        this.startHeartbeat()
      }

      socket.onmessage = (event) => {
        if (socket !== this.ws) return
        if (event.data === 'pong') {
          // 心跳响应，忽略
          return
        }
        try {
          const message = JSON.parse(event.data)
          this.notifyCallbacks(message)
        } catch (e) {
          console.warn('[WebSocket] 消息解析失败:', event.data)
        }
      }

      socket.onclose = (event) => {
        if (socket !== this.ws) return
        console.log(`[WebSocket] 连接关闭：code=${event.code}, reason=${event.reason}`)
        this.stopHeartbeat()
        this.ws = null
        if (event.code === 1008 || event.code === 4001 || event.code === 4003) {
          this.refreshAndReconnect()
          return
        }
        this.tryReconnect()
      }

      socket.onerror = (error) => {
        if (socket !== this.ws) return
        console.error('[WebSocket] 连接错误:', error)
      }
    } catch (e) {
      console.error('[WebSocket] 创建连接失败:', e)
      this.tryReconnect()
    }
  }

  /**
   * 断开连接
   */
  disconnect() {
    this.isManualClose = true
    this.stopReconnect()
    this.stopHeartbeat()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  /**
   * 发送消息
   */
  send(data: string) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(data)
    }
  }

  /**
   * 订阅消息
   */
  subscribe(callback: MessageCallback) {
    this.callbacks.add(callback)
    return () => this.callbacks.delete(callback)
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  // ============== 内部方法 ==============

  private startHeartbeat() {
    this.stopHeartbeat()
    // 每 30 秒发送一次 ping
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send('ping')
      }
    }, 30000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private tryReconnect() {
    if (this.isManualClose || this.reconnectCount >= this.maxReconnect) {
      if (this.reconnectCount >= this.maxReconnect) {
        console.warn('[WebSocket] 达到最大重连次数，停止重连')
      }
      return
    }

    this.stopReconnect()
    // 指数退避：1s, 2s, 4s, 8s... 最大 30s
    const delay = Math.min(1000 * Math.pow(2, this.reconnectCount), 30000)
    console.log(`[WebSocket] ${delay / 1000}秒后第 ${this.reconnectCount + 1} 次重连`)

    this.reconnectTimer = setTimeout(() => {
      this.reconnectCount++
      this.connect()
    }, delay)
  }

  private stopReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private async refreshAndReconnect() {
    const userStore = useUserStore()
    if (!userStore.refreshToken) {
      this.isManualClose = true
      this.stopReconnect()
      return
    }

    this.stopReconnect()
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify({ refreshToken: userStore.refreshToken }),
      })
      if (!res.ok) throw new Error(`refresh failed: ${res.status}`)

      const result: RefreshResponse = await res.json()
      if (result.code !== 200 || !result.data?.token || !result.data?.refreshToken) {
        throw new Error(result.message || 'refresh failed')
      }

      userStore.setTokens(result.data)
      this.reconnectCount = 0
      this.connect(result.data.token)
    } catch (e) {
      console.warn('[WebSocket] token 刷新失败，停止重连', e)
      this.isManualClose = true
      this.stopReconnect()
    }
  }

  private notifyCallbacks(message: any) {
    this.callbacks.forEach((cb) => {
      try {
        cb(message)
      } catch (e) {
        console.error('[WebSocket] 消息回调异常:', e)
      }
    })
  }
}

// 全局单例
export const wsClient = new WebSocketClient()

// 自动初始化（登录后自动连接）
export function initWebSocket() {
  const userStore = useUserStore()
  if (userStore.token) {
    wsClient.connect()
  }
}

// 页面卸载时断开连接
if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', () => {
    wsClient.disconnect()
  })
}
