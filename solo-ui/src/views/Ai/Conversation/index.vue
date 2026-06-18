<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'

defineOptions({ name: 'AiConversation' })
import {
  sendChatApi,
  getConversationsApi,
  getConversationMessagesApi,
  createConversationApi,
  deleteConversationApi,
  type AiConversation,
  type AiMessage
} from '@/api/ai'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Loading, Promote } from '@element-plus/icons-vue'

const conversations = ref<AiConversation[]>([])
const currentConversationId = ref<string | null>(null)
const messages = ref<AiMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const loadingMessages = ref(false)
const loadingConversations = ref(false)

const currentConversation = computed(() =>
  conversations.value.find((c) => c.id === currentConversationId.value)
)

const loadConversations = async () => {
  loadingConversations.value = true
  try {
    const res = await getConversationsApi({ page: 1, pageSize: 100 })
    conversations.value = res.data.records || []
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
  if (!content) return
  if (sending.value) return

  sending.value = true

  // 添加用户消息到界面
  const userMessage: AiMessage = {
    id: 'temp-' + Date.now(),
    conversationId: currentConversationId.value || '',
    role: 'USER',
    content,
    messageType: 'TEXT',
    modelName: '',
    tokens: 0,
    createTime: new Date().toISOString()
  }
  messages.value.push(userMessage)
  inputText.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const res = await sendChatApi({
      conversationId: currentConversationId.value || undefined,
      content
    })

    // 如果是新会话，刷新会话列表
    if (!currentConversationId.value) {
      await loadConversations()
      if (conversations.value.length > 0) {
        currentConversationId.value = conversations.value[0].id
      }
    }

    // 添加助手消息
    const assistantMessage: AiMessage = {
      id: 'assistant-' + Date.now(),
      conversationId: currentConversationId.value || '',
      role: 'ASSISTANT',
      content: res.data,
      messageType: 'TEXT',
      modelName: '',
      tokens: 0,
      createTime: new Date().toISOString()
    }
    messages.value.push(assistantMessage)

    // 刷新消息列表
    if (currentConversationId.value) {
      await loadMessages(currentConversationId.value)
    }

    await nextTick()
    scrollToBottom()
  } catch {
    ElMessage.error('发送消息失败')
  } finally {
    sending.value = false
  }
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
  await loadConversations()
  if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0])
  }
})
</script>

<template>
  <div class="ai-conversation">
    <el-row :gutter="20" style="height: calc(100vh - 140px)">
      <!-- 左侧会话列表 -->
      <el-col :span="6">
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
      <el-col :span="18">
        <el-card class="chat-panel" shadow="never">
          <template #header>
            <div class="chat-header">
              <span class="chat-title">{{ currentConversation?.title || '新对话' }}</span>
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
                    <span class="message-time">{{ formatTime(message.createTime) }}</span>
                  </div>
                  <div class="message-text">{{ message.content }}</div>
                </div>
              </div>
              <div v-if="messages.length === 0 && currentConversationId" class="empty-state">
                <p>开始和 Agnes 对话吧</p>
              </div>
            </template>
          </div>

          <!-- 输入区域 -->
          <div class="input-container">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="3"
              placeholder="输入消息，按 Ctrl+Enter 发送..."
              @keydown.ctrl.enter="sendMessage"
              :disabled="sending"
            />
            <div class="input-actions">
              <el-button type="primary" :loading="sending" :icon="Promote" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.ai-conversation {
  height: 100%;
}

.conversation-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
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
      background-color: #f5f7fa;
    }

    &.active {
      background-color: #ecf5ff;
    }

    .conversation-title {
      font-size: 14px;
      font-weight: 500;
      color: #303133;
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
      color: #909399;
    }
  }

  .empty-state {
    text-align: center;
    padding: 40px 20px;
    color: #909399;
    font-size: 14px;
  }
}

.chat-panel {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
  }

  .chat-header {
    .chat-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .message-container {
    flex: 1;
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
          background-color: #409eff;
          color: #fff;
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
            color: #303133;
          }

          .message-time {
            color: #909399;
          }
        }

        .message-text {
          padding: 12px 16px;
          background-color: #f5f7fa;
          border-radius: 8px;
          line-height: 1.6;
          white-space: pre-wrap;
          word-break: break-word;
        }
      }
    }

    .empty-state {
      text-align: center;
      padding: 80px 20px;
      color: #909399;
      font-size: 14px;
    }
  }

  .input-container {
    padding: 20px;
    border-top: 1px solid #ebeef5;

    .input-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 12px;
    }
  }
}
</style>
