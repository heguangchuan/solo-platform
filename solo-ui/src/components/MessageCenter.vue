<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <div class="message-icon-wrapper">
      <el-icon class="message-icon" :class="{ 'has-message': unreadCount > 0 }">
        <Bell />
      </el-icon>
      <el-badge v-if="unreadCount > 0" :value="badgeCount" :hidden="unreadCount === 0" class="message-badge" />
    </div>

    <template #dropdown>
      <el-dropdown-menu class="message-dropdown">
        <div class="dropdown-header">
          <span>消息中心</span>
          <el-button type="primary" link size="small" @click.stop="handleMarkAllRead" v-if="unreadCount > 0">
            全部已读
          </el-button>
        </div>

        <el-dropdown-item divided class="message-item-empty" v-if="messages.length === 0">
          暂无消息
        </el-dropdown-item>

        <el-dropdown-item
          v-for="msg in displayedMessages"
          :key="msg.id"
          :command="{ action: 'open', message: msg }"
          class="message-item"
          :class="{ unread: msg.readFlag === 0 }"
        >
          <div class="msg-left">
            <el-icon class="msg-icon" :class="levelClass(msg.level)">
              <component :is="levelIcon(msg.level)" />
            </el-icon>
          </div>
          <div class="msg-content">
            <div class="msg-title">{{ msg.title }}</div>
            <div class="msg-desc">{{ msg.content }}</div>
            <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
          </div>
          <div class="msg-unread-dot" v-if="msg.readFlag === 0"></div>
        </el-dropdown-item>

        <el-dropdown-item divided v-if="messages.length > 0" :command="{ action: 'viewAll' }" class="view-all-btn">
          查看全部消息
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Warning, InfoFilled, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useMessageStore, type SysMessage } from '@/stores/message'

const router = useRouter()
const messageStore = useMessageStore()

const messages = computed(() => messageStore.messages)
const unreadCount = computed(() => messageStore.unreadCount)
const badgeCount = computed(() => messageStore.badgeCount)

// 最多显示 5 条消息
const displayedMessages = computed(() => messages.value.slice(0, 5))

// 消息级别对应的图标和样式
function levelIcon(level: string) {
  switch (level) {
    case 'WARNING': return Warning
    case 'ERROR': return CircleClose
    case 'IMPORTANT': return CircleCheck
    default: return InfoFilled
  }
}

function levelClass(level: string) {
  return `msg-level-${level.toLowerCase()}`
}

// 格式化时间
function formatTime(time: string) {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // 1分钟内
  if (diff < 60000) return '刚刚'
  // 1小时内
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  // 1天内
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  // 7天内
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return date.toLocaleDateString()
}

// 处理下拉菜单命令
function handleCommand(cmd: { action: string; message?: SysMessage }) {
  switch (cmd.action) {
    case 'open':
      if (cmd.message) {
        // 标记为已读
        if (cmd.message.readFlag === 0) {
          messageStore.markRead(cmd.message.id)
        }
        // 如果有路由则跳转
        if (cmd.message.route) {
          router.push(cmd.message.route)
        }
      }
      break
    case 'viewAll':
      router.push('/message')
      break
  }
}

// 全部标记为已读
function handleMarkAllRead() {
  messageStore.markAllRead()
}
</script>

<style scoped lang="scss">
.message-icon-wrapper {
  position: relative;
  cursor: pointer;
  padding: 4px;
}

.message-icon {
  font-size: 20px;
  color: var(--text-color-regular);
  transition: color 0.3s;

  &:hover, &.has-message {
    color: var(--el-color-primary);
  }
}

.message-badge {
  position: absolute;
  top: 0;
  right: 0;
  transform: scale(0.8);
}

.message-dropdown {
  width: 360px;
  max-height: 500px;
  overflow-y: auto;

  .dropdown-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    font-weight: 600;
    font-size: 14px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}

.message-item {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  position: relative;

  &:hover {
    background-color: var(--el-fill-color-light);
  }

  &.unread {
    background-color: var(--el-color-primary-light-9);
  }

  .msg-left {
    margin-right: 12px;
    margin-top: 2px;
  }

  .msg-icon {
    font-size: 18px;

    &.msg-level-info { color: var(--el-color-primary); }
    &.msg-level-warning { color: var(--el-color-warning); }
    &.msg-level-error { color: var(--el-color-danger); }
    &.msg-level-important { color: var(--el-color-success); }
  }

  .msg-content {
    flex: 1;
    min-width: 0;

    .msg-title {
      font-size: 13px;
      font-weight: 500;
      color: var(--text-color-primary);
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .msg-desc {
      font-size: 12px;
      color: var(--text-color-regular);
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .msg-time {
      font-size: 11px;
      color: var(--text-color-placeholder);
    }
  }

  .msg-unread-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: var(--el-color-danger);
    margin-left: 8px;
    flex-shrink: 0;
  }
}

.message-item-empty {
  text-align: center;
  padding: 40px 16px !important;
  color: var(--text-color-placeholder);
  font-size: 13px;
}

.view-all-btn {
  text-align: center;
  color: var(--el-color-primary) !important;
  font-size: 13px;
  padding: 12px 16px !important;
}
</style>
