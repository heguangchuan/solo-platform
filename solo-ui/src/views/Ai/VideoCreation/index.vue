<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { VideoCamera, Refresh, Brush, Download, Plus, Delete, Loading, Picture } from '@element-plus/icons-vue'
import {
  deleteConversationApi,
  generateVideoApi,
  getAssetPageApi,
  getConversationMessagesApi,
  getConversationsApi,
  getModelConfigListApi,
  getTaskDetailApi,
  type AiConversation,
  type AiMessage,
  type VideoGenerationResponse,
} from '@/api/ai'
import { uploadFileApi } from '@/api/file'
import type { AiAsset, AiModelConfig } from '@/types/api'

interface ReferenceImage {
  url: string
  name: string
  source: 'UPLOAD' | 'ASSET' | 'URL'
}

const loading = ref(false)
const polling = ref(false)
let pollingTimer: number | null = null
const uploading = ref(false)
const modelLoading = ref(false)
const assetLoading = ref(false)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const modelOptions = ref<AiModelConfig[]>([])
const conversations = ref<AiConversation[]>([])
const messages = ref<AiMessage[]>([])
const currentConversationId = ref('')
const selectedModelConfigId = ref('')
const activeMode = ref<'text' | 'image' | 'multi' | 'keyframes'>('text')
const prompt = ref('')
const negativePrompt = ref('')
const urlInput = ref('')
const width = ref(1152)
const height = ref(768)
const numFrames = ref(121)
const frameRate = ref(24)
const seed = ref<number | undefined>()
const generatedVideo = ref<VideoGenerationResponse | null>(null)
const referenceImages = ref<ReferenceImage[]>([])
const assetDialogVisible = ref(false)
const assetList = ref<AiAsset[]>([])
const assetTotal = ref(0)
const assetPage = ref(1)
const assetPageSize = ref(12)
const assetKeyword = ref('')
const lastTaskId = ref('')
const lastModelName = ref('')

const frameOptions = [
  { label: '81 帧', value: 81 },
  { label: '121 帧', value: 121 },
  { label: '161 帧', value: 161 },
  { label: '201 帧', value: 201 },
  { label: '241 帧', value: 241 },
  { label: '321 帧', value: 321 },
  { label: '409 帧', value: 409 },
  { label: '441 帧（部分规格可能不支持）', value: 441 },
]

const ratioOptions = [
  { label: '横屏 16:9（1152 × 768）', width: 1152, height: 768 },
  { label: '竖屏 9:16（768 × 1152）', width: 768, height: 1152 },
  { label: '方形 1:1（1024 × 1024）', width: 1024, height: 1024 },
  { label: '横屏 4:3（1088 × 832）', width: 1088, height: 832 },
  { label: '竖屏 3:4（832 × 1088）', width: 832, height: 1088 },
]

const sourceTypeMap: Record<ReferenceImage['source'], string> = {
  UPLOAD: '上传',
  ASSET: '素材',
  URL: 'URL',
}

const currentModel = computed(() => modelOptions.value.find((item) => item.id === selectedModelConfigId.value))
const currentConversation = computed(() => conversations.value.find((item) => item.id === currentConversationId.value))
const referenceUrls = computed(() => referenceImages.value.map((item) => item.url))
const estimatedSeconds = computed(() => {
  if (!frameRate.value) return '0.00'
  return (numFrames.value / frameRate.value).toFixed(2)
})

function parseMessageVideos(raw?: string | null): VideoGenerationResponse[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item) => item && typeof item.videoUrl === 'string')
  } catch {
    return []
  }
}

async function loadModels() {
  modelLoading.value = true
  try {
    const res = await getModelConfigListApi()
    modelOptions.value = (res.data || []).filter((item) => item.modelType === 'VIDEO' && item.status === 1)
    if (!selectedModelConfigId.value && modelOptions.value.length > 0) selectedModelConfigId.value = modelOptions.value[0].id
  } catch {
    ElMessage.error('加载视频模型失败')
  } finally {
    modelLoading.value = false
  }
}

async function loadConversations() {
  loadingConversations.value = true
  try {
    const res = await getConversationsApi({ page: 1, pageSize: 100, conversationType: 'VIDEO' })
    conversations.value = res.data.rows || []
  } catch {
    ElMessage.error('加载会话列表失败')
  } finally {
    loadingConversations.value = false
  }
}

async function loadMessages(conversationId: string) {
  if (!conversationId) return
  loadingMessages.value = true
  try {
    const res = await getConversationMessagesApi(conversationId)
    messages.value = res.data || []
  } catch {
    ElMessage.error('加载会话消息失败')
  } finally {
    loadingMessages.value = false
  }
}

async function selectConversation(conversation: AiConversation) {
  currentConversationId.value = conversation.id
  generatedVideo.value = null
  lastTaskId.value = ''
  lastModelName.value = ''
  await loadMessages(conversation.id)
}

function createNewConversation() {
  currentConversationId.value = ''
  messages.value = []
  handleReset()
}

async function handleDeleteConversation(conversation: AiConversation, event: MouseEvent) {
  event.stopPropagation()
  try {
    await ElMessageBox.confirm(`确定要删除会话 "${conversation.title || '视频创作'}" 吗？`, '提示', { type: 'warning' })
    await deleteConversationApi(conversation.id)
    ElMessage.success('删除成功')
    if (currentConversationId.value === conversation.id) {
      currentConversationId.value = ''
      messages.value = []
    }
    await loadConversations()
  } catch {
    // cancelled or error
  }
}

function applyRatio(item: { width: number; height: number }) {
  width.value = item.width
  height.value = item.height
}

function addReferenceImage(image: ReferenceImage) {
  if (!image.url) return
  if (referenceImages.value.some((item) => item.url === image.url)) {
    ElMessage.warning('该参考图片已添加')
    return
  }
  referenceImages.value.push(image)
}

function removeReferenceImage(index: number) {
  referenceImages.value.splice(index, 1)
}

async function handleLocalFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const res = await uploadFileApi(raw)
    if (!res.data?.url) return
    addReferenceImage({ url: res.data.url, name: res.data.originalName || raw.name, source: 'UPLOAD' })
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function addUrlReference() {
  const url = urlInput.value.trim()
  if (!url) {
    ElMessage.warning('请输入图片 URL')
    return
  }
  addReferenceImage({ url, name: url.split('/').pop() || '在线图片', source: 'URL' })
  urlInput.value = ''
}

async function openAssetDialog() {
  assetDialogVisible.value = true
  assetPage.value = 1
  await loadAssets()
}

async function loadAssets() {
  assetLoading.value = true
  try {
    const res = await getAssetPageApi({ page: assetPage.value, pageSize: assetPageSize.value, assetType: 'IMAGE', fileName: assetKeyword.value || undefined, status: 1 })
    assetList.value = res.data.rows || []
    assetTotal.value = res.data.total || 0
  } catch {
    ElMessage.error('加载素材失败')
  } finally {
    assetLoading.value = false
  }
}

function addAssetReference(asset: AiAsset) {
  addReferenceImage({ url: asset.fileUrl, name: asset.fileName, source: 'ASSET' })
}

function handleAssetSearch() {
  assetPage.value = 1
  loadAssets()
}

function stopPolling() {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
  polling.value = false
}

function startPollingTask(taskId: string, conversationId: string) {
  stopPolling()
  polling.value = true
  pollingTimer = window.setInterval(async () => {
    try {
      const res = await getTaskDetailApi(taskId)
      const task = res.data
      if (task.status === 'SUCCESS') {
        stopPolling()
        const videos = parseMessageVideos(task.resultContent)
        generatedVideo.value = videos[0] || null
        await loadConversations()
        await loadMessages(conversationId)
        ElMessage.success('视频生成成功')
      } else if (task.status === 'FAILED' || task.status === 'CANCELLED') {
        stopPolling()
        await loadMessages(conversationId)
        ElMessage.error(task.errorMsg || '视频生成失败')
      }
    } catch {
      stopPolling()
      ElMessage.error('查询任务状态失败')
    }
  }, 5000)
}

async function handleGenerate() {
  if (!selectedModelConfigId.value) return ElMessage.warning('请选择视频模型')
  if (!prompt.value.trim()) return ElMessage.warning('请输入视频描述')
  if (activeMode.value !== 'text' && referenceUrls.value.length === 0) return ElMessage.warning('请至少添加一张参考图片')
  if ((activeMode.value === 'image') && referenceUrls.value.length > 1) return ElMessage.warning('图生视频只需要一张参考图，多图请切换到多图或关键帧')
  loading.value = true
  try {
    const mode = activeMode.value === 'keyframes' ? 'keyframes' : activeMode.value === 'image' ? 'ti2vid' : undefined
    const res = await generateVideoApi({
      conversationId: currentConversationId.value || undefined,
      modelConfigId: selectedModelConfigId.value,
      prompt: prompt.value.trim(),
      image: activeMode.value === 'text' ? undefined : referenceUrls.value,
      mode,
      width: activeMode.value === 'text' ? width.value : undefined,
      height: activeMode.value === 'text' ? height.value : undefined,
      numFrames: numFrames.value,
      frameRate: frameRate.value,
      seed: seed.value,
      negativePrompt: negativePrompt.value || undefined,
    })
    generatedVideo.value = null
    currentConversationId.value = res.data.conversationId
    lastTaskId.value = res.data.taskId
    lastModelName.value = res.data.modelName
    await loadConversations()
    await loadMessages(res.data.conversationId)
    startPollingTask(res.data.taskId, res.data.conversationId)
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handleReset() {
  prompt.value = ''
  negativePrompt.value = ''
  urlInput.value = ''
  width.value = 1152
  height.value = 768
  numFrames.value = 121
  frameRate.value = 24
  seed.value = undefined
  referenceImages.value = []
  generatedVideo.value = null
  lastTaskId.value = ''
  lastModelName.value = ''
}

function downloadVideo(item: VideoGenerationResponse) {
  if (!item.videoUrl) return
  const link = document.createElement('a')
  link.href = item.videoUrl
  link.download = item.fileName || 'ai-video.mp4'
  link.target = '_blank'
  link.click()
}

onMounted(async () => {
  await loadModels()
  await loadConversations()
  if (conversations.value.length > 0) await selectConversation(conversations.value[0])
})

onUnmounted(() => stopPolling())
</script>

<template>
  <div class="video-creation">
    <el-row :gutter="16" class="creation-layout">
      <el-col :span="5" class="layout-column">
        <el-card class="conversation-sidebar" shadow="never">
          <template #header>
            <div class="card-header">
              <span>会话列表</span>
              <el-button :icon="Plus" circle size="small" @click="createNewConversation" />
            </div>
          </template>
          <div class="conversation-list">
            <div v-if="loadingConversations" class="empty-state small"><el-icon class="is-loading"><Loading /></el-icon><p>加载中...</p></div>
            <template v-else>
              <div v-for="conversation in conversations" :key="conversation.id" class="conversation-item" :class="{ active: currentConversationId === conversation.id }" @click="selectConversation(conversation)">
                <div class="conversation-title">{{ conversation.title || '视频创作' }}</div>
                <div class="conversation-info">
                  <span>{{ conversation.messageCount }} 条消息</span>
                  <el-button :icon="Delete" circle size="small" type="danger" text @click="handleDeleteConversation(conversation, $event)" />
                </div>
              </div>
              <div v-if="!conversations.length" class="empty-state small"><p>暂无会话，点击右上角 + 开始新会话</p></div>
            </template>
          </div>
        </el-card>
      </el-col>

      <el-col :span="11" class="layout-column">
        <el-card shadow="hover" class="form-card">
          <template #header><div class="card-header"><span>{{ currentConversation?.title || '视频创作' }}</span><el-button :icon="Refresh" link @click="loadModels">刷新模型</el-button></div></template>
          <el-form label-width="90px">
            <el-tabs v-model="activeMode" class="mode-tabs" stretch>
              <el-tab-pane label="文生视频" name="text" />
              <el-tab-pane label="图生视频" name="image" />
              <el-tab-pane label="多图视频" name="multi" />
              <el-tab-pane label="关键帧" name="keyframes" />
            </el-tabs>
            <el-form-item label="视频模型"><el-select v-model="selectedModelConfigId" placeholder="请选择视频模型" :loading="modelLoading" style="width: 100%"><el-option v-for="model in modelOptions" :key="model.id" :label="model.modelName" :value="model.id" /></el-select></el-form-item>
            <el-form-item label="模型来源"><el-input :model-value="currentModel?.provider || '-'" disabled /></el-form-item>
            <el-form-item v-if="activeMode === 'text'" label="画面比例"><el-button v-for="item in ratioOptions" :key="item.label" @click="applyRatio(item)">{{ item.label }}</el-button></el-form-item>
            <el-form-item v-if="activeMode === 'text'" label="宽高"><el-input-number v-model="width" :min="256" /> <span class="separator">×</span> <el-input-number v-model="height" :min="256" /></el-form-item>
            <el-form-item label="帧数"><el-select v-model="numFrames" style="width: 100%"><el-option v-for="item in frameOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="帧率"><el-input-number v-model="frameRate" :min="1" :max="60" style="width: 100%" /></el-form-item>
            <el-form-item label="预计时长"><el-input :model-value="`${estimatedSeconds} 秒`" disabled /></el-form-item>
            <el-form-item label="随机种子"><el-input-number v-model="seed" :min="0" style="width: 100%" placeholder="可选" /></el-form-item>

            <template v-if="activeMode !== 'text'">
              <el-form-item label="参考图片">
                <div class="reference-panel">
                  <div class="reference-actions"><el-upload accept="image/*" :auto-upload="false" :show-file-list="false" :on-change="handleLocalFileChange"><el-button :loading="uploading" :icon="Plus">本地上传</el-button></el-upload><el-button :icon="Picture" @click="openAssetDialog">素材库选择</el-button></div>
                  <div class="url-row"><el-input v-model="urlInput" placeholder="输入在线图片 URL" @keyup.enter="addUrlReference" /><el-button type="primary" @click="addUrlReference">添加</el-button></div>
                  <div v-if="referenceImages.length" class="reference-list"><div v-for="(item, idx) in referenceImages" :key="item.url" class="reference-item"><el-image :src="item.url" fit="cover" class="reference-image" /><div class="reference-info"><el-tag size="small">{{ sourceTypeMap[item.source] }}</el-tag><span class="reference-name">{{ item.name }}</span></div><el-button :icon="Delete" circle text type="danger" @click="removeReferenceImage(idx)" /></div></div>
                  <div v-else class="reference-empty">可通过本地上传、素材库或在线 URL 添加参考图</div>
                </div>
              </el-form-item>
            </template>

            <el-form-item label="视频描述"><el-input v-model="prompt" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="描述主体、动作、场景、镜头运动、光照和风格" /></el-form-item>
            <el-form-item label="负向提示"><el-input v-model="negativePrompt" type="textarea" :rows="2" placeholder="可选，描述需要避免的内容" /></el-form-item>
            <el-form-item><el-button v-permission="'ai:videoCreation:generate'" type="primary" :icon="Brush" :loading="loading" :disabled="polling" @click="handleGenerate">开始生成</el-button><el-button :icon="Refresh" :disabled="loading || polling" @click="handleReset">重置</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="8" class="layout-column">
        <el-card shadow="hover" class="result-card">
          <template #header><div class="card-header"><span>生成结果</span><span v-if="lastTaskId" class="task-info">任务：{{ lastTaskId }} ｜ {{ lastModelName }}</span></div></template>
          <div v-if="loading || polling || loadingMessages" class="empty-state"><el-icon class="is-loading"><Loading /></el-icon><p>{{ loading ? '正在提交生成任务...' : polling ? '视频生成中，请稍候...' : '加载会话中...' }}</p></div>
          <div v-else-if="!messages.length && !generatedVideo" class="empty-state"><el-icon><VideoCamera /></el-icon><p>选择历史会话或输入描述后开始生成视频</p></div>
          <div v-else-if="messages.length" class="message-list">
            <div v-for="message in messages" :key="message.id" class="message-item" :class="message.role.toLowerCase()">
              <div class="message-meta"><span>{{ message.role === 'USER' ? '用户' : 'Agnes' }}</span><span v-if="message.modelName" class="message-model">{{ message.modelName }}</span><span>{{ message.createTime }}</span></div>
              <div v-if="message.content" class="message-text">{{ message.content }}</div>
              <div v-if="parseMessageVideos(message.attachments).length" class="video-list"><div v-for="item in parseMessageVideos(message.attachments)" :key="item.videoUrl" class="video-item"><video :src="item.videoUrl" controls class="generated-video" /><div class="video-actions"><span class="file-name">{{ item.fileName }}</span><el-button type="primary" link :icon="Download" @click="downloadVideo(item)">下载</el-button></div></div></div>
            </div>
          </div>
          <div v-else-if="generatedVideo" class="video-item"><video :src="generatedVideo.videoUrl" controls class="generated-video" /><div class="video-actions"><span class="file-name">{{ generatedVideo.fileName }}</span><el-button type="primary" link :icon="Download" @click="downloadVideo(generatedVideo)">下载</el-button></div></div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="assetDialogVisible" title="选择素材图片" width="820px" :close-on-click-modal="false">
      <div class="asset-toolbar"><el-input v-model="assetKeyword" placeholder="搜索文件名" clearable style="width: 240px" @keyup.enter="handleAssetSearch" /><el-button type="primary" @click="handleAssetSearch">查询</el-button></div>
      <div v-loading="assetLoading" class="asset-grid"><div v-for="asset in assetList" :key="asset.id" class="asset-item" @click="addAssetReference(asset)"><el-image :src="asset.fileUrl" fit="cover" class="asset-image" /><div class="asset-name">{{ asset.fileName }}</div></div><el-empty v-if="!assetLoading && !assetList.length" description="暂无图片素材" /></div>
      <template #footer><el-pagination v-model:current-page="assetPage" v-model:page-size="assetPageSize" :total="assetTotal" :page-sizes="[12, 24, 48]" layout="total, sizes, prev, pager, next" @current-change="loadAssets" @size-change="loadAssets" /></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.video-creation { height: calc(100vh - 140px); min-height: 0; overflow: hidden; }
.creation-layout, .layout-column { height: 100%; min-height: 0; overflow: hidden; }
.layout-column { display: flex; }
.conversation-sidebar, .form-card, .result-card { flex: 1; height: 100%; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }
.conversation-sidebar :deep(.el-card__body), .form-card :deep(.el-card__body), .result-card :deep(.el-card__body) { flex: 1; min-height: 0; overflow-y: auto; }
.card-header, .conversation-info, .reference-actions, .url-row, .asset-toolbar, .message-meta, .video-actions { display: flex; align-items: center; gap: 8px; }
.card-header, .conversation-info, .video-actions { justify-content: space-between; }
.conversation-list, .reference-list, .message-list, .video-list { display: flex; flex-direction: column; gap: 10px; }
.conversation-item { padding: 10px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; background: var(--el-fill-color-light); }
.conversation-item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.conversation-title, .reference-name, .asset-name, .file-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-title { font-weight: 500; }
.conversation-info, .task-info, .message-model, .message-meta { font-size: 12px; color: var(--el-text-color-secondary); }
.mode-tabs, .reference-panel { width: 100%; }
.mode-tabs { margin-bottom: 14px; }
.separator { margin: 0 8px; }
.reference-item { display: flex; align-items: center; gap: 8px; padding: 6px; border: 1px solid var(--el-border-color-light); border-radius: 6px; }
.reference-image { width: 54px; height: 54px; border-radius: 4px; }
.reference-info { flex: 1; min-width: 0; display: flex; align-items: center; gap: 8px; }
.reference-empty { padding: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); border-radius: 6px; }
.empty-state { min-height: 320px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--el-text-color-secondary); }
.empty-state.small { min-height: 160px; }
.empty-state .el-icon { font-size: 48px; margin-bottom: 12px; }
.message-item { padding: 10px; border-radius: 8px; background: var(--el-fill-color-light); }
.message-item.assistant { background: var(--el-color-primary-light-9); }
.message-text { white-space: pre-wrap; line-height: 1.5; margin-bottom: 6px; font-size: 13px; }
.generated-video { width: 100%; max-height: 260px; border-radius: 8px; background: #000; }
.video-item, .asset-item { border: 1px solid var(--el-border-color-light); border-radius: 8px; overflow: hidden; background: var(--el-bg-color); }
.video-actions { padding: 6px 8px; }
.asset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; min-height: 360px; }
.asset-item { cursor: pointer; }
.asset-item:hover { border-color: var(--el-color-primary); }
.asset-image { width: 100%; height: 160px; display: block; }
.asset-name { padding: 8px; font-size: 12px; }
.file-name { flex: 1; font-size: 11px; color: var(--el-text-color-regular); }
</style>
