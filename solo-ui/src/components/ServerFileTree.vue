<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, Download, FolderOpened, Fold, Refresh, Upload, Expand } from '@element-plus/icons-vue'
import type { ServerFileItem } from '@/types/api'
import { getServerFileListApi, uploadServerFileApi, downloadServerFileApi, deleteServerFileApi } from '@/api/server'
import { downloadBlob } from '@/utils/download'

const props = withDefaults(defineProps<{
  serverId: string
  collapsed?: boolean
}>(), {
  collapsed: false,
})

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  error: [message: string]
  uploadSuccess: [path: string]
  downloadSuccess: [path: string]
}>()

const loading = ref(false)
const currentPath = ref('.')
const files = ref<ServerFileItem[]>([])
const uploadRef = ref()

const displayPath = computed(() => currentPath.value === '.' ? '~' : currentPath.value)

function toggleCollapsed() {
  emit('update:collapsed', !props.collapsed)
}

async function fetchFiles(path = currentPath.value) {
  if (!props.serverId) return
  loading.value = true
  try {
    const res = await getServerFileListApi({ serverId: props.serverId, path })
    currentPath.value = path || '.'
    files.value = res.data
  } catch (e: any) {
    const message = e?.message || '读取服务器目录失败'
    emit('error', message)
  } finally {
    loading.value = false
  }
}

function enterDirectory(item: ServerFileItem) {
  if (!item.directory) return
  fetchFiles(item.path)
}

function goParent() {
  if (currentPath.value === '.' || currentPath.value === '/' || !currentPath.value) return
  const normalized = currentPath.value.replace(/\/$/, '')
  const index = normalized.lastIndexOf('/')
  if (index <= 0) {
    fetchFiles('.')
    return
  }
  fetchFiles(normalized.slice(0, index))
}

function formatSize(size?: number) {
  if (!size) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

async function handleUpload(options: any) {
  try {
    await uploadServerFileApi(props.serverId, currentPath.value, options.file)
    ElMessage.success('上传成功')
    emit('uploadSuccess', currentPath.value)
    fetchFiles()
  } catch {
    // handled
  }
}

async function handleDownload(item: ServerFileItem) {
  if (item.directory) return
  try {
    const res = await downloadServerFileApi(props.serverId, item.path)
    downloadBlob(res.data, item.name)
    emit('downloadSuccess', item.path)
  } catch {
    // handled
  }
}

async function handleDelete(item: ServerFileItem) {
  try {
    await ElMessageBox.confirm(`确定要删除${item.directory ? '目录' : '文件'} "${item.name}" 吗？`, '提示', { type: 'warning' })
    await deleteServerFileApi(props.serverId, item.path)
    ElMessage.success('删除成功')
    fetchFiles()
  } catch {
    // cancelled or error
  }
}

onMounted(() => {
  fetchFiles('.')
})
</script>

<template>
  <div :class="['server-file-tree', { collapsed }]">
    <div v-if="collapsed" class="collapsed-bar" @click="toggleCollapsed">
      <el-icon><Expand /></el-icon>
    </div>
    <template v-else>
      <div class="file-toolbar">
        <div class="path-row">
          <div class="path" :title="displayPath">{{ displayPath }}</div>
          <div class="tools">
            <el-button circle size="small" :icon="ArrowLeft" title="返回上级" @click="goParent" />
            <el-button circle size="small" :icon="Refresh" title="刷新" @click="fetchFiles()" />
            <el-upload ref="uploadRef" action="#" :show-file-list="false" :http-request="handleUpload">
              <el-button circle size="small" :icon="Upload" title="上传文件" />
            </el-upload>
            <el-button circle size="small" :icon="Fold" title="折叠" @click="toggleCollapsed" />
          </div>
        </div>
      </div>

      <div v-loading="loading" class="file-list">
        <div
          v-for="item in files"
          :key="item.path"
          :class="['file-item', { directory: item.directory }]"
          @dblclick="enterDirectory(item)"
        >
          <div class="file-main" @click="item.directory && enterDirectory(item)">
            <el-icon :class="['file-icon', item.directory ? 'folder-icon' : 'document-icon']">
              <FolderOpened v-if="item.directory" />
              <Document v-else />
            </el-icon>
            <span class="file-name" :title="item.name">{{ item.name }}</span>
          </div>
          <div class="file-extra">
            <span class="file-size">{{ formatSize(item.size) }}</span>
            <el-button
              v-if="!item.directory"
              circle
              text
              size="small"
              :icon="Download"
              title="下载"
              @click.stop="handleDownload(item)"
            />
            <el-button
              circle
              text
              size="small"
              type="danger"
              :icon="Delete"
              title="删除"
              @click.stop="handleDelete(item)"
            />
          </div>
        </div>
        <el-empty v-if="!loading && !files.length" description="目录为空" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.server-file-tree {
  width: 280px;
  height: 100%;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  transition: width 0.2s ease;
  overflow: hidden;
  flex-shrink: 0;
}

.server-file-tree.collapsed {
  width: 36px;
}

.collapsed-bar {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--el-text-color-secondary);
}

.file-toolbar {
  height: 42px;
  padding: 6px 8px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-light);
}

.path-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.path {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.file-list {
  height: calc(100% - 42px);
  overflow-y: auto;
  padding: 6px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  height: 30px;
  padding: 0 6px;
  border-radius: 6px;
  cursor: default;
}

.file-item:hover {
  background: var(--el-fill-color-light);
}

.file-item.directory {
  cursor: pointer;
}

.file-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.file-icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}

.folder-icon {
  color: #d97706;
  background: #fef3c7;
}

.document-icon {
  color: #2563eb;
  background: #dbeafe;
}

.file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.file-extra {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.file-size {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
