<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary">
        <IconRoute class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ position.deviceName || '位置点' }}
        </div>
        <div class="truncate text-xs text-muted-foreground font-mono">
          {{ position.deviceId }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 位置信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">位置信息</h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">定位时间</span>
          <span class="font-mono">{{ position.positionTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">经度</span>
          <span class="font-mono">{{ position.longitude ?? '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">纬度</span>
          <span class="font-mono">{{ position.latitude ?? '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">海拔</span>
          <span class="font-mono">{{ position.altitude != null ? `${position.altitude} 米` : '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">速度</span>
          <span class="font-mono">{{ position.speed != null ? position.speed : '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">方向</span>
          <span class="font-mono">{{ position.direction != null ? `${position.direction}°` : '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">接收时间</span>
          <span class="font-mono">{{ position.receiveTime || '—' }}</span>
        </div>
        <div class="flex items-center justify-between gap-3">
          <span class="shrink-0 text-muted-foreground">入库时间</span>
          <span class="font-mono">{{ position.createTime || '—' }}</span>
        </div>
      </div>
    </section>

    <!-- 危险操作 -->
    <div class="my-4 h-px bg-border" />
    <section>
      <h4 class="mb-2 text-[13px] font-medium text-destructive">危险操作</h4>
      <p class="mb-3 text-[11px] text-muted-foreground">删除该位置点后不可恢复。</p>
      <Button
        v-access="PERM.GB_POSITION_DELETE"
        variant="destructive"
        size="sm"
        class="w-full"
        @click="emit('delete')"
      >
        <IconTrash class="size-3.5" />
        删除该位置点
      </Button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { IconRoute, IconX, IconTrash } from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { PERM } from '@/features/access/permissions'
import type { GbMobilePosition } from '@/types/api'

defineProps<{
  position: GbMobilePosition
}>()
const emit = defineEmits<{
  close: []
  delete: []
}>()
</script>
