import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { refreshTokenApi } from '@/api/auth'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 白名单路径（不需要 token）
const WHITE_LIST = ['/auth/login', '/auth/refresh']

// 刷新 token 锁，防止并发刷新
let isRefreshing = false
// 缓存等待重试的请求
let pendingRequests: ((token: string) => void)[] = []

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const token = userStore.token
    if (token && !WHITE_LIST.some((path) => config.url?.startsWith(path))) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['X-Client-Source'] = 'web'
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    if (response.config.responseType === 'blob') {
      return response as any
    }

    const res = response.data

    if (res.code === 200) {
      return res as any
    }

    // 根据 code 提示错误
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async (error: any) => {
    const { response, config } = error
    if (response) {
      switch (response.status) {
        case 401: {
          const userStore = useUserStore()
          // refresh 接口本身 401，或无 refresh token：直接登出
          if (config.url?.includes('/auth/refresh') || !userStore.refreshToken) {
            userStore.logout()
            window.location.href = '/login'
            ElMessage.error('登录已过期，请重新登录')
            break
          }
          // 正在刷新，将当前请求加入等待队列
          if (isRefreshing) {
            return new Promise((resolve) => {
              pendingRequests.push((token: string) => {
                config.headers.Authorization = `Bearer ${token}`
                resolve(service(config))
              })
            })
          }
          // 开始刷新 token
          isRefreshing = true
          try {
            const { data } = await refreshTokenApi({ refreshToken: userStore.refreshToken })
            userStore.setTokens(data)
            // 重试所有等待的请求
            config.headers.Authorization = `Bearer ${data.token}`
            pendingRequests.forEach((cb) => cb(data.token))
            pendingRequests = []
            // 重试当前请求
            return service(config)
          } catch {
            // 刷新失败，清除所有等待请求并登出
            pendingRequests = []
            userStore.logout()
            window.location.href = '/login'
            ElMessage.error('登录已过期，请重新登录')
          } finally {
            isRefreshing = false
          }
          break
        }
        case 429:
          ElMessage.warning(response.data?.message || '请求过于频繁，请稍后再试')
          break
        case 403:
          ElMessage.error('没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接异常')
    }
    return Promise.reject(error)
  }
)

// 封装通用的请求方法
export function get<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.get(url, config)
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.post(url, data, config)
}

export function put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.put(url, data, config)
}

export function del<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
  return service.delete(url, config)
}

export default service
