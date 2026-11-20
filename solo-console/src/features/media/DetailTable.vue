<template>
  <div class="relative max-h-[46vh] overflow-auto rounded-md border">
    <div v-if="loading" class="absolute right-2 top-2 z-10">
      <IconLoader2 class="size-4 animate-spin text-muted-foreground" />
    </div>
    <table v-if="count" class="w-full table-fixed border-collapse text-left text-[13px]">
      <thead class="sticky top-0 z-10 border-b border-border bg-muted/90 text-xs text-muted-foreground">
        <slot name="head" />
      </thead>
      <tbody>
        <slot />
      </tbody>
    </table>
    <div
      v-else-if="!loading"
      class="py-8 text-center text-xs text-muted-foreground"
    >
      {{ empty }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconLoader2 } from '@tabler/icons-vue'

withDefaults(defineProps<{ loading?: boolean; count: number; empty?: string }>(), {
  empty: '暂无数据',
})
</script>
