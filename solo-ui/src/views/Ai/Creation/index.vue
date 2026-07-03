<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'

defineOptions({ name: 'AiCreation' })
import {
  getConversationsApi,
  getConversationMessagesApi,
  createConversationApi,
  deleteConversationApi,
  getTaskDetailApi,
  getAssetPageApi,
  type AiConversation,
  type AiMessage,
} from '@/api/ai'
import { uploadFileApi } from '@/api/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Loading, Position, Picture, Close as CloseIcon } from '@element-plus/icons-vue'
import type { AiAsset } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { renderMarkdown } from '@/utils/markdown'

const userStore = useUserStore()

const conversations = ref<AiConversation[]>([])
const currentConversationId = ref<string | null>(null)
const messages = ref<AiMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const polling = ref(false)
const loadingMessages = ref(false)
const loadingConversations = ref(false)
let pollingTimer: number | null = null

const MAX_PENDING_IMAGES = 4
interface PendingImage {
  url: string
  fileName?: string
}
const pendingImages = ref<PendingImage[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadingImage = ref(false)

// 素材库选择弹窗
const assetDialogVisible = ref(false)
const assetLoading = ref(false)
const assetList = ref<AiAsset[]>([])
const assetPage = ref(1)
const assetPageSize = ref(12)
const assetTotal = ref(0)

const currentConversation = computed(() =>
  conversations.value.find((c) => c.id === currentConversationId.value)
)

interface ParsedAsset {
  type?: string
  url?: string
  videoUrl?: string
  fileName?: string
}

const parseAssets = (raw?: string | null): ParsedAsset[] => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
  } catch {
    return []
  }
}

const messageImages = (msg: AiMessage): string[] =>
  parseAssets(msg.attachments)
    .map((a) => a.url)
    .filter((u): u is string => typeof u === 'string' && u.length > 0)

const messageVideos = (msg: AiMessage): ParsedAsset[] =>
  parseAssets(msg.attachments).filter((a) => typeof a.videoUrl === 'string' && a.videoUrl.length > 0)

const loadConversations = async () => {
  loadingConversations.value = true
  try {
    const res = await getConversationsApi({ page: 1, pageSize: 100, conversationType: 'MIXED' })
    conversations.value = res.data.rows || []
  } catch {
    ElMessage.error('加载会话列表失败')
  } finally {
    loadingConversations.value = false
  }
}

const loadMessages = async (conversationId: string) => {
  if (!conversationId) return
  loadingMessages.value = true
  try {
    const res = await getConversationMessagesApi(conversationId)
    messages.value = res.data || []
    await nextTick()
    scrollToBottom()
  } catch {
    ElMessage.error('加载消息失败')
  } finally {
    loadingMessages.value = false
  }
}

const selectConversation = async (conversation: AiConversation) => {
  if (sending.value || polling.value) return
  currentConversationId.value = conversation.id
  await loadMessages(conversation.id)
}

const createNewConversation = async () => {
  try {
    const res = await createConversationApi({ title: '新创作', conversationType: 'MIXED' })
    conversations.value.unshift(res.data)
    currentConversationId.value = res.data.id
    messages.value = []
    ElMessage.success('创建会话成功')
  } catch {
    ElMessage.error('创建会话失败')
  }
}

const handleDeleteConversation = async (conversation: AiConversation, event: Event) => {
  event.stopPropagation()
  try {
    await ElMessageBox.confirm('确定删除该会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteConversationApi(conversation.id)
    ElMessage.success('删除成功')
    if (currentConversationId.value === conversation.id) {
      currentConversationId.value = null
      messages.value = []
    }
    conversations.value = conversations.value.filter((c) => c.id !== conversation.id)
  } catch (err: any) {
    if (err !== 'cancel') ElMessage.error('删除失败')
  }
}

const stopPolling = () => {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
  polling.value = false
}

/** 生成类任务轮询：完成后刷新会话消息，把结果消息追加渲染 */
const pollTask = (taskId: string) => {
  stopPolling()
  polling.value = true
  pollingTimer = window.setInterval(async () => {
    try {
      const res = await getTaskDetailApi(taskId)
      const task = res.data
      if (task.status === 'SUCCESS') {
        stopPolling()
        if (currentConversationId.value) await loadMessages(currentConversationId.value)
        await loadConversations()
      } else if (task.status === 'FAILED' || task.status === 'CANCELLED') {
        stopPolling()
        ElMessage.error(task.errorMsg || '生成失败')
        if (currentConversationId.value) await loadMessages(currentConversationId.value)
      }
    } catch {
      stopPolling()
      ElMessage.error('查询任务状态失败')
    }
  }, 2500)
}

const triggerFilePicker = () => {
  if (uploadingImage.value || sending.value || polling.value) return
  if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
    ElMessage.warning(`最多添加 ${MAX_PENDING_IMAGES} 张图片`)
    return
  }
  fileInputRef.value?.click()
}

const onFileSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
    if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
      ElMessage.warning(`最多添加 ${MAX_PENDING_IMAGES} 张图片`)
      break
    }
    uploadingImage.value = true
    try {
      const res = await uploadFileApi(file)
      const url = res.data?.url
      if (url) pendingImages.value.push({ url, fileName: file.name || 'image' })
      else ElMessage.error('上传失败')
    } catch {
      ElMessage.error('上传失败')
    } finally {
      uploadingImage.value = false
    }
  }
}

const removePendingImage = (index: number) => {
  pendingImages.value.splice(index, 1)
}

const openAssetDialog = () => {
  if (sending.value || polling.value) return
  assetDialogVisible.value = true
  assetPage.value = 1
  loadAssets()
}

const loadAssets = async () => {
  assetLoading.value = true
  try {
    const res = await getAssetPageApi({
      page: assetPage.value,
      pageSize: assetPageSize.value,
      assetType: 'IMAGE'
    })
    assetList.value = res.data.rows || []
    assetTotal.value = res.data.total || 0
  } catch {
    ElMessage.error('加载素材失败')
  } finally {
    assetLoading.value = false
  }
}

const pickAsset = (asset: AiAsset) => {
  if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
    ElMessage.warning(`最多添加 ${MAX_PENDING_IMAGES} 张图片`)
    return
  }
  if (pendingImages.value.some((img) => img.url === asset.fileUrl)) {
    ElMessage.info('该素材已添加')
    return
  }
  pendingImages.value.push({ url: asset.fileUrl, fileName: asset.fileName })
  ElMessage.success('已添加')
}

const handleAssetPageChange = (page: number) => {
  assetPage.value = page
  loadAssets()
}

const sendMessage = async () => {
  const content = inputText.value.trim()
  if (!content || sending.value || polling.value) return
  sending.value = true

  const imagesToSend = pendingImages.value.slice()

  const userMessage: AiMessage = {
    id: 'temp-' + Date.now(),
    conversationId: currentConversationId.value || '',
    role: 'USER',
    content,
    messageType: 'TEXT',
    modelName: '',
    tokens: 0,
    attachments: imagesToSend.length > 0
      ? JSON.stringify(imagesToSend.map((img) => ({ type: 'IMAGE', url: img.url, fileName: img.fileName })))
      : undefined,
    createTime: new Date().toISOString()
  }
  messages.value.push(userMessage)
  inputText.value = ''
  pendingImages.value = []

  const assistantMessage: AiMessage = {
    id: 'assistant-streaming-' + Date.now(),
    conversationId: currentConversationId.value || '',
    role: 'ASSISTANT',
    content: '',
    messageType: 'TEXT',
    modelName: '',
    tokens: 0,
    createTime: new Date().toISOString()
  }
  messages.value.push(assistantMessage)

  await nextTick()
  scrollToBottom()

  let streamedConversationId: string | null = currentConversationId.value
  let receivedAny = false
  let errorMessage = ''
  let createdTaskId = ''

  try {
    const response = await fetch('/api/ai/creation/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        Accept: 'text/event-stream',
        Authorization: userStore.token ? `Bearer ${userStore.token}` : '',
        'X-Client-Source': 'web'
      },
      body: JSON.stringify({
        conversationId: currentConversationId.value || undefined,
        content,
        images: imagesToSend.length > 0
          ? imagesToSend.map((img) => ({ url: img.url, fileName: img.fileName }))
          : undefined
      })
    })

    if (!response.ok || !response.body) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let currentEvent = 'message'

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let idx: number
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)

        const dataLines: string[] = []
        currentEvent = 'message'
        for (const line of rawEvent.split('\n')) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).replace(/^ /, ''))
          }
        }
        const data = dataLines.join('\n')
        if (!data) continue

        if (currentEvent === 'chunk') {
          assistantMessage.content += data
          receivedAny = true
          await nextTick()
          scrollToBottom()
        } else if (currentEvent === 'task-created') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.taskId) createdTaskId = String(parsed.taskId)
            if (parsed.conversationId) streamedConversationId = String(parsed.conversationId)
          } catch {
            // ignore
          }
        } else if (currentEvent === 'done') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.conversationId) streamedConversationId = String(parsed.conversationId)
            if (typeof parsed.reply === 'string' && parsed.reply.length > 0) {
              assistantMessage.content = parsed.reply
            }
          } catch {
            // ignore
          }
        } else if (currentEvent === 'error') {
          errorMessage = data
        }
      }
    }
  } catch (e: any) {
    errorMessage = e?.message || '发送失败'
  }

  if (errorMessage && !receivedAny) {
    messages.value = messages.value.filter((m) => m.id !== assistantMessage.id)
    ElMessage.error(errorMessage || '发送失败')
  } else if (errorMessage) {
    ElMessage.warning('中断：' + errorMessage)
  }

  sending.value = false

  // 会话切换/刷新
  const isNewConversation = !currentConversationId.value && streamedConversationId
  if (streamedConversationId) {
    currentConversationId.value = streamedConversationId
  }

  // 生成类任务：开始轮询，完成后刷新消息
  if (createdTaskId) {
    pollTask(createdTaskId)
  } else if (isNewConversation) {
    await loadConversations()
  } else {
    await loadConversations()
  }

  await nextTick()
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = document.querySelector('.message-container')
    if (container) container.scrollTop = container.scrollHeight
  })
}

const formatTime = (timeStr: string) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(async () => {
  await loadConversations()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0])
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="ai-creation">
    <el-row :gutter="20" class="creation-layout">
      <!-- 左侧会话列表 -->
      <el-col :span="6" class="creation-column">
        <el-card class="creation-sidebar" shadow="never">
          <template #header>
            <div class="sidebar-header">
              <span>创作会话</span>
              <el-button :icon="Plus" circle size="small" @click="createNewConversation" />
            </div>
          </template>
          <div class="conversation-list">
            <div v-if="loadingConversations" class="empty-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <p>加载中...</p>
            </div>
            <template v-else>
              <div
                v-for="conv in conversations"
                :key="conv.id"
                class="conversation-item"
                :class="{ active: currentConversationId === conv.id }"
                @click="selectConversation(conv)"
              >
                <div class="conversation-title">{{ conv.title || '新创作' }}</div>
                <div class="conversation-info">
                  <span class="message-count">{{ conv.messageCount }} 条消息</span>
                  <el-button :icon="Delete" circle size="small" type="danger" text @click="handleDeleteConversation(conv, $event)" />
                </div>
              </div>
              <div v-if="conversations.length === 0" class="empty-state">
                <p>暂无会话，点击右上角 + 开始创作</p>
              </div>
            </template>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧创作区 -->
      <el-col :span="18" class="creation-column">
        <el-card class="chat-panel" shadow="never">
          <template #header>
            <div class="chat-header">
              <span class="chat-title">{{ currentConversation?.title || '智能创作' }}</span>
            </div>
          </template>

          <div class="message-container">
            <div v-if="loadingMessages" class="empty-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <p>加载消息中...</p>
            </div>
            <template v-else>
              <div v-for="message in messages" :key="message.id" class="message-item" :class="message.role">
                <div class="message-avatar">
                  <el-avatar :size="40">
                    <template v-if="message.role === 'USER'">U</template>
                    <template v-else-if="message.role === 'ASSISTANT'">AI</template>
                    <template v-else>S</template>
                  </el-avatar>
                </div>
                <div class="message-content">
                  <div class="message-meta">
                    <span class="message-role">{{ message.role === 'USER' ? '用户' : message.role === 'ASSISTANT' ? 'Agnes' : '系统' }}</span>
                    <span v-if="message.role === 'ASSISTANT' && message.modelName" class="message-model">{{ message.modelName }}</span>
                    <span class="message-time">{{ formatTime(message.createTime) }}</span>
                  </div>

                  <!-- 图片消息 -->
                  <div v-if="message.messageType === 'IMAGE' && messageImages(message).length" class="message-images">
                    <el-image
                      v-for="(url, idx) in messageImages(message)"
                      :key="idx"
                      :src="url"
                      :preview-src-list="messageImages(message)"
                      :initial-index="idx"
                      fit="cover"
                      class="message-image"
                      preview-teleported
                    />
                  </div>
                  <!-- 视频消息 -->
                  <div v-else-if="message.messageType === 'VIDEO' && messageVideos(message).length" class="message-videos">
                    <div v-for="(item, idx) in messageVideos(message)" :key="idx" class="video-item">
                      <video :src="item.videoUrl" controls class="message-video" />
                    </div>
                  </div>
                  <!-- 助手文本（markdown） -->
                  <div
                    v-else-if="message.role === 'ASSISTANT'"
                    class="message-text markdown-body"
                    :class="{ 'is-empty': !message.content }"
                    v-html="message.content ? renderMarkdown(message.content) : '<span class=\'streaming-cursor\'>▍</span>'"
                  />
                  <!-- 用户文本（可带上传/素材图片） -->
                  <template v-else>
                    <div v-if="message.role === 'USER' && messageImages(message).length" class="message-images">
                      <el-image
                        v-for="(url, idx) in messageImages(message)"
                        :key="idx"
                        :src="url"
                        :preview-src-list="messageImages(message)"
                        :initial-index="idx"
                        fit="cover"
                        class="message-image"
                        preview-teleported
                      />
                    </div>
                    <div v-if="message.content" class="message-text">{{ message.content }}</div>
                  </template>

                  <!-- 图片/视频消息附带的说明文字 -->
                  <div
                    v-if="(message.messageType === 'IMAGE' || message.messageType === 'VIDEO') && message.content"
                    class="message-caption"
                  >
                    {{ message.content }}
                  </div>
                </div>
              </div>
              <div v-if="polling" class="empty-state">
                <el-icon class="is-loading"><Loading /></el-icon>
                <p>正在生成，请稍候...</p>
              </div>
              <div v-if="messages.length === 0 && currentConversationId && !polling" class="empty-state">
                <p>说点什么，让 Agnes 帮你聊天、画图或生成视频吧</p>
              </div>
            </template>
          </div>

          <div class="input-container">
            <div class="composer">
              <div v-if="pendingImages.length" class="pending-attachments">
                <div v-for="(img, idx) in pendingImages" :key="img.url" class="pending-thumb">
                  <img :src="img.url" :alt="img.fileName" />
                  <button class="thumb-remove" type="button" @click="removePendingImage(idx)">
                    <el-icon><CloseIcon /></el-icon>
                  </button>
                </div>
              </div>

              <textarea
                v-model="inputText"
                class="composer-textarea"
                placeholder="输入内容，按 Ctrl+Enter 发送。例如：画一只柴犬 / 把它改成夜景 / 用这张图生成视频"
                rows="3"
                :disabled="sending || polling"
                @keydown.ctrl.enter.prevent="sendMessage"
              />
              <div class="composer-toolbar">
                <button
                  type="button"
                  class="tool-btn"
                  :disabled="sending || polling || uploadingImage || pendingImages.length >= MAX_PENDING_IMAGES"
                  :title="uploadingImage ? '正在上传...' : '上传图片'"
                  @click="triggerFilePicker"
                >
                  <el-icon class="tool-icon"><Loading v-if="uploadingImage" /><Picture v-else /></el-icon>
                </button>
                <el-button
                  text
                  size="small"
                  :disabled="sending || polling || pendingImages.length >= MAX_PENDING_IMAGES"
                  @click="openAssetDialog"
                >从素材库选择</el-button>
                <span class="composer-tip">自动识别意图：对话 / 生图 / 生视频</span>
                <span class="composer-spacer" />
                <el-button
                  v-permission="'ai:creation:send'"
                  type="primary"
                  :loading="sending"
                  :disabled="polling"
                  :icon="Position"
                  @click="sendMessage"
                >发送</el-button>
              </div>
            </div>

            <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              multiple
              style="display: none"
              @change="onFileSelected"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 素材库选择弹窗 -->
    <el-dialog v-model="assetDialogVisible" title="从素材库选择图片" width="720px" :close-on-click-modal="false">
      <div v-loading="assetLoading" class="asset-grid">
        <div v-for="asset in assetList" :key="asset.id" class="asset-cell" @click="pickAsset(asset)">
          <el-image :src="asset.coverUrl || asset.fileUrl" fit="cover" class="asset-thumb" />
          <div class="asset-name" :title="asset.fileName">{{ asset.fileName }}</div>
        </div>
        <el-empty v-if="!assetLoading && !assetList.length" description="暂无图片素材" style="grid-column: 1 / -1" />
      </div>
      <template #footer>
        <el-pagination
          v-model:current-page="assetPage"
          :page-size="assetPageSize"
          :total="assetTotal"
          layout="total, prev, pager, next"
          @current-change="handleAssetPageChange"
        />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ai-creation {
  height: calc(100vh - 138px);
  min-height: 0;
}

.creation-layout {
  height: 100%;
  min-height: 0;
}

.creation-column {
  height: 100%;
  min-height: 0;
}

.creation-sidebar {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    padding: 12px 0;
    overflow: hidden;
  }

  .sidebar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .conversation-list {
    height: 100%;
    overflow-y: auto;
    padding: 0 12px;
  }

  .conversation-item {
    padding: 12px;
    border-radius: 8px;
    cursor: pointer;
    transition: background-color 0.2s;
    margin-bottom: 8px;

    &:hover {
      background-color: var(--el-fill-color-light);
    }

    &.active {
      background-color: var(--el-color-primary-light-9);
    }

    .conversation-title {
      font-size: 14px;
      font-weight: 500;
      color: var(--el-text-color-primary);
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .conversation-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .message-count {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .empty-state {
    text-align: center;
    padding: 40px 20px;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}

.chat-panel {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
  }

  .chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    .chat-title {
      font-size: 16px;
      font-weight: 500;
      color: var(--el-text-color-primary);
    }
  }

  .message-container {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 20px;

    .message-item {
      display: flex;
      margin-bottom: 24px;

      &.USER {
        flex-direction: row-reverse;

        .message-content {
          margin-right: 12px;
          margin-left: 0;
          align-items: flex-end;
        }

        .message-text {
          background-color: var(--el-color-primary);
          color: #fff;
          border-color: var(--el-color-primary);
        }
      }

      .message-avatar {
        flex-shrink: 0;
      }

      .message-content {
        max-width: 70%;
        margin-left: 12px;
        display: flex;
        flex-direction: column;

        .message-meta {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 8px;
          font-size: 12px;

          .message-role {
            font-weight: 500;
            color: var(--el-text-color-primary);
          }

          .message-model {
            padding: 1px 8px;
            border: 1px solid var(--el-border-color-lighter);
            border-radius: 10px;
            color: var(--el-text-color-regular);
            background: var(--el-fill-color-lighter);
            font-size: 11px;
            line-height: 1.4;
          }

          .message-time {
            color: var(--el-text-color-secondary);
          }
        }

        .message-text {
          padding: 12px 16px;
          background-color: var(--el-fill-color-light);
          color: var(--el-text-color-primary);
          border: 1px solid var(--el-border-color-lighter);
          border-radius: 8px;
          line-height: 1.6;
          white-space: pre-wrap;
          word-break: break-word;

          &.markdown-body {
            white-space: normal;
          }

          &.is-empty {
            min-width: 24px;
          }
        }

        .message-caption {
          margin-top: 6px;
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }

        .message-images {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;

          .message-image {
            width: 160px;
            height: 160px;
            border-radius: 8px;
            border: 1px solid var(--el-border-color-lighter);
            background: var(--el-fill-color-lighter);
            cursor: zoom-in;
            overflow: hidden;
          }
        }

        .message-videos {
          display: flex;
          flex-direction: column;
          gap: 8px;

          .message-video {
            max-width: 360px;
            border-radius: 8px;
            border: 1px solid var(--el-border-color-lighter);
            background: #000;
          }
        }
      }
    }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }

  .input-container {
    flex-shrink: 0;
    padding: 16px 20px 20px;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .composer {
    position: relative;
    border: 1px solid var(--el-border-color);
    border-radius: 10px;
    background: var(--el-bg-color);
    transition: border-color 0.2s, box-shadow 0.2s;
    padding: 12px 12px 8px;

    &:focus-within {
      border-color: var(--el-color-primary);
      box-shadow: 0 0 0 2px var(--el-color-primary-light-9);
    }

    .pending-attachments {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 8px;

      .pending-thumb {
        position: relative;
        width: 64px;
        height: 64px;
        border-radius: 8px;
        overflow: hidden;
        border: 1px solid var(--el-border-color-lighter);
        background: var(--el-fill-color-lighter);

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          display: block;
        }

        .thumb-remove {
          position: absolute;
          top: 2px;
          right: 2px;
          width: 20px;
          height: 20px;
          border-radius: 50%;
          background: rgba(0, 0, 0, 0.55);
          border: none;
          color: #fff;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0;
        }
      }
    }

    .composer-textarea {
      width: 100%;
      min-height: 64px;
      max-height: 200px;
      resize: vertical;
      border: none;
      outline: none;
      background: transparent;
      color: var(--el-text-color-primary);
      font-family: inherit;
      font-size: 14px;
      line-height: 1.6;
      padding: 4px 4px 8px;
      box-sizing: border-box;

      &::placeholder {
        color: var(--el-text-color-placeholder);
      }

      &:disabled {
        color: var(--el-text-color-disabled);
        cursor: not-allowed;
      }
    }

    .composer-toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding-top: 6px;
      border-top: 1px dashed var(--el-border-color-lighter);

      .tool-btn {
        width: 32px;
        height: 32px;
        border-radius: 8px;
        border: 1px solid var(--el-border-color-lighter);
        background: var(--el-fill-color-lighter);
        color: var(--el-text-color-regular);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          color: var(--el-color-primary);
          border-color: var(--el-color-primary);
          background: var(--el-color-primary-light-9);
        }

        &:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }

        .tool-icon {
          font-size: 18px;
        }
      }

      .composer-tip {
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }

      .composer-spacer {
        flex: 1;
      }
    }
  }
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  min-height: 200px;
  max-height: 50vh;
  overflow-y: auto;

  .asset-cell {
    cursor: pointer;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    overflow: hidden;
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover {
      border-color: var(--el-color-primary);
      box-shadow: var(--el-box-shadow-light);
    }

    .asset-thumb {
      width: 100%;
      height: 120px;
      display: block;
    }

    .asset-name {
      padding: 6px 8px;
      font-size: 12px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
