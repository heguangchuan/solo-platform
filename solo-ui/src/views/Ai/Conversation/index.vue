<script setup lang="ts">
import { ref, onMounted, nextTick, computed, watch } from 'vue'

defineOptions({ name: 'AiConversation' })
import {
  getConversationsApi,
  getConversationMessagesApi,
  getModelConfigListApi,
  createConversationApi,
  deleteConversationApi,
  type AiConversation,
  type AiMessage,
  type ChatAttachmentInput
} from '@/api/ai'
import { uploadFileApi } from '@/api/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Loading, Position, Picture, Close as CloseIcon } from '@element-plus/icons-vue'
import type { AiModelConfig } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { renderMarkdown } from '@/utils/markdown'

const userStore = useUserStore()

const conversations = ref<AiConversation[]>([])
const currentConversationId = ref<string | null>(null)
const messages = ref<AiMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const loadingMessages = ref(false)
const loadingConversations = ref(false)
const modelOptions = ref<AiModelConfig[]>([])
const selectedModelConfigId = ref<string>('')
const pendingImages = ref<ChatAttachmentInput[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadingImage = ref(false)
const MAX_PENDING_IMAGES = 4

const currentConversation = computed(() =>
  conversations.value.find((c) => c.id === currentConversationId.value)
)

const currentModel = computed(() =>
  modelOptions.value.find((m) => m.id === selectedModelConfigId.value) || null
)

const supportsVision = computed(() => (currentModel.value?.supportVision ?? 0) === 1)

interface ParsedAttachment {
  type: string
  url: string
  fileName?: string
  mimeType?: string
}

const parseAttachments = (raw: string | undefined | null): ParsedAttachment[] => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((it) => it && typeof it.url === 'string')
  } catch {
    return []
  }
}

const messageImages = (msg: AiMessage): string[] =>
  parseAttachments(msg.attachments)
    .filter((a) => (a.type || 'IMAGE').toUpperCase() === 'IMAGE')
    .map((a) => a.url)

const triggerFilePicker = () => {
  if (uploadingImage.value || sending.value) return
  if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
    ElMessage.warning(`最多上传 ${MAX_PENDING_IMAGES} 张图片`)
    return
  }
  fileInputRef.value?.click()
}

const onFileSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持上传图片')
    return
  }
  if (pendingImages.value.length >= MAX_PENDING_IMAGES) {
    ElMessage.warning(`最多上传 ${MAX_PENDING_IMAGES} 张图片`)
    return
  }
  uploadingImage.value = true
  try {
    const res = await uploadFileApi(file)
    const url = res.data?.url
    if (!url) {
      ElMessage.error('上传失败')
      return
    }
    pendingImages.value.push({
      url,
      fileName: file.name,
      mimeType: file.type
    })
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploadingImage.value = false
  }
}

const removePendingImage = (index: number) => {
  pendingImages.value.splice(index, 1)
}

watch(supportsVision, (canVision) => {
  if (!canVision && pendingImages.value.length > 0) {
    ElMessage.info('当前模型不支持图片输入，已清空待发送图片')
    pendingImages.value = []
  }
})

const loadModelOptions = async () => {
  try {
    const res = await getModelConfigListApi()
    modelOptions.value = (res.data || []).filter((model) => model.modelType === 'TEXT' && model.status === 1)
    if (!selectedModelConfigId.value && modelOptions.value.length > 0) {
      selectedModelConfigId.value = modelOptions.value[0].id
    }
  } catch {
    ElMessage.error('加载模型列表失败')
  }
}

const loadConversations = async () => {
  loadingConversations.value = true
  try {
    const res = await getConversationsApi({ page: 1, pageSize: 100 })
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
  currentConversationId.value = conversation.id
  if (conversation.modelConfigId) {
    selectedModelConfigId.value = conversation.modelConfigId
  }
  await loadMessages(conversation.id)
}

const createNewConversation = async () => {
  try {
    const res = await createConversationApi({ title: '新对话', conversationType: 'CHAT' })
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
    if (err !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const sendMessage = async () => {
  const content = inputText.value.trim()
  const imagesToSend = pendingImages.value.slice()
  if (!content && imagesToSend.length === 0) return
  if (sending.value) return

  sending.value = true

  // 添加用户消息到界面（含附件预览）
  const userMessage: AiMessage = {
    id: 'temp-' + Date.now(),
    conversationId: currentConversationId.value || '',
    role: 'USER',
    content,
    messageType: 'TEXT',
    modelName: '',
    tokens: 0,
    attachments: imagesToSend.length > 0
      ? JSON.stringify(imagesToSend.map((img) => ({
        type: 'IMAGE',
        url: img.url,
        fileName: img.fileName,
        mimeType: img.mimeType
      })))
      : undefined,
    createTime: new Date().toISOString()
  }
  messages.value.push(userMessage)
  inputText.value = ''
  pendingImages.value = []

  // 占位的助手消息，用于流式追加
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

  try {
    const response = await fetch('/api/ai/chat/stream', {
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
        modelConfigId: selectedModelConfigId.value || undefined,
        images: imagesToSend.length > 0 ? imagesToSend : undefined
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
        } else if (currentEvent === 'done') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.conversationId) {
              streamedConversationId = String(parsed.conversationId)
            }
            if (typeof parsed.reply === 'string' && parsed.reply.length > 0) {
              assistantMessage.content = parsed.reply
            }
          } catch {
            // ignore parse errors
          }
        } else if (currentEvent === 'error') {
          errorMessage = data
        }
      }
    }
  } catch (e: any) {
    errorMessage = e?.message || '发送消息失败'
  }

  if (errorMessage && !receivedAny) {
    assistantMessage.content = ''
    messages.value = messages.value.filter((m) => m.id !== assistantMessage.id)
    ElMessage.error(errorMessage || '发送消息失败')
  } else if (errorMessage) {
    ElMessage.warning('回复中断：' + errorMessage)
  }

  sending.value = false

  // 新会话：刷新列表并切换到新会话
  if (!currentConversationId.value && streamedConversationId) {
    currentConversationId.value = streamedConversationId
    await loadConversations()
  } else if (streamedConversationId) {
    // 已有会话：刷新最新一条消息及统计
    await loadConversations()
  }

  await nextTick()
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    const messageContainer = document.querySelector('.message-container')
    if (messageContainer) {
      messageContainer.scrollTop = messageContainer.scrollHeight
    }
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
  await loadModelOptions()
  await loadConversations()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0])
  }
})
</script>

<template>
  <div class="ai-conversation">
    <el-row :gutter="20" class="conversation-layout">
      <!-- 左侧会话列表 -->
      <el-col :span="6" class="conversation-column">
        <el-card class="conversation-sidebar" shadow="never">
          <template #header>
            <div class="sidebar-header">
              <span>会话列表</span>
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
                <div class="conversation-title">{{ conv.title || '新对话' }}</div>
                <div class="conversation-info">
                  <span class="message-count">{{ conv.messageCount }} 条消息</span>
                  <el-button
                    :icon="Delete"
                    circle
                    size="small"
                    type="danger"
                    text
                    @click="handleDeleteConversation(conv, $event)"
                  />
                </div>
              </div>
              <div v-if="conversations.length === 0" class="empty-state">
                <p>暂无会话，点击右上角 + 创建新会话</p>
              </div>
            </template>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧聊天区域 -->
      <el-col :span="18" class="conversation-column">
        <el-card class="chat-panel" shadow="never">
          <template #header>
            <div class="chat-header">
              <span class="chat-title">{{ currentConversation?.title || '新对话' }}</span>
              <el-select v-model="selectedModelConfigId" placeholder="请选择模型" style="width: 220px" :disabled="sending">
                <el-option
                  v-for="model in modelOptions"
                  :key="model.id"
                  :label="model.modelName"
                  :value="model.id"
                />
              </el-select>
            </div>
          </template>

          <!-- 消息区域 -->
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
                    <span class="message-role">{{
                      message.role === 'USER' ? '用户' : message.role === 'ASSISTANT' ? 'Agnes' : '系统'
                    }}</span>
                    <span
                      v-if="message.role === 'ASSISTANT' && message.modelName"
                      class="message-model"
                    >
                      {{ message.modelName }}
                    </span>
                    <span class="message-time">{{ formatTime(message.createTime) }}</span>
                  </div>
                  <div
                    v-if="message.role === 'ASSISTANT'"
                    class="message-text markdown-body"
                    :class="{ 'is-empty': !message.content }"
                    v-html="message.content ? renderMarkdown(message.content) : '<span class=\'streaming-cursor\'>▍</span>'"
                  />
                  <template v-else>
                    <div v-if="messageImages(message).length" class="message-images">
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
                </div>
              </div>
              <div v-if="messages.length === 0 && currentConversationId" class="empty-state">
                <p>开始和 Agnes 对话吧</p>
              </div>
            </template>
          </div>

          <!-- 输入区域 -->
          <div class="input-container">
            <div v-if="pendingImages.length" class="pending-attachments">
              <div
                v-for="(img, idx) in pendingImages"
                :key="img.url"
                class="pending-thumb"
              >
                <img :src="img.url" :alt="img.fileName" />
                <button class="thumb-remove" type="button" @click="removePendingImage(idx)">
                  <el-icon><CloseIcon /></el-icon>
                </button>
              </div>
            </div>
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              placeholder="输入消息，按 Ctrl+Enter 发送..."
              @keydown.ctrl.enter="sendMessage"
              :disabled="sending"
            />
            <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              style="display: none"
              @change="onFileSelected"
            />
            <div class="input-actions">
              <el-button
                v-if="supportsVision"
                :icon="Picture"
                :loading="uploadingImage"
                :disabled="sending || pendingImages.length >= MAX_PENDING_IMAGES"
                @click="triggerFilePicker"
              >
                上传图片
              </el-button>
              <el-button type="primary" :loading="sending" :icon="Position" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.ai-conversation {
  height: calc(100vh - 138px);
  min-height: 0;
}

.conversation-layout {
  height: 100%;
  min-height: 0;
}

.conversation-column {
  height: 100%;
  min-height: 0;
}

.conversation-sidebar {
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

        .message-images {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          margin-bottom: 8px;

          .message-image {
            width: 120px;
            height: 120px;
            border-radius: 8px;
            border: 1px solid var(--el-border-color-lighter);
            background: var(--el-fill-color-lighter);
            cursor: zoom-in;
            overflow: hidden;
          }
        }
      }
    }

    .empty-state {
      text-align: center;
      padding: 80px 20px;
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }

  .input-container {
    flex-shrink: 0;
    padding: 20px;
    border-top: 1px solid var(--el-border-color-lighter);

    .pending-attachments {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;

      .pending-thumb {
        position: relative;
        width: 72px;
        height: 72px;
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

    .input-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 12px;
    }
  }
}
</style>

<style lang="scss">
/* markdown 渲染样式（非 scoped，因为 v-html 注入的元素不在 scoped 作用域内） */
.markdown-body {
  font-size: 14px;
  line-height: 1.7;

  > *:first-child {
    margin-top: 0;
  }

  > *:last-child {
    margin-bottom: 0;
  }

  /* KaTeX 渲染出来的块级公式默认会撑满一行，让它居中、上下留点空 */
  .katex-display {
    margin: 0.6em 0;
    overflow-x: auto;
  }

  .katex {
    font-size: 1.05em;
  }

  p {
    margin: 0.5em 0;
  }

  h1, h2, h3, h4, h5, h6 {
    margin: 1em 0 0.5em;
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.4;
  }
  h1 { font-size: 1.4em; }
  h2 { font-size: 1.3em; }
  h3 { font-size: 1.15em; }
  h4 { font-size: 1.05em; }

  ul, ol {
    padding-left: 1.5em;
    margin: 0.5em 0;
  }

  li {
    margin: 0.25em 0;
  }

  blockquote {
    margin: 0.5em 0;
    padding: 4px 12px;
    border-left: 3px solid var(--el-color-primary);
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-lighter);
    border-radius: 0 4px 4px 0;
  }

  a {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  hr {
    border: none;
    border-top: 1px solid var(--el-border-color-lighter);
    margin: 12px 0;
  }

  code {
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Fira Code', 'Source Code Pro', Menlo, Consolas, monospace;
    font-size: 0.9em;
    background: var(--el-fill-color);
    color: var(--el-color-danger);
  }

  pre {
    margin: 0.5em 0;
    padding: 12px 14px;
    border-radius: 6px;
    background: var(--el-fill-color-darker);
    overflow-x: auto;
    font-family: 'Fira Code', 'Source Code Pro', Menlo, Consolas, monospace;
    font-size: 0.9em;
    line-height: 1.5;

    code {
      background: transparent;
      color: var(--el-text-color-primary);
      padding: 0;
      border-radius: 0;
    }
  }

  table {
    border-collapse: collapse;
    margin: 0.5em 0;
    width: 100%;
    border: 1px solid var(--el-border-color-lighter);

    th, td {
      padding: 6px 12px;
      border: 1px solid var(--el-border-color-lighter);
      text-align: left;
    }

    th {
      background: var(--el-fill-color-light);
      font-weight: 600;
    }
  }

  img {
    max-width: 100%;
    border-radius: 4px;
  }

  .streaming-cursor {
    display: inline-block;
    margin-left: 2px;
    color: var(--el-color-primary);
    animation: blink 1s steps(2, start) infinite;
  }
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

/* highlight.js 主题：基于 github / github-dark 简化版，使用 EP 变量 */
.markdown-body .hljs {
  display: block;
  overflow-x: auto;
  background: transparent;
  color: var(--el-text-color-primary);
}
.markdown-body .hljs-comment,
.markdown-body .hljs-quote {
  color: #6a737d;
  font-style: italic;
}
.markdown-body .hljs-keyword,
.markdown-body .hljs-selector-tag,
.markdown-body .hljs-built_in,
.markdown-body .hljs-name,
.markdown-body .hljs-tag {
  color: #d73a49;
}
html.dark .markdown-body .hljs-keyword,
html.dark .markdown-body .hljs-selector-tag,
html.dark .markdown-body .hljs-built_in,
html.dark .markdown-body .hljs-name,
html.dark .markdown-body .hljs-tag {
  color: #ff7b72;
}
.markdown-body .hljs-string,
.markdown-body .hljs-attr,
.markdown-body .hljs-template-tag,
.markdown-body .hljs-template-variable,
.markdown-body .hljs-addition {
  color: #032f62;
}
html.dark .markdown-body .hljs-string,
html.dark .markdown-body .hljs-attr,
html.dark .markdown-body .hljs-template-tag,
html.dark .markdown-body .hljs-template-variable,
html.dark .markdown-body .hljs-addition {
  color: #a5d6ff;
}
.markdown-body .hljs-number,
.markdown-body .hljs-literal,
.markdown-body .hljs-symbol,
.markdown-body .hljs-meta {
  color: #005cc5;
}
html.dark .markdown-body .hljs-number,
html.dark .markdown-body .hljs-literal,
html.dark .markdown-body .hljs-symbol,
html.dark .markdown-body .hljs-meta {
  color: #79c0ff;
}
.markdown-body .hljs-title,
.markdown-body .hljs-section,
.markdown-body .hljs-function {
  color: #6f42c1;
}
html.dark .markdown-body .hljs-title,
html.dark .markdown-body .hljs-section,
html.dark .markdown-body .hljs-function {
  color: #d2a8ff;
}
.markdown-body .hljs-variable,
.markdown-body .hljs-attribute {
  color: #e36209;
}
html.dark .markdown-body .hljs-variable,
html.dark .markdown-body .hljs-attribute {
  color: #ffa657;
}

/* 用户气泡使用主色背景，markdown 内的链接/代码颜色需反色显示 */
.message-item.USER .markdown-body {
  color: #fff;
}
.message-item.USER .markdown-body code,
.message-item.USER .markdown-body pre {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
</style>
