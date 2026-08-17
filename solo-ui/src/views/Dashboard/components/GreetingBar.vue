<script setup lang="ts">
/**
 * 顶部问候条
 *
 * <p>按本地小时切换问候语；右侧显示未读告警数 + 当前日期。
 * 渐变背景用 ds-cat-1（蓝），与现有 Element Plus 主题贴合。</p>
 */
import { computed } from 'vue'

interface Props {
  username: string
  /** 0-23 */
  hour: number
  unreadCount: number
}

const props = defineProps<Props>()

const greeting = computed(() => {
  const h = props.hour
  if (h < 6) return '凌晨好'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 22) return '晚上好'
  return '夜深了'
})

const now = new Date()
const dateText = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const weekDay = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]
const timeText = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
</script>

<template>
  <div class="greeting-bar">
    <div class="greeting-left">
      <div class="greeting-text">
        <span class="greeting-prefix">{{ greeting }}，</span>
        <span class="greeting-name">{{ username || 'admin' }}</span>
        <span class="greeting-emoji">👋</span>
      </div>
      <div class="greeting-subtitle">欢迎回到 Solo 平台 · 视频监控运营中心</div>
    </div>
    <div class="greeting-right">
      <div class="greeting-stat">
        <span class="stat-num">{{ unreadCount }}</span>
        <span class="stat-label">未读消息</span>
      </div>
      <div class="greeting-divider" />
      <div class="greeting-stat">
        <span class="stat-num">{{ timeText }}</span>
        <span class="stat-label">{{ dateText }} {{ weekDay }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.greeting-bar {
  background: linear-gradient(135deg, #409EFF 0%, #17A2B8 100%);
  color: #fff;
  border-radius: var(--ds-card-radius);
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: var(--ds-card-shadow);
  margin-bottom: var(--ds-gap);
}

.greeting-text {
  font-size: 22px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.greeting-name {
  font-weight: 600;
  margin: 0 2px;
}

.greeting-emoji {
  margin-left: 6px;
  font-size: 20px;
}

.greeting-subtitle {
  font-size: 13px;
  opacity: 0.86;
  margin-top: 4px;
  font-weight: 400;
}

.greeting-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.greeting-stat {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-num {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-label {
  font-size: 11px;
  opacity: 0.85;
}

.greeting-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.3);
}
</style>
