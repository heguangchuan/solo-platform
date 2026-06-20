<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sendMessage, broadcastMessage, getMessageList, markAllMessagesRead, getWsOnlineCount } from '@/api/message'
import { useMessageStore } from '@/stores/message'
import { useUserStore } from '@/stores/user'

const messageStore = useMessageStore()
const userStore = useUserStore()

const msgTitle = ref('测试消息')
const msgContent = ref('这是一条测试消息内容')
const msgType = ref<'SYSTEM' | 'NOTICE' | 'REMINDER' | 'BROADCAST'>('NOTICE')
const msgLevel = ref<'INFO' | 'WARNING' | 'ERROR' | 'IMPORTANT'>('INFO')
const targetUsername = ref('')
const isSending = ref(false)
const isBroadcasting = ref(false)
const testMessages = ref<any[]>([])
const isLoadingMessages = ref(false)
const wsOnlineCount = ref(0)
const isLoadingOnlineCount = ref(false)

const currentUsername = computed(() => userStore.user?.username || '')

async function handleSendMessage() {
  if (!targetUsername.value.trim()) {
    ElMessage.warning('请输入目标用户名')
    return
  }
  if (!msgTitle.value.trim()) {
    ElMessage.warning('请输入消息标题')
    return
  }

  isSending.value = true
  try {
    await sendMessage({
      username: targetUsername.value,
      title: msgTitle.value,
      content: msgContent.value,
      type: msgType.value,
      level: msgLevel.value,
    })
    ElMessage.success('消息发送成功')
    await refreshAll()
  } catch (e) {
    ElMessage.error('消息发送失败')
  } finally {
    isSending.value = false
  }
}

async function handleBroadcast() {
  if (!msgTitle.value.trim()) {
    ElMessage.warning('请输入消息标题')
    return
  }

  isBroadcasting.value = true
  try {
    await broadcastMessage({
      title: msgTitle.value,
      content: msgContent.value,
      level: msgLevel.value,
    })
    ElMessage.success('广播消息发送成功')
    await refreshAll()
  } catch (e) {
    ElMessage.error('广播消息发送失败')
  } finally {
    isBroadcasting.value = false
  }
}

async function loadMessages() {
  isLoadingMessages.value = true
  try {
    const res = await getMessageList(20)
    testMessages.value = res.data
  } catch (e) {
    ElMessage.error('加载消息列表失败')
  } finally {
    isLoadingMessages.value = false
  }
}

async function handleMarkAllRead() {
  try {
    await markAllMessagesRead()
    await messageStore.fetchUnreadCount()
    await loadMessages()
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function loadOnlineCount() {
  isLoadingOnlineCount.value = true
  try {
    const res = await getWsOnlineCount()
    wsOnlineCount.value = res.data
  } catch (e) {
    console.error('获取在线用户数失败:', e)
  } finally {
    isLoadingOnlineCount.value = false
  }
}

async function refreshAll() {
  await Promise.all([
    loadOnlineCount(),
    loadMessages(),
    messageStore.fetchUnreadCount(),
  ])
}

function getLevelTagType(level: string) {
  const map: Record<string, string> = {
    INFO: 'info',
    WARNING: 'warning',
    ERROR: 'danger',
    IMPORTANT: 'success',
  }
  return map[level] || 'info'
}

function getTypeText(type: string) {
  const map: Record<string, string> = {
    SYSTEM: '系统通知',
    NOTICE: '普通通知',
    REMINDER: '提醒',
    BROADCAST: '广播',
  }
  return map[type] || type
}

onMounted(() => {
  targetUsername.value = currentUsername.value
  refreshAll()
})
</script>

<template>
  <el-card shadow="never" class="test-card">
    <template #header>
      <span>消息发送测试</span>
      <el-tag size="small" type="info" style="margin-left: 12px">
        WebSocket 实时推送
      </el-tag>
    </template>

    <el-form label-width="120px">
      <el-form-item label="目标用户名">
        <el-input
          v-model="targetUsername"
          placeholder="接收消息的用户"
          style="width: 320px"
        />
        <el-button link @click="targetUsername = currentUsername">
          设为当前用户（{{ currentUsername }}）
        </el-button>
      </el-form-item>

      <el-form-item label="消息标题">
        <el-input
          v-model="msgTitle"
          placeholder="消息标题"
          style="width: 320px"
        />
      </el-form-item>

      <el-form-item label="消息内容">
        <el-input
          v-model="msgContent"
          type="textarea"
          :rows="3"
          placeholder="消息内容"
          style="width: 480px"
        />
      </el-form-item>

      <el-form-item label="消息类型">
        <el-radio-group v-model="msgType">
          <el-radio value="SYSTEM">系统通知</el-radio>
          <el-radio value="NOTICE">普通通知</el-radio>
          <el-radio value="REMINDER">提醒</el-radio>
          <el-radio value="BROADCAST">广播</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="消息级别">
        <el-radio-group v-model="msgLevel">
          <el-radio value="INFO">普通</el-radio>
          <el-radio value="WARNING">警告</el-radio>
          <el-radio value="ERROR">错误</el-radio>
          <el-radio value="IMPORTANT">重要</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="isSending" @click="handleSendMessage">
          发送给指定用户
        </el-button>
        <el-button type="warning" :loading="isBroadcasting" @click="handleBroadcast">
          广播给所有在线用户
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="test-card" style="margin-top: 16px">
    <template #header>
      <span>消息状态</span>
      <div style="float: right">
        <el-tag type="primary" style="margin-right: 8px">
          在线：{{ wsOnlineCount }} 人
        </el-tag>
        <el-tag type="danger" style="margin-right: 8px">
          未读：{{ messageStore.unreadCount }} 条
        </el-tag>
        <el-button size="small" :loading="isLoadingOnlineCount" @click="loadOnlineCount">
          刷新在线
        </el-button>
        <el-button size="small" @click="handleMarkAllRead">
          全部标记已读
        </el-button>
        <el-button size="small" :loading="isLoadingMessages" @click="loadMessages">
          刷新列表
        </el-button>
      </div>
    </template>

    <el-table :data="testMessages" style="width: 100%" empty-text="暂无消息" max-height="calc(100vh - 400px)">
      <el-table-column prop="id" label="ID" width="80" / max-height="calc(100vh - 400px)">
      <el-table-column prop="title" label="标题" min-width="180" / max-height="calc(100vh - 400px)">
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip / max-height="calc(100vh - 400px)">
      <el-table-column prop="type" label="类型" width="100" max-height="calc(100vh - 400px)">
        <template #default="{ row }">
          {{ getTypeText(row.type) }}
        </template>
      </el-table-column>
      <el-table-column prop="level" label="级别" width="100" max-height="calc(100vh - 400px)">
        <template #default="{ row }">
          <el-tag :type="getLevelTagType(row.level)" size="small">
            {{ row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="readFlag" label="状态" width="80" max-height="calc(100vh - 400px)">
        <template #default="{ row }">
          <el-tag :type="row.readFlag === 1 ? 'success' : 'info'" size="small">
            {{ row.readFlag === 1 ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="180" / max-height="calc(100vh - 400px)">
    </el-table>
  </el-card>
</template>
