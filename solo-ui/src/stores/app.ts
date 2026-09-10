import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const THEME_KEY = 'app-theme-dark'
const PRIMARY_KEY = 'app-theme-primary'

const DEFAULT_PRIMARY = '#409eff'

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '')
  const v = h.length === 3
    ? h.split('').map((c) => c + c).join('')
    : h
  const num = parseInt(v, 16)
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255]
}

function mix(color: [number, number, number], target: [number, number, number], weight: number): string {
  const r = Math.round(color[0] * (1 - weight) + target[0] * weight)
  const g = Math.round(color[1] * (1 - weight) + target[1] * weight)
  const b = Math.round(color[2] * (1 - weight) + target[2] * weight)
  return `rgb(${r}, ${g}, ${b})`
}

function applyPrimaryColor(hex: string, dark: boolean) {
  const root = document.documentElement
  const base = hexToRgb(hex)
  const mixTarget: [number, number, number] = dark ? [0, 0, 0] : [255, 255, 255]
  root.style.setProperty('--el-color-primary', hex)
  for (let i = 1; i <= 9; i++) {
    root.style.setProperty(`--el-color-primary-light-${i}`, mix(base, mixTarget, i * 0.1))
  }
  root.style.setProperty('--el-color-primary-dark-2', mix(base, dark ? [255, 255, 255] : [0, 0, 0], 0.2))
}

function applyTheme(dark: boolean, primary: string) {
  const html = document.documentElement
  if (dark) html.classList.add('dark')
  else html.classList.remove('dark')
  applyPrimaryColor(primary, dark)
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const logoVisible = ref(true)
  const isDark = ref(localStorage.getItem(THEME_KEY) === '1')
  const primaryColor = ref(localStorage.getItem(PRIMARY_KEY) || DEFAULT_PRIMARY)

  applyTheme(isDark.value, primaryColor.value)

  watch(isDark, (v) => {
    localStorage.setItem(THEME_KEY, v ? '1' : '0')
    applyTheme(v, primaryColor.value)
  })

  watch(primaryColor, (v) => {
    localStorage.setItem(PRIMARY_KEY, v)
    applyTheme(isDark.value, v)
  })

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleLogo() {
    logoVisible.value = !logoVisible.value
  }

  function collapse() {
    sidebarCollapsed.value = true
  }

  function expand() {
    sidebarCollapsed.value = false
  }

  function toggleDark() {
    isDark.value = !isDark.value
  }

  function setPrimaryColor(color: string) {
    primaryColor.value = color
  }

  return {
    sidebarCollapsed,
    logoVisible,
    isDark,
    primaryColor,
    toggleSidebar,
    toggleLogo,
    collapse,
    expand,
    toggleDark,
    setPrimaryColor,
  }
})
