<script setup lang="ts">
import type { TabsListProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { TabsList, useForwardProps } from "reka-ui"
import { cn } from "@/lib/utils"

const props = defineProps<TabsListProps & { class?: HTMLAttributes["class"] }>()

const delegatedProps = reactiveOmit(props, "class")

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <TabsList
    v-bind="forwardedProps"
    data-slot="tabs-list"
    :class="
      cn(
        'flex h-auto w-fit items-end justify-start gap-1 rounded-none border-b border-border bg-transparent p-0',
        props.class,
      )
    "
  >
    <slot />
  </TabsList>
</template>
