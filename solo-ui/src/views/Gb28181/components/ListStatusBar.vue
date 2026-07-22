<script setup lang="ts">
withDefaults(defineProps<{
  active: boolean
  width?: string
}>(), {
  width: '72px',
})
</script>

<template>
  <el-tag :type="active ? 'success' : 'danger'" size="small">
    {{ active ? '在线' : '离线' }}
  </el-tag>
</template>

<style scoped>
</style>
