<template>
  <div class="flex h-full flex-col overflow-hidden">
    <!-- tab 行 -->
    <div class="flex h-10 shrink-0 items-center border-b border-border px-2">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="relative flex h-full items-center px-3 text-[13px] transition-colors"
        :class="
          active === t.key
            ? 'font-medium text-foreground'
            : 'text-muted-foreground hover:text-foreground'
        "
        @click="active = t.key"
      >
        {{ t.label }}
        <span
          v-if="active === t.key"
          class="absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-primary"
        />
      </button>
    </div>

    <div class="min-h-0 flex-1">
      <KeepAlive>
        <ChildPlatform v-if="active === 'child'" />
        <ParentPlatform v-else />
      </KeepAlive>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChildPlatform from '@/features/gb/ChildPlatform.vue'
import ParentPlatform from '@/features/gb/ParentPlatform.vue'

const active = ref<'child' | 'parent'>('child')
const tabs = [
  { key: 'child' as const, label: '下级平台' },
  { key: 'parent' as const, label: '上级平台' },
]
</script>
