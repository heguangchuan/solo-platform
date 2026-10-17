<template>
  <DialogRoot :open="state.visible" @update:open="(v: boolean) => !v && settle(false)">
    <DialogContent class="max-w-sm">
      <DialogHeader>
        <DialogTitle>{{ state.title }}</DialogTitle>
        <DialogDescription v-if="state.description">{{ state.description }}</DialogDescription>
      </DialogHeader>
      <DialogFooter class="mt-2">
        <Button variant="outline" size="sm" @click="settle(false)">
          {{ state.cancelText || '取消' }}
        </Button>
        <Button
          :variant="state.danger ? 'destructive' : 'default'"
          size="sm"
          @click="settle(true)"
        >
          {{ state.confirmText || '确认' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </DialogRoot>
</template>

<script setup lang="ts">
import { DialogRoot, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useConfirmState } from '@/lib/confirm'

const { state, settle } = useConfirmState()
</script>
