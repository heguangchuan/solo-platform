<script setup lang="ts">
/**
 * 首页（C 方案：现代极简）
 *
 * <h3>布局</h3>
 * <ol>
 *   <li>顶部问候条（GreetingBar）</li>
 *   <li>4 个 KPI 卡片横排：在线设备 / ZLM 节点 / 活跃流 / 告警</li>
 *   <li>24h 趋势折线（双系列 direct label）</li>
 *   <li>设备状态分布 + 厂商分布 双 donut</li>
 *   <li>ZLM 节点状态网格（紧凑卡片列表）</li>
 *   <li>最近告警列表</li>
 *   <li>快捷入口（6 卡片）</li>
 * </ol>
 *
 * <h3>数据流</h3>
 * <p>一次 {@code getDashboardStatsApi} 拉全；30s 轮询一次增量更新（不刷整页）。
 * 任意字段为 null 或异常时降级显示"无数据"，不阻塞整页渲染。</p>
 */
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { getDashboardStatsApi } from '@/api/dashboard'
import type { DashboardStats, DashboardTrendPoint } from '@/types/api'

import GreetingBar from './components/GreetingBar.vue'
import KpiCard from './components/KpiCard.vue'
import TrendChart from './components/TrendChart.vue'
import DonutChart from './components/DonutChart.vue'
import AlertList from './components/AlertList.vue'
import QuickActions from './components/QuickActions.vue'

import './styles/dashboard-vars.css'

const stats = ref<DashboardStats | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function refresh() {
  loading.value = true
  error.value = null
  try {
    const res = await getDashboardStatsApi()
    stats.value = res.data
  } catch (e: unknown) {
    const err = e as { message?: string }
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refresh()
  // 30s 轮询一次（首页常驻场景，保证 KPI 数字不会过时）
  pollTimer = setInterval(refresh, 30_000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

// KPI 用的 sparkline 数据
const deviceOnlineTrend = computed<number[]>(() =>
  (stats.value?.trends.deviceOnline24h || []).map((p: DashboardTrendPoint) => p.value),
)
const activeStreamTrend = computed<number[]>(() =>
  (stats.value?.trends.activeStream24h || []).map((p: DashboardTrendPoint) => p.value),
)

// 设备状态分布（在线 / 离线 / 未注册 → 状态色）
const deviceDist = computed(() => {
  const d = stats.value?.device
  if (!d) return []
  const arr = [
    { name: '在线', value: d.online },
    { name: '离线', value: d.offline },
    { name: '未注册', value: d.unregistered },
  ].filter((x) => x.value > 0)
  return arr
})

const deviceDistColors = ['#67C23A', '#909399', '#E6A23C']

// KPI 总览
const onlineDevices = computed(() => stats.value?.device.online ?? 0)
const onlineRate = computed(() =>
  stats.value?.device && stats.value.device.total
    ? Math.round(stats.value.device.onlineRate * 100)
    : 0,
)
const zlmOnline = computed(() => {
  const arr = stats.value?.mediaServer || []
  return arr.filter((m) => m.status === 1).length
})
const zlmTotal = computed(() => stats.value?.mediaServer.length ?? 0)
const activeStreams = computed(() => stats.value?.stream.live ?? 0)
const alertToday = computed(() => stats.value?.alert.todayCount ?? 0)
const alertUnread = computed(() => stats.value?.alert.unreadCount ?? 0)

// 较 1h 前差值
const compareDevice = computed(() => stats.value?.compareHour.deviceOnline ?? null)
const compareStream = computed(() => stats.value?.compareHour.activeStream ?? null)
</script>

<template>
  <div class="dashboard">
    <GreetingBar
      v-if="stats"
      :username="stats.greeting.username"
      :hour="stats.greeting.hour"
      :unread-count="alertUnread"
    />

    <!-- 4 个 KPI 卡 -->
    <el-row v-if="stats" :gutter="20" class="kpi-row">
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="在线设备"
          :value="onlineDevices"
          unit="台"
          :trend="deviceOnlineTrend"
          :diff="compareDevice"
          color="#409EFF"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="ZLM 节点"
          :value="`${zlmOnline}/${zlmTotal}`"
          :trend="activeStreamTrend"
          :diff="null"
          color="#17A2B8"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="活跃流"
          :value="activeStreams"
          unit="路"
          :trend="activeStreamTrend"
          :diff="compareStream"
          color="#E6A23C"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="今日告警"
          :value="alertToday"
          unit="条"
          :trend="[]"
          :diff="null"
          color="#F56C6C"
        />
      </el-col>
    </el-row>

    <!-- 24h 趋势 -->
    <div v-if="stats" class="block-card">
      <div class="block-header">
        <span class="block-title">24h 趋势</span>
        <span class="block-sub">设备在线 · 活跃流</span>
      </div>
      <TrendChart
        :device-online="stats.trends.deviceOnline24h"
        :active-stream="stats.trends.activeStream24h"
      />
    </div>

    <!-- 设备状态分布 + 厂商分布 -->
    <el-row v-if="stats" :gutter="20" class="block-row">
      <el-col :xs="24" :md="12">
        <div class="block-card">
          <div class="block-header">
            <span class="block-title">设备状态分布</span>
            <span class="block-sub">在线率 {{ onlineRate }}%</span>
          </div>
          <DonutChart
            :data="deviceDist"
            :center-value="stats.device.total"
            center-label="总设备"
            :colors="deviceDistColors"
            empty-text="暂无设备"
          />
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="block-card">
          <div class="block-header">
            <span class="block-title">厂商分布</span>
            <span class="block-sub">按 manufacturer 聚合</span>
          </div>
          <DonutChart
            :data="stats.manufacturerDist"
            empty-text="暂无厂商数据"
          />
        </div>
      </el-col>
    </el-row>

    <!-- ZLM 节点状态网格 -->
    <div v-if="stats && stats.mediaServer.length" class="block-card">
      <div class="block-header">
        <span class="block-title">ZLM 节点</span>
        <span class="block-sub">{{ zlmOnline }} / {{ zlmTotal }} 在线</span>
      </div>
      <div class="zlm-grid">
        <div
          v-for="m in stats.mediaServer"
          :key="m.serverId"
          class="zlm-card"
          :class="`status-${m.status}`"
        >
          <div class="zlm-card-head">
            <span class="zlm-dot" />
            <span class="zlm-name">{{ m.name || m.serverId }}</span>
            <span class="zlm-status">{{ m.status === 1 ? '在线' : '离线' }}</span>
          </div>
          <div class="zlm-card-body">
            <div class="zlm-load-bar">
              <div
                class="zlm-load-fill"
                :style="{ width: Math.min(100, m.loadScore * 10) + '%' }"
              />
            </div>
            <div class="zlm-meta">
              <span>负载 {{ m.loadScore }}</span>
              <span>{{ m.hostIp }}:{{ m.httpPort }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近告警 -->
    <div v-if="stats" class="block-card">
      <div class="block-header">
        <span class="block-title">最近告警</span>
        <span class="block-sub">最新 10 条</span>
      </div>
      <AlertList :alerts="stats.alert.recent" />
    </div>

    <!-- 快捷入口 -->
    <QuickActions />

    <!-- 加载/错误提示 -->
    <div v-if="error" class="error-tip">{{ error }} <el-button link @click="refresh">重试</el-button></div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--ds-gap);
}

.kpi-row {
  margin-bottom: 0 !important;
}

.block-card {
  background: var(--el-bg-color);
  border-radius: var(--ds-card-radius);
  box-shadow: var(--ds-card-shadow);
  padding: var(--ds-card-padding);
}

.block-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.block-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.block-row {
  margin-bottom: 0 !important;
}

.zlm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.zlm-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
  transition: border-color 0.15s ease;
}

.zlm-card:hover {
  border-color: var(--ds-cat-1);
}

.zlm-card.status-0 {
  opacity: 0.72;
}

.zlm-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.zlm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ds-neutral);
  flex-shrink: 0;
}

.status-1 .zlm-dot {
  background: var(--ds-success);
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.18);
}

.zlm-name {
  font-weight: 500;
  font-size: 13px;
  color: var(--el-text-color-primary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zlm-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.status-1 .zlm-status {
  background: rgba(103, 194, 58, 0.1);
  color: var(--ds-success);
}

.zlm-load-bar {
  height: 4px;
  background: var(--el-fill-color-light);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 6px;
}

.zlm-load-fill {
  height: 100%;
  background: var(--ds-cat-2);
  border-radius: 2px;
  transition: width 0.4s ease;
}

.zlm-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.error-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 12px 0;
}
</style>
