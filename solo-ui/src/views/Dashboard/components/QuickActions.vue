<script setup lang="ts">
/**
 * 快捷入口卡片网格
 * <p>6 个常用功能入口：点击直接 push 到对应路由。
 * 路由用静态硬编码而非查菜单，避免权限变更时这页报错。</p>
 */
import { useRouter } from 'vue-router'

interface Action {
  label: string
  icon: string
  route: string
  color: string
}

const actions: Action[] = [
  { label: '添加设备', icon: '➕', route: '/gb28181/device', color: 'var(--ds-cat-1)' },
  { label: '通道管理', icon: '☰', route: '/gb28181/device-channel', color: 'var(--ds-cat-2)' },
  { label: '实时点播', icon: '▶', route: '/gb28181/device', color: 'var(--ds-success)' },
  { label: '录像回放', icon: '⏪', route: '/gb28181/device', color: 'var(--ds-cat-3)' },
  { label: 'ZLM 配置', icon: '⚙', route: '/media/server', color: 'var(--ds-cat-4)' },
  { label: '系统日志', icon: '☷', route: '/system/log', color: 'var(--ds-cat-5)' },
]

const router = useRouter()
function onClick(a: Action) {
  router.push(a.route)
}
</script>

<template>
  <div class="quick-actions">
    <div
      v-for="a in actions"
      :key="a.label"
      class="action-card"
      :style="{ '--action-color': a.color }"
      @click="onClick(a)"
    >
      <span class="action-icon">{{ a.icon }}</span>
      <span class="action-label">{{ a.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.quick-actions {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.action-card {
  background: var(--el-bg-color);
  border-radius: var(--ds-card-radius);
  box-shadow: var(--ds-card-shadow);
  padding: 20px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.18s ease;
  border: 1px solid transparent;
}

.action-card:hover {
  transform: translateY(-2px);
  border-color: var(--action-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.action-icon {
  font-size: 24px;
  color: var(--action-color);
  line-height: 1;
}

.action-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

@media (max-width: 1200px) {
  .quick-actions {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
