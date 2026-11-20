<script setup lang="ts">
import type { TabsContentProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { TabsContent, useForwardProps } from "reka-ui"
import { cn } from "@/lib/utils"

const props = defineProps<TabsContentProps & { class?: HTMLAttributes["class"] }>()

const delegatedProps = reactiveOmit(props, "class")

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <TabsContent
    v-bind="forwardedProps"
    data-slot="tabs-content"
    :class="
      cn(
        'flex-1 outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
        props.class,
      )
    "
  >
    <slot />
  </TabsContent>
</template>
