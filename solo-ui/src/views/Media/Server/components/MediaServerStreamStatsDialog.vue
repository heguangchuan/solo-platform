<script setup lang="ts">
/**
 * 节点流统计弹窗（节点管理"流统计"按钮的下钻视图）
 *
 * <p>布局：顶部 5 个 KPI（国标流 / 推流 / 拉流 / ONVIF 流 / 总数）+ 节点身份信息；
 * 下方分 4 个 Tab 列四类会话明细（国标点播/回放 / 第三方推流 / 拉流代理 / ONVIF 实时点播）。</p>
 *
 * <p>加载策略（按需懒加载，避免一次性 4 类明细大 JSON）：
 * <ol>
 *   <li>首屏只发 {@code GET /stream-stats/summary} 拿 KPI，~200B，秒回</li>
 *   <li>summary 拿到后自动拉一次默认 active Tab 的明细，避免用户多一次点击</li>
 *   <li>其余 Tab 在用户切过去时才发请求，已加载过的 Tab 不重发</li>
 *   <li>{@code lazy} 让明细表格的 DOM 也只在首次激活时创建</li>
 * </ol>
 * </p>
 *
 * <p>复用父持有 ref 模式：父组件调 {@code open(row)}，弹窗内部负责拉数据。</p>
 */
import { ref, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import type {
  GbMediaServer,
  MediaServerStreamStatsSummary,
  StreamStatsGbItem,
  StreamStatsOnvifItem,
  StreamStatsProxyItem,
  StreamStatsPushItem,
  StreamStatsTabKey,
} from '@/types/api'
import {
  getMediaServerStreamStatsSummaryApi,
  getMediaServerStreamStatsDetailApi,
} from '@/api/gb28181'

const visible = ref(false)
const loading = ref(false)
const activeTab = ref<StreamStatsTabKey>('gb')
const summary = ref<MediaServerStreamStatsSummary | null>(null)

/** 默认激活的 Tab：弹窗打开后自动加载该 Tab 的明细，避免用户多一次点击 */
const DEFAULT_TAB: StreamStatsTabKey = 'gb'

// 4 个 Tab 的明细数据 / 加载状态 / 已加载标志
const gbSessions = ref<StreamStatsGbItem[]>([])
const pushItems = ref<StreamStatsPushItem[]>([])
const proxyItems = ref<StreamStatsProxyItem[]>([])
const onvifSessions = ref<StreamStatsOnvifItem[]>([])

const tabLoading = ref<Record<StreamStatsTabKey, boolean>>({
  gb: false,
  push: false,
  proxy: false,
  onvif: false,
})
const tabLoaded = ref<Record<StreamStatsTabKey, boolean>>({
  gb: false,
  push: false,
  proxy: false,
  onvif: false,
})

/** 实时刷新：默认开，打开弹窗即每 5s 自动拉 summary + 当前 Tab 明细 */
const realtime = ref(true)
const REFRESH_INTERVAL = 5000  // 5 秒
const lastRefreshAt = ref<Date | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null
/** 静默模式：true 时不弹 loading / 错误吐司（轮询场景用，避免每 5s 弹一次错误） */
let silentRefresh = false

async function open(row: GbMediaServer) {
  visible.value = true
  // 重置所有状态
  summary.value = null
  activeTab.value = DEFAULT_TAB
  gbSessions.value = []
  pushItems.value = []
  proxyItems.value = []
  onvifSessions.value = []
  tabLoaded.value = { gb: false, push: false, proxy: false, onvif: false }
  tabLoading.value = { gb: false, push: false, proxy: false, onvif: false }
  await loadSummary(row.serverId)
  // 默认 Tab 自动加载明细（loadSummary 失败时已关弹窗，summary.value 为空则跳过）
  if (summary.value) {
    await loadTab(DEFAULT_TAB)
  }
}

async function loadSummary(serverId: string) {
  if (!silentRefresh) loading.value = true
  try {
    const res = await getMediaServerStreamStatsSummaryApi(serverId)
    summary.value = res.data
  } catch (e) {
    if (!silentRefresh) {
      ElMessage.error(e instanceof Error ? e.message : '加载流统计失败')
      visible.value = false
    } else {
      console.warn('[流统计] 静默刷新失败', e)
    }
  } finally {
    if (!silentRefresh) loading.value = false
  }
}

/**
 * 切 Tab 时触发：已加载过的跳过；未加载的拉对应明细。
 * 同时用 {@code el-tab-pane lazy} 防止未激活 Tab 的 DOM 被创建。
 * 轮询场景下 force=true 强制刷新当前 Tab（无论是否已加载）。
 */
async function onTabChange(name: string | number | undefined) {
  if (typeof name !== 'string') return
  const key = name as StreamStatsTabKey
  await loadTab(key, false)
}

async function loadTab(key: StreamStatsTabKey, force = false) {
  if (!summary.value) return
  if (!force && (tabLoaded.value[key] || tabLoading.value[key])) return
  tabLoading.value[key] = true
  try {
    const serverId = summary.value.serverId
    if (key === 'gb') {
      const res = await getMediaServerStreamStatsDetailApi<StreamStatsGbItem>(serverId, 'gb')
      gbSessions.value = res.data
    } else if (key === 'push') {
      const res = await getMediaServerStreamStatsDetailApi<StreamStatsPushItem>(serverId, 'push')
      pushItems.value = res.data
    } else if (key === 'proxy') {
      const res = await getMediaServerStreamStatsDetailApi<StreamStatsProxyItem>(serverId, 'proxy')
      proxyItems.value = res.data
    } else if (key === 'onvif') {
      const res = await getMediaServerStreamStatsDetailApi<StreamStatsOnvifItem>(serverId, 'onvif')
      onvifSessions.value = res.data
    }
    tabLoaded.value[key] = true
  } catch (e) {
    if (!silentRefresh) {
      ElMessage.error(e instanceof Error ? e.message : `加载 ${key} 明细失败`)
    } else {
      console.warn(`[流统计] 静默刷新 ${key} 明细失败`, e)
    }
  } finally {
    tabLoading.value[key] = false
  }
}

/** 手动刷新 / 轮询：summary + 当前 active Tab 强制重拉 */
async function refreshAll() {
  if (!summary.value) return
  const wasSilent = silentRefresh
  silentRefresh = true
  try {
    await loadSummary(summary.value.serverId)
    if (summary.value) {
      await loadTab(activeTab.value, true)
    }
  } finally {
    silentRefresh = wasSilent
  }
  lastRefreshAt.value = new Date()
}

function startRealtime() {
  if (refreshTimer) return
  refreshTimer = setInterval(refreshAll, REFRESH_INTERVAL)
}

function stopRealtime() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

/** 实时开关变化时启停轮询；弹窗关闭时强制停 */
watch(realtime, (on) => {
  if (on && visible.value) startRealtime()
  else stopRealtime()
})
watch(visible, (on) => {
  if (!on) {
    stopRealtime()
    lastRefreshAt.value = null
  } else if (realtime.value) {
    startRealtime()
  }
})
/** 组件卸载兜底 */
onUnmounted(() => stopRealtime())

function formatTime(d: Date | null): string {
  if (!d) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function statusText(s?: number) {
  return s === 1 ? '在线' : '离线'
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="节点流统计"
    width="65%"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div v-loading="loading">
      <!-- 节点身份 + 5 KPI -->
      <template v-if="summary">
        <div class="node-meta">
          <span class="name">{{ summary.serverName || summary.serverId }}</span>
          <el-tag :type="summary.status === 1 ? 'success' : 'danger'" size="small">
            {{ statusText(summary.status) }}
          </el-tag>
          <span class="host">{{ summary.hostIp }}:{{ summary.httpPort }}</span>
          <el-divider direction="vertical" />
          <!-- 实时刷新开关（默认关；开启后每 5s 自动拉 summary + 当前 Tab 明细） -->
          <el-switch
            v-model="realtime"
            active-text="实时"
            inactive-text="实时"
            inline-prompt
            size="small"
          />
          <span v-if="lastRefreshAt" class="refresh-time">最后刷新 {{ formatTime(lastRefreshAt) }}</span>
          <el-button :icon="Refresh" size="small" plain @click="refreshAll">刷新</el-button>
        </div>
        <div class="kpi-row">
          <div class="kpi-tile">
            <div class="kpi-num">{{ summary.gbStreamCount }}</div>
            <div class="kpi-label">国标流</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ summary.pushStreamCount }}</div>
            <div class="kpi-label">推流数</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ summary.proxyStreamCount }}</div>
            <div class="kpi-label">拉流数</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ summary.onvifStreamCount }}</div>
            <div class="kpi-label">ONVIF 流</div>
          </div>
          <div class="kpi-tile kpi-total">
            <div class="kpi-num">{{ summary.total }}</div>
            <div class="kpi-label">总数</div>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="onTabChange">
          <!-- 国标点播 / 回放 -->
          <el-tab-pane :label="`国标会话 (${summary.gbStreamCount})`" name="gb" lazy>
            <div v-loading="tabLoading.gb">
              <el-table
                v-if="tabLoaded.gb"
                :data="gbSessions"
                size="small"
                border
                stripe
                fit
                style="width: 100%"
                max-height="360"
                empty-text="无国标会话"
              >
                <el-table-column prop="streamId" label="streamId" show-overflow-tooltip />
                <el-table-column prop="sessionType" label="类型" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.sessionType === 'PLAYBACK' ? 'warning' : 'primary'">
                      {{ row.sessionType || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" align="center">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.status === 'READY' ? 'success' : (row.status === 'INVITING' ? 'warning' : 'info')"
                    >
                      {{ row.status || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="开始时间" />
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 第三方推流 -->
          <el-tab-pane :label="`推流 (${summary.pushStreamCount})`" name="push" lazy>
            <div v-loading="tabLoading.push">
              <el-table
                v-if="tabLoaded.push"
                :data="pushItems"
                size="small"
                border
                stripe
                fit
                style="width: 100%"
                max-height="360"
                empty-text="无第三方推流"
              >
                <el-table-column prop="app" label="app" show-overflow-tooltip />
                <el-table-column prop="stream" label="stream" show-overflow-tooltip />
                <el-table-column prop="originType" label="协议" align="center" />
                <el-table-column prop="pushTime" label="推流开始时间" />
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 拉流代理 -->
          <el-tab-pane :label="`拉流代理 (${summary.proxyStreamCount})`" name="proxy" lazy>
            <div v-loading="tabLoading.proxy">
              <el-table
                v-if="tabLoaded.proxy"
                :data="proxyItems"
                size="small"
                border
                stripe
                fit
                style="width: 100%"
                max-height="360"
                empty-text="无拉流代理"
              >
                <el-table-column prop="app" label="app" show-overflow-tooltip />
                <el-table-column prop="stream" label="stream" show-overflow-tooltip />
                <el-table-column prop="srcUrl" label="源地址" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                      {{ row.status === 1 ? '运行中' : '未启动' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- ONVIF 实时点播 -->
          <el-tab-pane :label="`ONVIF 会话 (${summary.onvifStreamCount})`" name="onvif" lazy>
            <div v-loading="tabLoading.onvif">
              <el-table
                v-if="tabLoaded.onvif"
                :data="onvifSessions"
                size="small"
                border
                stripe
                fit
                style="width: 100%"
                max-height="360"
                empty-text="无 ONVIF 会话"
              >
                <el-table-column prop="streamId" label="streamId" show-overflow-tooltip />
                <el-table-column prop="deviceId" label="设备 ID" align="center" />
                <el-table-column prop="proxyKey" label="proxyKey" show-overflow-tooltip />
                <el-table-column prop="startTime" label="开始时间" />
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.node-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.node-meta .name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.node-meta .host {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi-tile {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px 12px;
  text-align: center;
}
.kpi-total {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}
.kpi-num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
  color: var(--el-text-color-primary);
}
.kpi-label {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-tabs {
  margin-top: 4px;
}

.node-meta .refresh-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  margin: 0 4px;
}
</style>
