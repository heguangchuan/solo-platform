<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed bottom-5 right-5 z-[100] flex w-80 flex-col gap-2">
      <TransitionGroup name="toast">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="pointer-events-auto flex items-start gap-3 rounded-lg border border-border bg-card px-3.5 py-3 text-sm shadow-lg"
        >
          <span class="mt-0.5">
            <CheckCircle2 v-if="t.type === 'success'" class="h-4 w-4 text-[var(--success)]" />
            <XCircle v-else-if="t.type === 'error'" class="h-4 w-4 text-destructive" />
            <AlertTriangle v-else-if="t.type === 'warning'" class="h-4 w-4 text-[var(--warning)]" />
            <Info v-else class="h-4 w-4 text-primary" />
          </span>
          <span class="flex-1 leading-5">{{ t.message }}</span>
          <button
            v-if="t.action"
            type="button"
            class="shrink-0 text-xs font-medium text-primary hover:underline"
            @click="onAction(t)"
          >
            {{ t.action.label }}
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Info,
} from 'lucide-vue-next'
import { useToasts } from '@/lib/toast'
import type { ToastItem } from '@/lib/toast'

const { toasts, dismiss } = useToasts()

const onAction = (t: ToastItem) => {
  t.action?.onClick()
  dismiss(t.id)
}
</script>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}
.toast-enter-from {
  transform: translateX(24px);
  opacity: 0;
}
.toast-leave-to {
  transform: translateX(24px);
  opacity: 0;
}
</style>
