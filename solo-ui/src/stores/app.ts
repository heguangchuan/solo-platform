import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function collapse() {
    sidebarCollapsed.value = true
  }

  function expand() {
    sidebarCollapsed.value = false
  }

  return {
    sidebarCollapsed,
    toggleSidebar,
    collapse,
    expand,
  }
})
