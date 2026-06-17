import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfoVO } from '@/types/api'

const TOKEN_KEY = 'solo-token'
const REFRESH_TOKEN_KEY = 'solo-refresh-token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const refreshToken = ref<string>('')
  const user = ref<UserInfoVO | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const pwdResetRequired = ref<boolean>(false)

  function setToken(t: string) {
    token.value = t
  }

  /** 滚动刷新成功后同时更新两枚 token 与本地存储 */
  function setTokens(payload: { token: string; refreshToken: string }) {
    token.value = payload.token
    refreshToken.value = payload.refreshToken
    localStorage.setItem(TOKEN_KEY, payload.token)
    localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
  }

  function setUser(u: UserInfoVO) {
    user.value = u
    pwdResetRequired.value = u?.pwdResetRequired === 1
  }

  function setRoles(r: string[]) {
    roles.value = r
  }

  function setPermissions(p: string[]) {
    permissions.value = p
  }

  function clearPwdResetRequired() {
    pwdResetRequired.value = false
    if (user.value) user.value.pwdResetRequired = 0
  }

  function resetAll() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    roles.value = []
    permissions.value = []
    pwdResetRequired.value = false
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  // 持久化 token / refreshToken
  function loginSuccess(result: { token: string; refreshToken: string; user: UserInfoVO }) {
    token.value = result.token
    refreshToken.value = result.refreshToken
    user.value = result.user
    pwdResetRequired.value = result.user?.pwdResetRequired === 1
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(REFRESH_TOKEN_KEY, result.refreshToken)
  }

  function logout() {
    resetAll()
  }

  // 恢复 token / refreshToken
  function initFromStorage() {
    const t = localStorage.getItem(TOKEN_KEY)
    if (t) token.value = t
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (rt) refreshToken.value = rt
  }

  return {
    token,
    refreshToken,
    user,
    roles,
    permissions,
    pwdResetRequired,
    setToken,
    setTokens,
    setUser,
    setRoles,
    setPermissions,
    clearPwdResetRequired,
    loginSuccess,
    logout,
    initFromStorage,
  }
})
