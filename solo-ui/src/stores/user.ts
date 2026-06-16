import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfoVO } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const user = ref<UserInfoVO | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const pwdResetRequired = ref<boolean>(false)

  function setToken(t: string) {
    token.value = t
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
    user.value = null
    roles.value = []
    permissions.value = []
    pwdResetRequired.value = false
    localStorage.removeItem('solo-token')
  }

  // 持久化 token
  function loginSuccess(result: { token: string; user: UserInfoVO }) {
    token.value = result.token
    user.value = result.user
    pwdResetRequired.value = result.user?.pwdResetRequired === 1
    localStorage.setItem('solo-token', result.token)
  }

  function logout() {
    resetAll()
  }

  // 恢复 token
  function initFromStorage() {
    const t = localStorage.getItem('solo-token')
    if (t) {
      token.value = t
    }
  }

  return {
    token,
    user,
    roles,
    permissions,
    pwdResetRequired,
    setToken,
    setUser,
    setRoles,
    setPermissions,
    clearPwdResetRequired,
    loginSuccess,
    logout,
    initFromStorage,
  }
})
