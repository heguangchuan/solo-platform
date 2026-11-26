import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginRequest, UserInfoVO } from '@/types/api'
import { loginApi, getProfileApi } from '@/api/auth'

const TOKEN_KEY = 'solo_token'
const REFRESH_KEY = 'solo_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_KEY) || '')
  const user = ref<UserInfoVO | null>(null)

  const isLogin = computed(() => !!token.value)

  function setTokens(data: { token: string; refreshToken: string }) {
    token.value = data.token
    refreshToken.value = data.refreshToken
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
  }

  async function login(payload: LoginRequest) {
    const res = await loginApi(payload)
    setTokens(res.data)
    await loadProfile()
  }

  async function loadProfile() {
    const profile = await getProfileApi()
    user.value = profile.data
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }

  return {
    token,
    refreshToken,
    user,
    isLogin,
    setTokens,
    login,
    loadProfile,
    logout,
  }
})
