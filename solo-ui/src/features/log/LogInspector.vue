<script setup lang="ts">
import {
  IconX,
  IconFileText,
  IconTrash,
  IconClock,
  IconRoute,
  IconDeviceDesktop,
  IconAlertTriangle,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { PERM } from '@/features/access/permissions'
import { businessMetaOf } from './logMeta'
import type { SysLog } from '@/types/api'

defineProps<{ log?: SysLog }>()
const emit = defineEmits<{
  close: []
  deleted: [log: SysLog]
}>()
</script>

<template>
  <div v-if="log" class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconFileText class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">{{ log.description || '未命名操作' }}</div>
        <div class="text-xs text-muted-foreground">
          {{ log.module }} · {{ log.createTime }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 概览 -->
    <dl class="space-y-2.5 text-xs">
      <div class="flex items-center justify-between">
        <dt class="text-muted-foreground">业务类型</dt>
        <dd>
          <span
            class="inline-flex w-fit rounded px-1.5 py-0.5"
            :class="businessMetaOf(log.businessType).badge"
          >
            {{ businessMetaOf(log.businessType).label }}
          </span>
        </dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="text-muted-foreground">响应状态</dt>
        <dd>
          <span
            class="inline-flex w-fit rounded px-1.5 py-0.5"
            :class="
              log.responseStatus === 1
                ? 'bg-emerald-500/10 text-emerald-600'
                : 'bg-destructive/10 text-destructive'
            "
          >
            {{ log.responseStatus === 1 ? '成功' : '失败' }}
          </span>
        </dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1 text-muted-foreground">
          <IconClock class="size-3.5" />响应耗时
        </dt>
        <dd class="font-mono">{{ log.costMs }} ms</dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="text-muted-foreground">操作人员</dt>
        <dd class="font-medium">{{ log.operatorName || '—' }}</dd>
      </div>
    </dl>

    <!-- 请求 -->
    <div class="my-4 h-px bg-border" />
    <section>
      <h4 class="mb-2.5 flex items-center gap-1.5 text-[13px] font-medium">
        <IconRoute class="size-4 text-muted-foreground" />请求
      </h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-center gap-2">
          <span class="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]">
            {{ log.requestMethod || '—' }}
          </span>
          <span class="break-all font-mono text-[11px] text-muted-foreground">
            {{ log.requestUrl || '—' }}
          </span>
        </div>
        <pre
          class="max-h-44 overflow-auto break-all whitespace-pre-wrap rounded-md bg-muted/60 p-2 font-mono text-[11px]"
        >{{ log.requestParam || '—' }}</pre>
      </div>
    </section>

    <!-- 客户端 -->
    <div class="my-4 h-px bg-border" />
    <section>
      <h4 class="mb-2.5 flex items-center gap-1.5 text-[13px] font-medium">
        <IconDeviceDesktop class="size-4 text-muted-foreground" />客户端
      </h4>
      <dl class="space-y-2 text-xs">
        <div class="flex items-center justify-between">
          <dt class="text-muted-foreground">来源</dt>
          <dd>{{ log.clientSource || '—' }}</dd>
        </div>
        <div class="flex items-center justify-between">
          <dt class="text-muted-foreground">地址</dt>
          <dd class="font-mono">{{ log.clientIp || '—' }}</dd>
        </div>
        <dd class="break-all pt-1 font-mono text-[11px] text-muted-foreground">
          {{ log.userAgent || '—' }}
        </dd>
      </dl>
    </section>

    <!-- 错误信息 -->
    <template v-if="log.errorMsg">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-2.5 flex items-center gap-1.5 text-[13px] font-medium text-destructive">
          <IconAlertTriangle class="size-4" />错误信息
        </h4>
        <pre
          class="max-h-44 overflow-auto break-all whitespace-pre-wrap rounded-md bg-destructive/5 p-2 font-mono text-[11px] text-destructive"
        >{{ log.errorMsg }}</pre>
      </section>
    </template>

    <!-- 危险操作 -->
    <div class="my-4 h-px bg-border" />
    <Button
      v-access="PERM.LOG_DELETE"
      variant="destructive"
      size="sm"
      class="w-full"
      @click="emit('deleted', log)"
    >
      <IconTrash class="h-3.5 w-3.5" />
      删除该日志
    </Button>
  </div>
</template>
