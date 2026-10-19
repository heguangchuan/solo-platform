import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import type { Result, RefreshTokenResponse } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/lib/toast'

/** 不需要携带 token 的白名单路径 */
const WHITE_LIST = ['/auth/login', '/auth/refresh']

// 刷新 token 锁，防止并发刷新
let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

/**
 * 会话彻底失效：清登录态、提示一次并跳转登录页。
 * 加跳转锁，避免并发 401 重复 toast / 重复 replace。
 */
let isRedirecting = false
async function handleSessionExpired() {
  const authStore = useAuthStore()
  authStore.logout()
  if (isRedirecting) return
  isRedirecting = true
  toast.error('登录已过期，请重新登录')
  const router = (await import('@/app/router')).default
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    await router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
  isRedirecting = false
}

const service: AxiosInstance = axios.create({
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 请求拦截器：注入 JWT 与客户端来源
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    const token = authStore.token
    if (token && !WHITE_LIST.some((p) => config.url?.startsWith(p))) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['X-Client-Source'] = 'web'
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    if (response.config.responseType === 'blob' || (response.config as AxiosRequestConfig & { rawResponse?: boolean }).rawResponse) {
      return response as unknown as AxiosResponse
    }
    const res = response.data
    if (res.code === 200) {
      return res as unknown as AxiosResponse
    }
    toast.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async (error: any) => {
    const { response, config } = error as {
      response?: AxiosResponse<Result>
      config?: InternalAxiosRequestConfig
    }

    if (response && config) {
      // 401：尝试滚动刷新
      if (response.status === 401) {
        const authStore = useAuthStore()
        // 刷新请求自身 401：交由外层发起刷新的 catch 统一处理，这里不再提示，避免重复
        if (config.url?.includes('/auth/refresh')) {
          return Promise.reject(error)
        }
        if (!authStore.refreshToken) {
          await handleSessionExpired()
          return Promise.reject(error)
        }
        if (isRefreshing) {
          return new Promise((resolve) => {
            pendingRequests.push((token: string) => {
              config.headers.Authorization = `Bearer ${token}`
              resolve(service(config))
            })
          })
        }
        isRefreshing = true
        try {
          const resp = await service.post<unknown, Result<RefreshTokenResponse>>(
            '/api/auth/refresh',
            { refreshToken: authStore.refreshToken }
          )
          authStore.setTokens(resp.data)
          pendingRequests.forEach((cb) => cb(resp.data.token))
          pendingRequests = []
          config.headers.Authorization = `Bearer ${resp.data.token}`
          return service(config)
        } catch {
          pendingRequests = []
          await handleSessionExpired()
        } finally {
          isRefreshing = false
        }
        return Promise.reject(error)
      }

      const msg = response.data?.message
      switch (response.status) {
        case 403:
          toast.error(msg || '没有权限访问')
          break
        case 404:
          toast.error(msg || '请求的资源不存在')
          break
        case 429:
          toast.warning(msg || '请求过于频繁，请稍后再试')
          break
        case 500:
          toast.error(msg || '服务器内部错误')
          break
        default:
          toast.error(msg || '请求失败')
      }
    } else {
      toast.error('网络连接异常')
    }
    return Promise.reject(error)
  }
)

/** 统一请求方法，返回解包后的 Result */
export function get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.get(url, config) as unknown as Promise<Result<T>>
}

export function post<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<Result<T>> {
  return service.post(url, data, config) as unknown as Promise<Result<T>>
}

export function put<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<Result<T>> {
  return service.put(url, data, config) as unknown as Promise<Result<T>>
}

export function del<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.delete(url, config) as unknown as Promise<Result<T>>
}

export default service
