<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  IconUpload,
  IconX,
  IconFile,
  IconCheck,
  IconAlertTriangle,
  IconReload,
  IconTrash,
} from '@tabler/icons-vue'
import { DialogRoot, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import Button from '@/components/ui/button/Button.vue'
import { uploadFileApi } from '@/api/file'
import type { SysFile } from '@/types/api'
import { formatSize } from './format'

/** 后端 multipart 单文件上限 20MB */
const MAX_SIZE = 20 * 1024 * 1024

type Status = 'waiting' | 'uploading' | 'done' | 'error'

interface Task {
  key: number
  file: File
  status: Status
  percent: number
  message: string
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  'update:open': [v: boolean]
  uploaded: [files: SysFile[]]
}>()

const tasks = ref<Task[]>([])
const succeeded = ref<SysFile[]>([])
let seq = 0
const inputSeed = ref(0)

const dragOver = ref(false)
const fileInput = ref<HTMLInputElement>()

const running = computed(() => tasks.value.some((t) => t.status === 'uploading'))
const doneCount = computed(() => tasks.value.filter((t) => t.status === 'done').length)
const allSettled = computed(
  () => tasks.value.length > 0 && tasks.value.every((t) => t.status === 'done' || t.status === 'error'),
)

/* 每次打开重置任务队列 */
watch(
  () => props.open,
  (v) => {
    if (v) {
      tasks.value = []
      succeeded.value = []
      dragOver.value = false
    }
  },
)

const addFiles = (files: FileList | File[] | null) => {
  if (!files) return
  for (const file of Array.from(files)) {
    tasks.value.push({
      key: ++seq,
      file,
      status: file.size > MAX_SIZE ? 'error' : 'waiting',
      percent: 0,
      message: file.size > MAX_SIZE ? `超过 ${formatSize(MAX_SIZE)} 上限` : '',
    })
  }
  inputSeed.value++
  pump()
}

const onPick = (e: Event) => {
  addFiles((e.target as HTMLInputElement).files)
}

const onDrop = (e: DragEvent) => {
  dragOver.value = false
  addFiles(e.dataTransfer?.files ?? null)
}

/* 队列串行上传（后端单字段单文件） */
const pump = async () => {
  if (running.value) return
  const task = tasks.value.find((t) => t.status === 'waiting')
  if (!task) {
    if (tasks.value.length && tasks.value.every((t) => t.status !== 'waiting')) {
      emit('uploaded', succeeded.value)
    }
    return
  }
  task.status = 'uploading'
  try {
    const res = await uploadFileApi(task.file, (p) => {
      task.percent = p
    })
    task.status = 'done'
    task.percent = 100
    if (res.data) succeeded.value.push(res.data)
  } catch (err) {
    task.status = 'error'
    task.message = err instanceof Error ? err.message : '上传失败'
  }
  pump()
}

const onRetry = (task: Task) => {
  task.status = 'waiting'
  task.percent = 0
  task.message = ''
  pump()
}

const onRemove = (task: Task) => {
  tasks.value = tasks.value.filter((t) => t.key !== task.key)
}

const onClose = () => {
  if (running.value) return
  emit('update:open', false)
}

const onEscapeKey = (e: Event) => {
  if (running.value) e.preventDefault()
}

const onPointerOutside = (e: Event) => {
  if (running.value) e.preventDefault()
}
</script>

<template>
  <DialogRoot :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent
      class="w-[520px] gap-0"
      :show-close-button="false"
      @escape-key-down="onEscapeKey"
      @pointer-down-outside="onPointerOutside"
    >
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <IconUpload class="h-4 w-4" />
          上传文件
        </DialogTitle>
      </DialogHeader>

      <!-- 拖拽区 -->
      <div
        class="mt-2 grid cursor-pointer place-items-center rounded-lg border border-dashed px-4 py-8 text-center transition-colors"
        :class="
          dragOver
            ? 'border-primary bg-primary/5'
            : 'border-border hover:border-primary/50 hover:bg-muted/40'
        "
        @click="fileInput?.click()"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="onDrop"
      >
        <IconUpload class="mb-2 h-6 w-6 text-muted-foreground" />
        <div class="text-[13px]">点击选择或拖拽文件到此处</div>
        <div class="mt-1 text-xs text-muted-foreground">
          支持多文件排队上传，单个文件不超过 {{ formatSize(MAX_SIZE) }}
        </div>
        <input
          ref="fileInput"
          type="file"
          multiple
          class="hidden"
          :key="inputSeed"
          @change="onPick"
        />
      </div>

      <!-- 任务列表 -->
      <ul v-if="tasks.length" class="mt-3 max-h-56 space-y-1.5 overflow-auto pr-1">
        <li
          v-for="t in tasks"
          :key="t.key"
          class="flex items-center gap-2 rounded-md border border-border px-2.5 py-2"
        >
          <IconFile class="h-4 w-4 shrink-0 text-muted-foreground" />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="truncate text-xs font-medium">{{ t.file.name }}</span>
              <span class="shrink-0 font-mono text-[10px] text-muted-foreground">
                {{ formatSize(t.file.size) }}
              </span>
            </div>
            <!-- 进度条 -->
            <div v-if="t.status === 'uploading' || t.status === 'done'" class="mt-1.5 h-1 w-full overflow-hidden rounded bg-muted">
              <div
                class="h-full rounded bg-primary transition-all"
                :class="t.status === 'done' ? 'bg-emerald-500' : ''"
                :style="{ width: `${t.percent}%` }"
              />
            </div>
            <div v-else-if="t.status === 'error'" class="mt-0.5 truncate text-[11px] text-destructive">
              {{ t.message }}
            </div>
          </div>

          <span v-if="t.status === 'done'" class="text-emerald-500"><IconCheck class="h-4 w-4" /></span>
          <span v-else-if="t.status === 'error'" class="text-destructive">
            <IconAlertTriangle class="h-4 w-4" />
          </span>
          <span v-else-if="t.status === 'uploading'" class="w-9 text-right font-mono text-[10px] text-muted-foreground">
            {{ t.percent }}%
          </span>

          <Button
            v-if="t.status === 'error'"
            variant="ghost"
            size="icon-sm"
            class="size-7"
            title="重试"
            @click="onRetry(t)"
          >
            <IconReload class="h-3.5 w-3.5" />
          </Button>
          <Button
            v-if="t.status !== 'uploading'"
            variant="ghost"
            size="icon-sm"
            class="size-7 text-muted-foreground"
            title="移除"
            @click="onRemove(t)"
          >
            <IconTrash class="h-3.5 w-3.5" />
          </Button>
        </li>
      </ul>

      <!-- 底部 -->
      <div class="mt-4 flex items-center justify-between">
        <span class="text-xs text-muted-foreground">
          <template v-if="running">上传中…</template>
          <template v-else-if="tasks.length">已成功 {{ doneCount }} / {{ tasks.length }}</template>
        </span>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" :disabled="running" @click="onClose">
            {{ allSettled ? '完成' : '取消' }}
          </Button>
        </div>
      </div>

      <button
        class="absolute right-4 top-4 rounded-sm opacity-70 transition-opacity hover:opacity-100 disabled:opacity-40"
        :disabled="running"
        @click="onClose"
      >
        <IconX class="h-4 w-4" />
      </button>
    </DialogContent>
  </DialogRoot>
</template>
