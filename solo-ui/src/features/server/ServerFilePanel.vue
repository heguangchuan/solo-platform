<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  IconArrowUp,
  IconRefresh,
  IconUpload,
  IconChevronLeft,
  IconFolder,
  IconFile as IconFileTabler,
  IconDownload,
  IconTrash,
  IconLoader2,
} from '@tabler/icons-vue'
import type { ServerFileItem } from '@/types/api'
import {
  getServerFileListApi,
  uploadServerFileApi,
  downloadServerFileApi,
  deleteServerFileApi,
} from '@/api/server'
import { formatSize } from '@/features/file/format'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'

const props = defineProps<{ serverId: string }>()
const emit = defineEmits<{ collapse: [] }>()

const path = ref('.')
const items = ref<ServerFileItem[]>([])
const loading = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const displayPath = () => (path.value === '.' ? '~' : path.value)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getServerFileListApi({ serverId: props.serverId, path: path.value })
    items.value = res.data || []
  } finally {
    loading.value = false
  }
}

const canBack = () => path.value !== '.' && path.value !== '/'

const onBack = () => {
  if (!canBack()) return
  const idx = path.value.lastIndexOf('/')
  if (idx <= 0) path.value = '.'
  else path.value = path.value.substring(0, idx)
  loadList()
}

const onOpen = (item: ServerFileItem) => {
  if (!item.directory) return
  path.value = item.path
  loadList()
}

const onPickFile = () => {
  fileInputRef.value?.click()
}

const onFileChange = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    await uploadServerFileApi(props.serverId, path.value, file)
    toast.success('上传成功')
    await loadList()
  } finally {
    uploading.value = false
  }
}

const onDownload = async (item: ServerFileItem) => {
  const res = await downloadServerFileApi(props.serverId, item.path)
  const blob = res.data as Blob
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = item.name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

const onDelete = async (item: ServerFileItem) => {
  const ok = await confirm({
    title: '删除远程文件',
    description: `确认删除远程${item.directory ? '目录（仅空目录可删）' : '文件'}「${item.name}」？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteServerFileApi(props.serverId, item.path)
  toast.success('已删除')
  await loadList()
}

onMounted(loadList)

defineExpose({ reload: loadList })
</script>

<template>
  <div class="flex h-full flex-col bg-card">
    <!-- 工具条 -->
    <div class="flex h-11 shrink-0 items-center gap-1 border-b border-border px-2">
      <Button
        variant="ghost"
        size="icon-sm"
        :disabled="!canBack()"
        title="返回上级"
        @click="onBack"
      >
        <IconArrowUp class="h-4 w-4" />
      </Button>
      <Button variant="ghost" size="icon-sm" title="刷新" @click="loadList">
        <IconRefresh class="h-4 w-4" />
      </Button>
      <Button
        v-access="PERM.SERVER_FILE_UPLOAD"
        variant="ghost"
        size="icon-sm"
        title="上传"
        :disabled="uploading"
        @click="onPickFile"
      >
        <IconLoader2 v-if="uploading" class="h-4 w-4 animate-spin" />
        <IconUpload v-else class="h-4 w-4" />
      </Button>
      <input
        ref="fileInputRef"
        type="file"
        class="hidden"
        @change="onFileChange"
      />
      <div class="flex-1" />
      <Button variant="ghost" size="icon-sm" title="折叠文件面板" @click="emit('collapse')">
        <IconChevronLeft class="h-4 w-4" />
      </Button>
    </div>

    <!-- 当前路径 -->
    <div class="shrink-0 border-b border-border px-3 py-1.5" :title="displayPath()">
      <span class="block truncate font-mono text-[11px] text-muted-foreground">
        {{ displayPath() }}
      </span>
    </div>

    <!-- 文件列表 -->
    <div class="min-h-0 flex-1 overflow-auto">
      <div v-if="loading" class="grid place-items-center py-10">
        <IconLoader2 class="h-4 w-4 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="!items.length" class="py-10 text-center text-xs text-muted-foreground">
        目录为空
      </div>
      <ul v-else class="py-1">
        <li
          v-for="item in items"
          :key="item.path"
          class="group flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-muted/50"
          :class="item.directory ? 'cursor-pointer' : ''"
          :title="item.name"
          @click="onOpen(item)"
        >
          <IconFolder v-if="item.directory" class="h-4 w-4 shrink-0 text-primary/80" />
          <IconFileTabler v-else class="h-4 w-4 shrink-0 text-muted-foreground" />
          <span class="min-w-0 flex-1 truncate">{{ item.name }}</span>
          <span class="shrink-0 font-mono text-[10px] text-muted-foreground/70">
            {{ item.directory ? '-' : formatSize(item.size ?? 0) }}
          </span>
          <Button
            v-if="!item.directory"
            v-access="PERM.SERVER_FILE_DOWNLOAD"
            variant="ghost"
            size="icon-sm"
            class="size-6 opacity-0 group-hover:opacity-100"
            title="下载"
            @click.stop="onDownload(item)"
          >
            <IconDownload class="h-3.5 w-3.5" />
          </Button>
          <Button
            v-access="PERM.SERVER_FILE_DELETE"
            variant="ghost"
            size="icon-sm"
            class="size-6 text-muted-foreground opacity-0 hover:text-destructive group-hover:opacity-100"
            title="删除"
            @click.stop="onDelete(item)"
          >
            <IconTrash class="h-3.5 w-3.5" />
          </Button>
        </li>
      </ul>
    </div>
  </div>
</template>
