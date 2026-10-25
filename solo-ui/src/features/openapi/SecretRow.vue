<template>
  <div class="flex items-center gap-2">
    <span class="w-20 shrink-0 text-[11px] text-muted-foreground">{{ label }}</span>
    <code
      class="min-w-0 flex-1 break-all rounded bg-background px-2 py-1 font-mono text-xs"
      :title="value"
    >{{ value }}</code>
    <Button variant="ghost" size="icon-sm" @click="onCopy">
      <IconCheck v-if="copied" class="h-3.5 w-3.5 text-emerald-500" />
      <IconCopy v-else class="h-3.5 w-3.5" />
    </Button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { IconCopy, IconCheck } from '@tabler/icons-vue'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'

const props = defineProps<{ label: string; value: string }>()

const copied = ref(false)

const onCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}
</script>
