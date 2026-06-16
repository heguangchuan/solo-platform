<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/**
 * 刷新当前 Tag：先跳到本中转页，再立刻 replace 回去，
 * 这样 keep-alive 缓存的页面会被销毁后重新挂载。
 */
onMounted(() => {
  const target = (route.query.to as string) || '/dashboard'
  router.replace(target)
})
</script>

<template>
  <div></div>
</template>
