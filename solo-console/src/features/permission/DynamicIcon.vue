<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { findIcon, loadAllIcons } from './iconLibrary'

const props = withDefaults(
  defineProps<{ name?: string | null; size?: number | string }>(),
  { size: 16 },
)

const loaded = ref(false)
onMounted(async () => {
  await loadAllIcons()
  loaded.value = true
})

const comp = computed(() => (props.name && loaded.value ? findIcon(props.name) : undefined))
</script>

<template>
  <component :is="comp" :size="size" v-if="comp" />
  <span v-else :style="{ width: typeof size === 'number' ? `${size}px` : size }" />
</template>
