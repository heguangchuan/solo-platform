<template>
  <div class="space-y-4">
    <div>
      <h4 class="text-sm font-medium">通用文件上传</h4>
      <p class="mt-0.5 text-xs text-muted-foreground">
        复用文件管理的上传接口（POST /api/file/upload），测试上传、回显与移除。
      </p>
    </div>

    <div class="flex items-center gap-3">
      <input ref="fileInputRef" type="file" class="hidden" @change="onFileChange" />
      <Button variant="outline" size="sm" :loading="uploading" @click="fileInputRef?.click()">
        <Upload class="size-3.5" />
        {{ uploaded ? '重新上传' : '选择文件上传' }}
      </Button>

      <template v-if="uploading">
        <div class="h-1.5 w-40 overflow-hidden rounded-full bg-muted">
          <div class="h-full rounded-full bg-primary transition-all" :style="{ width: progress + '%' }" />
        </div>
        <span class="text-xs text-muted-foreground">{{ progress }}%</span>
      </template>

      <Button
        v-if="uploaded && !uploading"
        variant="ghost"
        size="sm"
        class="text-muted-foreground"
        @click="onRemove"
      >
        <IconX class="size-3.5" />
        移除
      </Button>
    </div>

    <!-- 上传结果 -->
    <div
      v-if="uploaded"
      class="rounded-lg border border-border bg-muted/30 p-4 text-[13px]"
    >
      <dl class="grid grid-cols-[88px_1fr] gap-x-4 gap-y-2">
        <dt class="text-muted-foreground">文件名</dt>
        <dd class="truncate font-medium">{{ uploaded.originalName }}</dd>
        <dt class="text-muted-foreground">文件类型</dt>
        <dd class="font-mono text-xs">{{ uploaded.ext || '—' }}</dd>
        <dt class="text-muted-foreground">文件大小</dt>
        <dd class="font-mono text-xs">{{ formatSize(uploaded.size) }}</dd>
        <dt class="text-muted-foreground">访问地址</dt>
        <dd class="truncate">
          <a :href="uploaded.url" target="_blank" class="font-mono text-xs text-primary hover:underline">
            {{ uploaded.url }}
          </a>
        </dd>
      </dl>
    </div>

    <div v-else-if="!uploading" class="rounded-lg border border-dashed border-border py-10 text-center text-xs text-muted-foreground">
      请上传一个文件
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Upload } from 'lucide-vue-next'
import { IconX } from '@tabler/icons-vue'
import type { SysFile } from '@/types/api'
import { uploadFileApi } from '@/api/file'
import { toast } from '@/lib/toast'
import Button from '@/components/ui/button/Button.vue'

const fileInputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const progress = ref(0)
const uploaded = ref<SysFile | null>(null)

const onFileChange = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  uploading.value = true
  progress.value = 0
  try {
    const res = await uploadFileApi(file, (p) => (progress.value = p))
    uploaded.value = res.data
    toast.success('上传成功')
  } finally {
    uploading.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

const onRemove = () => {
  uploaded.value = null
  progress.value = 0
}

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>
