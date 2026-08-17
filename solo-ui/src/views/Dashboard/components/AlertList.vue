<script setup lang="ts">
/**
 * 最近告警列表
 *
 * <p>来自 sys_message 表的最近 10 条；状态点 + 文字双编码（不靠颜色单独编码）。
 * 点击行跳到 message.route（如果有）。</p>
 */
import { useRouter } from 'vue-router'
import type { DashboardAlertRecent } from '@/types/api'

interface Props {
  alerts: DashboardAlertRecent[]
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), { emptyText: '暂无告警' })
const router = useRouter()

function levelMeta(level: string | undefined) {
  const l = (level || '').toUpperCase()
  switch (l) {
    case 'ERROR':
    case 'CRITICAL':
      return { label: '严重', cls: 'level-critical' }
    case 'WARNING':
      return { label: '警告', cls: 'level-warning' }
    case 'IMPORTANT':
      return { label: '重要', cls: 'level-important' }
    default:
      return { label: '普通', cls: 'level-info' }
  }
}

function onClick(alert: DashboardAlertRecent) {
  if (alert.route) {
    router.push(alert.route)
  }
}
</script>

<template>
  <div class="alert-list">
    <div v-if="!alerts.length" class="empty">{{ emptyText }}</div>
    <div
      v-for="a in alerts"
      :key="a.id"
      class="alert-row"
      :class="{ clickable: !!a.route }"
      @click="onClick(a)"
    >
      <span class="alert-dot" :class="levelMeta(a.level).cls" :title="levelMeta(a.level).label" />
      <span class="alert-title">{{ a.title }}</span>
      <span class="alert-time">{{ a.createTime }}</span>
    </div>
  </div>
</template>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.empty {
  padding: 24px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.alert-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  transition: background 0.15s ease;
}

.alert-row:last-child {
  border-bottom: none;
}

.alert-row.clickable {
  cursor: pointer;
}

.alert-row.clickable:hover {
  background: var(--el-fill-color-light);
}

.alert-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.level-info {
  background: var(--ds-neutral);
}

.level-warning {
  background: var(--ds-warning);
}

.level-important {
  background: var(--ds-cat-1);
}

.level-critical {
  background: var(--ds-serious);
}

.alert-title {
  flex: 1;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-time {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
