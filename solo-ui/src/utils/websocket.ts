/**
 * WebSocket 连接管理
 * 支持自动重连、心跳保活、token 失效后刷新重连
 */
import { useAuthStore } from '@/stores/auth'
import type { RefreshTokenResponse } from '@/types/api'

type MessageCallback = (msg: unknown) => void

const isJwtExpired = (token: string, skew = 5000): boolean => {
  try {
    const payload = JSON.parse(
      atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))
    )
    if (!payload.exp) return false
    return payload.exp * 1000 <= Date.now() + skew
  } catch {
    return false
  }
}

class WebSocketClient {
  private ws: WebSocket | null = null
  private url = ''
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private reconnectCount = 0
  private maxReconnect = 10
  private isManualClose = false
  private callbacks = new Set<MessageCallback>()

  /** 建立连接 */
  connect = (token?: string) => {
    const authStore = useAuthStore()
    const authToken = token || authStore.token

    if (!authToken) {
      console.warn('[WebSocket] 无可用 token，跳过连接')
      return
    }

    if (isJwtExpired(authToken)) {
      console.warn('[WebSocket] token 已过期，刷新后重连')
      this.refreshAndReconnect()
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const nextUrl = `${protocol}//${window.location.host}/api/ws/message?token=${authToken}`

    if (
      this.ws &&
      (this.ws.readyState === WebSocket.OPEN ||
        this.ws.readyState === WebSocket.CONNECTING) &&
      this.url === nextUrl
    ) {
      return
    }

    this.disconnect()

    this.url = nextUrl
    this.isManualClose = false

    try {
      const socket = new WebSocket(this.url)
      this.ws = socket

      socket.onopen = () => {
        if (socket !== this.ws) return
        this.reconnectCount = 0
        this.startHeartbeat()
      }

      socket.onmessage = (event) => {
        if (socket !== this.ws) return
        if (event.data === 'pong') return
        try {
          this.notifyCallbacks(JSON.parse(event.data))
        } catch {
          console.warn('[WebSocket] 消息解析失败:', event.data)
        }
      }

      socket.onclose = (event) => {
        if (socket !== this.ws) return
        this.stopHeartbeat()
        this.ws = null
        if (event.code === 1008 || event.code === 4001 || event.code === 4003) {
          this.refreshAndReconnect()
          return
        }
        this.tryReconnect()
      }

      socket.onerror = () => {
        if (socket !== this.ws) return
        console.error('[WebSocket] 连接错误')
      }
    } catch {
      console.error('[WebSocket] 创建连接失败')
      this.tryReconnect()
    }
  }

  /** 主动断开 */
  disconnect = () => {
    this.isManualClose = true
    this.stopReconnect()
    this.stopHeartbeat()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  /** 订阅消息，返回取消订阅函数 */
  subscribe = (callback: MessageCallback) => {
    this.callbacks.add(callback)
    return () => this.callbacks.delete(callback)
  }

  /** 是否处于连接状态 */
  isConnected = (): boolean => {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  // ============== 内部方法 ==============

  private startHeartbeat = () => {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send('ping')
      }
    }, 30000)
  }

  private stopHeartbeat = () => {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private tryReconnect = () => {
    if (this.isManualClose) return
    if (this.reconnectCount >= this.maxReconnect) {
      console.warn('[WebSocket] 达到最大重连次数，停止重连')
      return
    }

    this.stopReconnect()
    const delay = Math.min(1000 * 2 ** this.reconnectCount, 30000)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectCount++
      this.connect()
    }, delay)
  }

  private stopReconnect = () => {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private refreshAndReconnect = async () => {
    const authStore = useAuthStore()
    if (!authStore.refreshToken) {
      this.isManualClose = true
      this.stopReconnect()
      return
    }

    this.stopReconnect()
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify({ refreshToken: authStore.refreshToken }),
      })
      if (!res.ok) throw new Error(`refresh failed: ${res.status}`)

      const result: {
        code: number
        message?: string
        data?: RefreshTokenResponse
      } = await res.json()
      if (result.code !== 200 || !result.data) {
        throw new Error(result.message || 'refresh failed')
      }

      authStore.setTokens(result.data)
      this.reconnectCount = 0
      this.connect(result.data.token)
    } catch {
      console.warn('[WebSocket] token 刷新失败，停止重连')
      this.isManualClose = true
      this.stopReconnect()
    }
  }

  private notifyCallbacks = (message: unknown) => {
    this.callbacks.forEach((cb) => {
      try {
        cb(message)
      } catch (e) {
        console.error('[WebSocket] 消息回调异常:', e)
      }
    })
  }
}

export const wsClient = new WebSocketClient()
