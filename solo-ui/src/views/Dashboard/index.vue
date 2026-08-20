<script setup lang="ts">
/**
 * 首页（C 方案：现代极简）
 *
 * <h3>布局</h3>
 * <ol>
 *   <li>顶部问候条（GreetingBar）</li>
 *   <li>4 个 KPI 卡片横排：在线设备 / ZLM 节点 / 活跃流 / 告警</li>
 *   <li>设备状态分布 + 厂商分布 双 donut</li>
 *   <li>流媒体节点网格（带国标/推流/拉流统计）</li>
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
import DonutChart from './components/DonutChart.vue'

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
const pushStreams = computed(() => stats.value?.stream.push ?? 0)
const proxyStreams = computed(() => stats.value?.stream.proxy ?? 0)
// 国标流 = 活跃流 - 推流 - 拉流（Redis ZSet 总 score 包含三类，扣除后即 GB 点播+回放）
const gbStreams = computed(() => {
  const s = stats.value?.stream
  if (!s) return 0
  return Math.max(0, s.live - s.push - s.proxy)
})
const alertToday = computed(() => stats.value?.alert.todayCount ?? 0)
const alertUnread = computed(() => stats.value?.alert.unreadCount ?? 0)

// 各节点最大流数（取所有节点中三类的最大峰值，用于流条比例尺，避免单节点值太大把条撑爆）
const maxNodeStream = computed(() => {
  const arr = stats.value?.mediaServer || []
  let m = 0
  for (const s of arr) {
    if ((s.gbStream || 0) > m) m = s.gbStream || 0
    if ((s.pushStream || 0) > m) m = s.pushStream || 0
    if ((s.proxyStream || 0) > m) m = s.proxyStream || 0
  }
  // 至少 1 避免除 0
  return Math.max(m, 1)
})
// 给单条流条用：0 路返回 0%，最大路数占满 100%（视觉上同类条间能直观比较）
function barWidth(n: number): string {
  if (!n) return '0%'
  return Math.min(100, Math.round((n / maxNodeStream.value) * 100)) + '%'
}
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
          color="#409EFF"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="ZLM 节点"
          :value="`${zlmOnline}/${zlmTotal}`"
          :trend="activeStreamTrend"
          color="#17A2B8"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="活跃流"
          :value="activeStreams"
          unit="路"
          :trend="activeStreamTrend"
          color="#E6A23C"
        />
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <KpiCard
          title="今日告警"
          :value="alertToday"
          unit="条"
          :trend="[]"
          color="#F56C6C"
        />
      </el-col>
    </el-row>

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

    <!-- 流媒体节点 -->
    <div v-if="stats && stats.mediaServer.length" class="block-card media-block">
      <div class="block-header media-header">
        <div class="media-header-main">
          <span class="block-title">流媒体节点</span>
          <span class="block-sub">{{ zlmOnline }} / {{ zlmTotal }} 在线</span>
        </div>
        <div class="stream-stats">
          <span class="stream-stat">
            <i class="dot dot-gb" />
            <span class="stream-stat-label">国标流</span>
            <span class="stream-stat-value">{{ gbStreams }}</span>
            <span class="stream-stat-unit">路</span>
          </span>
          <span class="stream-stat">
            <i class="dot dot-push" />
            <span class="stream-stat-label">推流</span>
            <span class="stream-stat-value">{{ pushStreams }}</span>
            <span class="stream-stat-unit">路</span>
          </span>
          <span class="stream-stat">
            <i class="dot dot-proxy" />
            <span class="stream-stat-label">拉流</span>
            <span class="stream-stat-value">{{ proxyStreams }}</span>
            <span class="stream-stat-unit">路</span>
          </span>
        </div>
      </div>
      <div class="zlm-grid">
        <div
          v-for="m in stats.mediaServer"
          :key="m.serverId"
          class="zlm-card"
          :class="`status-${m.status}`"
        >
          <div class="zlm-card-head">
            <span class="zlm-name">{{ m.name || m.serverId }}</span>
            <span class="zlm-status-badge">{{ m.status === 1 ? '在线' : '离线' }}</span>
          </div>
          <div class="zlm-host">{{ m.hostIp }}:{{ m.httpPort }}</div>
          <!-- 每节点的三类流分布 -->
          <div class="zlm-stream-bars">
            <div class="zlm-bar-row">
              <span class="zlm-bar-label"><i class="dot dot-gb" />国标</span>
              <div class="zlm-bar-track">
                <div
                  class="zlm-bar-fill bar-gb"
                  :style="{ width: barWidth(m.gbStream || 0) }"
                />
              </div>
              <span class="zlm-bar-num">{{ m.gbStream || 0 }}</span>
            </div>
            <div class="zlm-bar-row">
              <span class="zlm-bar-label"><i class="dot dot-push" />推流</span>
              <div class="zlm-bar-track">
                <div
                  class="zlm-bar-fill bar-push"
                  :style="{ width: barWidth(m.pushStream || 0) }"
                />
              </div>
              <span class="zlm-bar-num">{{ m.pushStream || 0 }}</span>
            </div>
            <div class="zlm-bar-row">
              <span class="zlm-bar-label"><i class="dot dot-proxy" />拉流</span>
              <div class="zlm-bar-track">
                <div
                  class="zlm-bar-fill bar-proxy"
                  :style="{ width: barWidth(m.proxyStream || 0) }"
                />
              </div>
              <span class="zlm-bar-num">{{ m.proxyStream || 0 }}</span>
            </div>
          </div>
          <div class="zlm-load">
            <div class="zlm-load-label">
              <span>总负载</span>
              <span class="zlm-load-num">{{ m.loadScore }}</span>
            </div>
            <div class="zlm-load-bar">
              <div
                class="zlm-load-fill"
                :class="m.loadScore >= 8 ? 'high' : m.loadScore >= 5 ? 'mid' : 'low'"
                :style="{ width: Math.min(100, m.loadScore * 10) + '%' }"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

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

/* ==================== 流媒体节点 ==================== */

.media-header {
  align-items: flex-start;
  gap: 16px;
}

.media-header-main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-shrink: 0;
}

.stream-stats {
  display: flex;
  gap: 24px;
  margin-left: auto;
  padding: 4px 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.stream-stat {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stream-stat-label {
  font-weight: 500;
}

.stream-stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.stream-stat-unit {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  align-self: center;
}
.dot-gb { background: #409EFF; }
.dot-push { background: #67C23A; }
.dot-proxy { background: #E6A23C; }

.zlm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.zlm-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px 16px;
  background: var(--el-bg-color);
  transition: all 0.18s ease;
  position: relative;
  overflow: hidden;
}

.zlm-card::before {
  /* 状态色条 */
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--el-border-color-lighter);
  transition: background 0.18s ease;
}

.zlm-card:hover {
  border-color: var(--ds-cat-1);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.zlm-card.status-0 {
  opacity: 0.7;
}

.zlm-card.status-0::before {
  background: var(--el-text-color-placeholder);
}

.zlm-card.status-1::before {
  background: var(--ds-success);
}

.zlm-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 8px;
}

.zlm-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.zlm-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.status-1 .zlm-status-badge {
  background: rgba(103, 194, 58, 0.1);
  color: var(--ds-success);
}

.status-0 .zlm-status-badge {
  background: rgba(144, 147, 153, 0.1);
  color: var(--el-text-color-placeholder);
}

.zlm-host {
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
  letter-spacing: 0.2px;
}

.zlm-stream-bars {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.zlm-bar-row {
  display: grid;
  grid-template-columns: 50px 1fr 24px;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.zlm-bar-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
}

.zlm-bar-track {
  height: 6px;
  background: var(--el-bg-color);
  border-radius: 3px;
  overflow: hidden;
}

.zlm-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
  min-width: 2px;
}

.zlm-bar-fill.bar-gb { background: linear-gradient(90deg, #409EFF, #66B1FF); }
.zlm-bar-fill.bar-push { background: linear-gradient(90deg, #67C23A, #85CE61); }
.zlm-bar-fill.bar-proxy { background: linear-gradient(90deg, #E6A23C, #EEBE77); }

.zlm-bar-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.zlm-load {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.zlm-load-label {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.zlm-load-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.zlm-load-bar {
  height: 5px;
  background: var(--el-fill-color-light);
  border-radius: 3px;
  overflow: hidden;
}

.zlm-load-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease, background 0.4s ease;
}

.zlm-load-fill.low { background: linear-gradient(90deg, #67C23A, #95D475); }
.zlm-load-fill.mid { background: linear-gradient(90deg, #E6A23C, #EEBE77); }
.zlm-load-fill.high { background: linear-gradient(90deg, #F56C6C, #F89898); }

.error-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 12px 0;
}
</style>
