<script setup lang="ts">
import { computed } from 'vue'
import {
  IconX,
  IconUser,
  IconClock,
  IconRoute,
  IconDeviceDesktop,
  IconPlugConnectedX,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import type { OnlineUser } from '@/types/api'
import { PERM } from '@/features/access/permissions'
import { parseUa } from './uaMeta'

const props = defineProps<{
  user: OnlineUser
  /** 是否为当前登录账号（禁止自踢） */
  self?: boolean
}>()

const emit = defineEmits<{
  close: []
  kick: [user: OnlineUser]
}>()

const ua = computed(() => parseUa(props.user.userAgent))
const displayName = computed(() => props.user.nickname || props.user.username)
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconUser class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">{{ displayName }}</div>
        <div class="truncate font-mono text-xs text-muted-foreground">
          @{{ user.username }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 概览 -->
    <dl class="space-y-2.5 text-[13px]">
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1.5 text-muted-foreground">
          <IconPlugConnectedX class="h-3.5 w-3.5" />
          客户来源
        </dt>
        <dd>
          <span
            v-if="user.clientSource"
            class="rounded bg-primary/10 px-1.5 py-0.5 font-mono text-[11px] text-primary"
          >
            {{ user.clientSource }}
          </span>
          <span v-else class="text-xs text-muted-foreground/50">—</span>
        </dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1.5 text-muted-foreground">
          <IconClock class="h-3.5 w-3.5" />
          登录时间
        </dt>
        <dd class="font-mono text-xs">{{ user.loginTime || '—' }}</dd>
      </div>
    </dl>

    <!-- 网络 -->
    <div class="my-4 h-px bg-border" />
    <section class="space-y-2">
      <h4 class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        <IconRoute class="h-3.5 w-3.5" />
        网络
      </h4>
      <div class="rounded-md bg-muted/60 px-3 py-2 font-mono text-xs">
        {{ user.loginIp || '—' }}
      </div>
    </section>

    <!-- 客户端 -->
    <div class="my-4 h-px bg-border" />
    <section class="space-y-2">
      <h4 class="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        <IconDeviceDesktop class="h-3.5 w-3.5" />
        客户端
      </h4>
      <div class="flex gap-2 text-xs">
        <span class="flex-1 rounded-md border border-border px-2.5 py-1.5">
          <span class="text-muted-foreground">浏览器 </span>{{ ua.browser }}
        </span>
        <span class="flex-1 rounded-md border border-border px-2.5 py-1.5">
          <span class="text-muted-foreground">系统 </span>{{ ua.os }}
        </span>
      </div>
      <div class="rounded-md bg-muted/60 px-3 py-2">
        <div class="mb-1 text-[10px] text-muted-foreground">User-Agent</div>
        <p class="break-all font-mono text-[11px] leading-relaxed text-muted-foreground">
          {{ user.userAgent || '—' }}
        </p>
      </div>
    </section>

    <!-- 强制下线 -->
    <div class="mt-auto pt-5">
      <Button
        v-access="PERM.ONLINE_KICK"
        variant="destructive"
        class="w-full gap-1.5"
        :disabled="self"
        :title="self ? '不能强制下线当前登录账号' : '强制下线'"
        @click="emit('kick', user)"
      >
        <IconPlugConnectedX class="h-4 w-4" />
        {{ self ? '当前登录账号' : '强制下线' }}
      </Button>
    </div>
  </div>
</template>
