/**
 * WebSocket 连接管理工具
 * 支持自动重连、心跳保活、消息订阅
 */
import { useUserStore } from '@/stores/user'

type MessageCallback = (msg: any) => void

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

    // 关闭旧连接
    this.disconnect()

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    this.url = `${protocol}//${window.location.host}/api/ws/message?token=${authToken}`
    this.isManualClose = false

    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        console.log('[WebSocket] 连接已建立')
        this.reconnectCount = 0
        this.startHeartbeat()
      }

      this.ws.onmessage = (event) => {
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

      this.ws.onclose = (event) => {
        console.log(`[WebSocket] 连接关闭：code=${event.code}, reason=${event.reason}`)
        this.stopHeartbeat()
        this.tryReconnect()
      }

      this.ws.onerror = (error) => {
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
