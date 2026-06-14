import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/api'
import { useUserStore } from '@/stores/user'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

// 白名单路径（不需要 token）
const WHITE_LIST = ['/auth/login']

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const token = userStore.token
    if (token && !WHITE_LIST.some((path) => config.url?.startsWith(path))) {
      config.headers.Authorization = `Bearer ${token}`
    }
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
  (error: any) => {
    const { response } = error
    if (response) {
      switch (response.status) {
        case 401:
          // token 过期/无效，跳转到登录页
          const userStore = useUserStore()
          userStore.logout()
          window.location.href = '/login'
          ElMessage.error('登录已过期，请重新登录')
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
