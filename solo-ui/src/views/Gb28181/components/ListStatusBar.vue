<script setup lang="ts">
withDefaults(defineProps<{
  active: boolean
  width?: string
}>(), {
  width: '72px',
})
</script>

<template>
  <span
    class="list-status-bar"
    :class="{ 'is-active': active }"
    :style="{ width }"
    role="img"
    :aria-label="active ? '1' : '0'"
  />
</template>

<style scoped>
.list-status-bar {
  display: inline-block;
  height: 10px;
  border-radius: 999px;
  background: var(--el-color-danger);
  vertical-align: middle;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35);
}

.list-status-bar.is-active {
  background: var(--el-color-success);
}
</style>
