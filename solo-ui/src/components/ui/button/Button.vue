<script setup lang="ts">
import type { PrimitiveProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import type { ButtonVariants } from "."
import { IconLoader2 } from "@tabler/icons-vue"
import { Primitive } from "reka-ui"
import { cn } from "@/lib/utils"
import { buttonVariants } from "."

interface Props extends PrimitiveProps {
  variant?: ButtonVariants["variant"]
  size?: ButtonVariants["size"]
  loading?: boolean
  class?: HTMLAttributes["class"]
}

const props = withDefaults(defineProps<Props>(), {
  as: "button",
  loading: false,
})

const attrs = useAttrs() as { type?: "button" | "submit" | "reset"; disabled?: boolean }
</script>

<template>
  <Primitive
    data-slot="button"
    :data-variant="variant"
    :data-size="size"
    :as="as"
    :as-child="asChild"
    :type="attrs.type ?? (as === 'button' ? 'button' : undefined)"
    :disabled="loading || attrs.disabled"
    :class="cn(buttonVariants({ variant, size }), props.class)"
  >
    <IconLoader2 v-if="loading" class="size-4 animate-spin" />
    <slot />
  </Primitive>
</template>
