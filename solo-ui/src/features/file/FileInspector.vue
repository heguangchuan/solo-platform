<script setup lang="ts">
import { computed } from 'vue'
import {
  IconX,
  IconLink,
  IconExternalLink,
  IconTrash,
  IconClock,
  IconUser,
  IconFile,
  IconFileDescription,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import type { SysFile } from '@/types/api'
import { PERM } from '@/features/access/permissions'
import { fileKind, formatSize } from './format'

const props = defineProps<{ file: SysFile }>()
const emit = defineEmits<{
  close: []
  deleted: [file: SysFile]
}>()

const kind = computed(() => fileKind(props.file.ext, props.file.mimeType))
const isImage = computed(() => kind.value === 'image')
const isVideo = computed(() => kind.value === 'video')
const isAudio = computed(() => kind.value === 'audio')

const onCopyLink = async () => {
  try {
    await navigator.clipboard.writeText(props.file.url)
  } catch {
    // 剪贴板权限被拒时静默失败（不阻断查看流程）
  }
}

const onOpenExternal = () => {
  window.open(props.file.url, '_blank')
}
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconFile class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold" :title="file.originalName">
          {{ file.originalName }}
        </div>
        <div class="truncate font-mono text-xs text-muted-foreground">
          .{{ file.ext || '—' }} · {{ formatSize(file.size) }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <!-- 预览 -->
    <div class="my-4">
      <div v-if="isImage" class="overflow-hidden rounded-lg border border-border bg-muted/40">
        <img :src="file.url" :alt="file.originalName" class="mx-auto max-h-56 w-full object-contain" />
      </div>
      <video
        v-else-if="isVideo"
        :src="file.url"
        controls
        class="max-h-56 w-full rounded-lg border border-border bg-black"
      />
      <audio v-else-if="isAudio" :src="file.url" controls class="mt-1 w-full" />
    </div>

    <!-- 概览 -->
    <dl class="space-y-2.5 text-[13px]">
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1.5 text-muted-foreground">
          <IconUser class="h-3.5 w-3.5" />
          上传账号
        </dt>
        <dd class="font-mono text-xs">{{ file.uploaderName || '—' }}</dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1.5 text-muted-foreground">
          <IconClock class="h-3.5 w-3.5" />
          上传时间
        </dt>
        <dd class="font-mono text-xs">{{ file.createTime || '—' }}</dd>
      </div>
      <div class="flex items-center justify-between">
        <dt class="flex items-center gap-1.5 text-muted-foreground">
          <IconFileDescription class="h-3.5 w-3.5" />
          类型
        </dt>
        <dd class="font-mono text-xs">{{ file.mimeType || '—' }}</dd>
      </div>
    </dl>

    <!-- 存储信息 -->
    <div class="my-4 h-px bg-border" />
    <section class="space-y-2">
      <h4 class="text-xs font-medium text-muted-foreground">存储</h4>
      <div class="rounded-md bg-muted/60 px-3 py-2">
        <div class="mb-1 text-[10px] text-muted-foreground">对象 Key</div>
        <p class="break-all font-mono text-[11px] text-muted-foreground">
          {{ file.storageName || '—' }}
        </p>
      </div>
      <div class="rounded-md bg-muted/60 px-3 py-2">
        <div class="mb-1 text-[10px] text-muted-foreground">访问 URL</div>
        <p class="break-all font-mono text-[11px] text-muted-foreground">
          {{ file.url || '—' }}
        </p>
      </div>
    </section>

    <!-- 底部操作 -->
    <div class="mt-auto space-y-2 pt-5">
      <div class="flex gap-2">
        <Button variant="outline" size="sm" class="flex-1 gap-1.5" @click="onCopyLink">
          <IconLink class="h-4 w-4" />
          复制链接
        </Button>
        <Button variant="outline" size="sm" class="flex-1 gap-1.5" @click="onOpenExternal">
          <IconExternalLink class="h-4 w-4" />
          新窗口打开
        </Button>
      </div>
      <Button
        v-access="PERM.FILE_DELETE"
        variant="destructive"
        class="w-full gap-1.5"
        @click="emit('deleted', file)"
      >
        <IconTrash class="h-4 w-4" />
        删除文件
      </Button>
    </div>
  </div>
</template>
