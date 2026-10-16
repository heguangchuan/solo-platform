import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

/**
 * 界面偏好：主题 / 密度
 * 主题通过 <html> 的 .dark class 生效（Tailwind darkMode: class）
 */
export const useUiStore = defineStore('ui', () => {
  const dark = ref(localStorage.getItem('solo_theme') !== 'light') // 默认深色
  const density = ref<'compact' | 'comfortable'>(
    (localStorage.getItem('solo_density') as 'compact' | 'comfortable') || 'compact'
  )

  function applyClass(isDark: boolean) {
    document.documentElement.classList.toggle('dark', isDark)
  }

  function toggleTheme() {
    dark.value = !dark.value
  }

  function setDensity(v: 'compact' | 'comfortable') {
    density.value = v
  }

  watch(dark, (v) => {
    localStorage.setItem('solo_theme', v ? 'dark' : 'light')
    applyClass(v)
  })

  // 初始化即同步到 <html>
  applyClass(dark.value)

  return { dark, density, toggleTheme, setDensity }
})
