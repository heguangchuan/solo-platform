<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { AvatarRoot } from "reka-ui"
import { cn } from "@/lib/utils"

const props = withDefaults(
  defineProps<{
    size?: number
    class?: HTMLAttributes["class"]
  }>(),
  { size: 32 }
)
</script>

<template>
  <AvatarRoot
    data-slot="avatar"
    :class="cn('relative flex shrink-0 overflow-hidden rounded-full', props.class)"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <slot />
  </AvatarRoot>
</template>
