<template>
  <Button
    variant="ghost"
    size="icon"
    class="size-8"
    title="刷新 (R)"
    :disabled="isLoading || pending"
    @click="onClick"
  >
    <Loader2 v-if="pending" class="size-4 animate-spin" />
    <RefreshCw v-else class="size-4" />
  </Button>
</template>

<script setup lang="ts">
import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'
import { RefreshCw, Loader2 } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  /** 异步刷新动作（返回 Promise，期间按钮转圈防重点） */
  onRefresh: () => Promise<unknown> | unknown
  /** 外部加载态（如列表 loading），为 true 时禁用；支持 ref */
  loading?: MaybeRefOrGetter<boolean | undefined>
}>()

const pending = ref(false)
const isLoading = computed(() => !!toValue(props.loading))

const onClick = async () => {
  if (pending.value || isLoading.value) return
  pending.value = true
  try {
    await props.onRefresh()
  } finally {
    pending.value = false
  }
}
</script>
