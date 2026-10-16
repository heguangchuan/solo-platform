import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginRequest, UserInfoVO, UserAuthResponse } from '@/types/api'
import { loginApi, getAuthMenusApi, getAuthPermissionsApi, getProfileApi } from '@/api/auth'
import type { PermCode } from '@/features/access/permissions'

const TOKEN_KEY = 'solo_token'
const REFRESH_KEY = 'solo_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_KEY) || '')
  const user = ref<UserInfoVO | null>(null)
  /** 后端下发的菜单树（原始结构） */
  const menus = ref<UserAuthResponse[]>([])
  /** 当前生效的权限标识集合 */
  const permissions = ref<Set<string>>(new Set())
  /** 视角模拟：以某个角色身份预览时，使用该集合；为空表示真实身份 */
  const simulatedPermissions = ref<Set<string> | null>(null)

  const isLogin = computed(() => !!token.value)
  /** 当前实际用于鉴权的集合（模拟视角优先） */
  const activePermissions = computed(() => simulatedPermissions.value ?? permissions.value)

  function setTokens(data: { token: string; refreshToken: string }) {
    token.value = data.token
    refreshToken.value = data.refreshToken
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
  }

  /** 登录并加载权限集合 */
  async function login(payload: LoginRequest) {
    const res = await loginApi(payload)
    setTokens(res.data)
    await loadProfile()
  }

  /** 拉取用户档案、菜单树与按钮权限集合 */
  async function loadProfile() {
    const [profile, menusRes, perms] = await Promise.all([
      getProfileApi(),
      getAuthMenusApi(),
      getAuthPermissionsApi(),
    ])
    user.value = profile.data
    menus.value = menusRes.data ?? []
    permissions.value = new Set(perms.data)
  }

  /** 以指定权限集合模拟视角（角色视角预览） */
  function simulate(perms: string[] | null) {
    simulatedPermissions.value = perms == null ? null : new Set(perms)
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    menus.value = []
    permissions.value = new Set()
    simulatedPermissions.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }

  /** 判断是否拥有某个按钮权限码 */
  function has(code: PermCode | string): boolean {
    return activePermissions.value.has(code)
  }

  return {
    token,
    refreshToken,
    user,
    menus,
    permissions,
    simulatedPermissions,
    isLogin,
    activePermissions,
    setTokens,
    login,
    loadProfile,
    simulate,
    logout,
    has,
  }
})
