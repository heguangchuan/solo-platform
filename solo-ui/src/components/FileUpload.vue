<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ButtonProps, UploadFile } from 'element-plus'
import type { SysFile } from '@/types/api'
import { uploadFileApi } from '@/api/file'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    file?: SysFile | null
    buttonText?: string
    tip?: string
    accept?: string
    disabled?: boolean
    showFileName?: boolean
    plain?: boolean
    buttonType?: ButtonProps['type']
    successMessage?: string
  }>(),
  {
    modelValue: '',
    file: null,
    buttonText: '上传文件',
    tip: '选择文件后会自动上传',
    accept: '',
    disabled: false,
    showFileName: true,
    plain: false,
    buttonType: 'primary',
    successMessage: '上传成功',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:file': [value: SysFile | null]
  success: [value: SysFile]
  remove: []
}>()

const uploading = ref(false)
const selectedFile = ref<File | null>(null)

const displayName = computed(() => props.file?.originalName || selectedFile.value?.name || props.modelValue)
const displaySize = computed(() => props.file?.size ?? selectedFile.value?.size)
const tipText = computed(() => {
  if (!selectedFile.value) return props.tip
  return displaySize.value == null
    ? `已选择：${selectedFile.value.name}`
    : `已选择：${selectedFile.value.name}（${formatSize(displaySize.value)}）`
})

async function handleChange(file: UploadFile) {
  if (!file.raw) return
  selectedFile.value = file.raw
  uploading.value = true
  try {
    const res = await uploadFileApi(file.raw)
    emit('update:modelValue', res.data.url)
    emit('update:file', res.data)
    emit('success', res.data)
    if (props.successMessage) ElMessage.success(props.successMessage)
  } finally {
    uploading.value = false
  }
}

function handleRemove() {
  selectedFile.value = null
  emit('update:modelValue', '')
  emit('update:file', null)
  emit('remove')
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<template>
  <div class="file-upload">
    <el-upload
      :accept="accept"
      :auto-upload="false"
      :disabled="disabled || uploading"
      :limit="1"
      :on-change="handleChange"
      :show-file-list="false"
    >
      <el-button :type="buttonType" :plain="plain" :disabled="disabled" :loading="uploading">
        <el-icon class="btn-icon"><Upload /></el-icon>
        {{ buttonText }}
      </el-button>
      <template #tip>
        <div v-if="tipText" class="upload-tip">{{ tipText }}</div>
      </template>
    </el-upload>

    <div v-if="showFileName && displayName" class="uploaded-file">
      <el-link :href="modelValue" target="_blank" type="primary" :underline="false">
        {{ displayName }}
      </el-link>
      <el-button link type="primary" :disabled="disabled || uploading" @click="handleRemove">移除</el-button>
    </div>
  </div>
</template>

<style scoped>
.file-upload {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.upload-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.uploaded-file {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
