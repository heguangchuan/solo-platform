<script setup lang="ts">
import type { TabsTriggerProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { TabsTrigger, useForwardProps } from "reka-ui"
import { cn } from "@/lib/utils"

const props = defineProps<TabsTriggerProps & { class?: HTMLAttributes["class"] }>()

const delegatedProps = reactiveOmit(props, "class")

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <TabsTrigger
    v-bind="forwardedProps"
    data-slot="tabs-trigger"
    :class="
      cn(
        `-mb-px inline-flex h-auto flex-none items-center justify-center gap-1.5 rounded-none border-b-2 border-transparent bg-transparent px-3 pb-2.5 pt-1 text-sm font-normal whitespace-nowrap transition-colors data-[state=active]:border-foreground data-[state=active]:font-semibold data-[state=active]:text-foreground data-[state=inactive]:text-foreground/45 data-[state=inactive]:hover:text-foreground/80 focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4`,
        props.class,
      )
    "
  >
    <slot />
  </TabsTrigger>
</template>
