<template>
  <div class="grid h-full place-items-center p-6">
    <div class="flex flex-col items-center gap-3 text-center">
      <div class="grid h-12 w-12 place-items-center rounded-xl border border-border bg-card text-muted-foreground">
        <Construction class="h-5 w-5" />
      </div>
      <div class="text-sm font-medium">{{ title }}</div>
      <p class="max-w-xs text-xs leading-relaxed text-muted-foreground">
        {{ hint || '该模块将在后续迭代迁移到新的设计系统，切片阶段暂不可用。' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Construction } from 'lucide-vue-next'

defineProps<{ title: string; hint?: string }>()
</script>
