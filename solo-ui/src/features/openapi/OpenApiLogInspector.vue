<script setup lang="ts">
import {
  IconX,
  IconFileText,
  IconClock,
  IconRoute,
  IconDeviceDesktop,
  IconAlertTriangle,
  IconCopy,
  IconKey,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { toast } from '@/lib/toast'
import type { OpenApiLog } from '@/types/api'

const props = defineProps<{ log?: OpenApiLog }>()
defineEmits<{ close: [] }>()

const isSuccess = (log?: OpenApiLog) => log?.bizCode === 200

const onCopy = async (text?: string) => {
  if (!text) return
  await navigator.clipboard.writeText(text)
  toast.success('已复制')
}
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
        <div class="flex items-center gap-2">
          <span
            class="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]"
          >{{ log.method }}</span>
          <span class="truncate font-mono text-[13px]">{{ log.path }}</span>
        </div>
        <div class="mt-0.5 text-xs text-muted-foreground">{{ log.createTime }}</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="$emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 概览 -->
    <dl class="space-y-2.5 text-xs">
      <div class="flex items-center justify-between">
        <dt class="text-muted-foreground">调用结果</dt>
        <dd>
          <span
            class="inline-flex w-fit items-center gap-1 rounded px-1.5 py-0.5"
            :class="
              isSuccess(log)
                ? 'bg-emerald-500/10 text-emerald-600'
                : 'bg-destructive/10 text-destructive'
            "
          >
            {{ isSuccess(log) ? '成功' : `失败 ${log.bizCode ?? ''}` }}
          </span>
        </dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="text-muted-foreground">HTTP 状态</dt>
        <dd class="font-mono">{{ log.httpStatus }}</dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1 text-muted-foreground">
          <IconClock class="size-3.5" />耗时
        </dt>
        <dd class="font-mono">{{ log.costMs }} ms</dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1 text-muted-foreground">
          <IconKey class="size-3.5" />应用
        </dt>
        <dd class="max-w-[260px] truncate font-mono" :title="log.appKey || ''">
          {{ log.appKey || '—（未提供）' }}
        </dd>
      </div>
    </dl>

    <!-- 请求 -->
    <div class="my-4 h-px bg-border" />
    <section>
      <h4 class="mb-2.5 flex items-center gap-1.5 text-[13px] font-medium">
        <IconRoute class="size-4 text-muted-foreground" />请求
      </h4>
      <div class="space-y-2 text-xs">
        <div class="flex items-start gap-2">
          <span class="break-all font-mono text-[11px] text-muted-foreground">
            {{ log.path }}<template v-if="log.queryString">?{{ log.queryString }}</template>
          </span>
        </div>
        <div class="flex items-center gap-1.5">
          <span class="shrink-0 text-muted-foreground">requestId</span>
          <span class="min-w-0 flex-1 truncate font-mono text-[11px]" :title="log.requestId || ''">
            {{ log.requestId || '—' }}
          </span>
          <Button
            v-if="log.requestId"
            variant="ghost"
            size="icon-sm"
            class="size-6 shrink-0"
            title="复制 requestId"
            @click="onCopy(log.requestId)"
          >
            <IconCopy class="size-3.5" />
          </Button>
        </div>
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
          <dt class="text-muted-foreground">来源 IP</dt>
          <dd class="font-mono">{{ log.clientIp || '—' }}</dd>
        </div>
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
  </div>
</template>
