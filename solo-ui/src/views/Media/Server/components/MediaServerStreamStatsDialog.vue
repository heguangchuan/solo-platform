<script setup lang="ts">
/**
 * 节点流统计弹窗（节点管理"流统计"按钮的下钻视图）
 *
 * <p>布局：顶部 5 个 KPI（国标流 / 推流 / 拉流 / ONVIF 流 / 总数）+ 节点身份信息；
 * 下方分 4 个 Tab 列四类会话明细（国标点播/回放 / 第三方推流 / 拉流代理 / ONVIF 实时点播）。</p>
 *
 * <p>复用 {@code MediaServerDetailDialog} 同样的父持有 ref 模式：父组件调 {@code open(row)}，
 * 弹窗内部按需调 {@code GET /api/media-server/{serverId}/stream-stats} 拉明细。</p>
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { GbMediaServer, MediaServerStreamStats } from '@/types/api'
import { getMediaServerStreamStatsApi } from '@/api/gb28181'

const visible = ref(false)
const loading = ref(false)
const activeTab = ref('gb')
const data = ref<MediaServerStreamStats | null>(null)

async function open(row: GbMediaServer) {
  visible.value = true
  data.value = null
  activeTab.value = 'gb'
  await load(row.serverId)
}

async function load(serverId: string) {
  loading.value = true
  try {
    const res = await getMediaServerStreamStatsApi(serverId)
    data.value = res.data
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载流统计失败')
    visible.value = false
  } finally {
    loading.value = false
  }
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
      <template v-if="data">
        <div class="node-meta">
          <span class="name">{{ data.serverName || data.serverId }}</span>
          <el-tag :type="data.status === 1 ? 'success' : 'danger'" size="small">
            {{ statusText(data.status) }}
          </el-tag>
          <span class="host">{{ data.hostIp }}:{{ data.httpPort }}</span>
        </div>
        <div class="kpi-row">
          <div class="kpi-tile">
            <div class="kpi-num">{{ data.gbStreamCount }}</div>
            <div class="kpi-label">国标流</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ data.pushStreamCount }}</div>
            <div class="kpi-label">推流数</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ data.proxyStreamCount }}</div>
            <div class="kpi-label">拉流数</div>
          </div>
          <div class="kpi-tile">
            <div class="kpi-num">{{ data.onvifStreamCount }}</div>
            <div class="kpi-label">ONVIF 流</div>
          </div>
          <div class="kpi-tile kpi-total">
            <div class="kpi-num">{{ data.total }}</div>
            <div class="kpi-label">总数</div>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <!-- 国标点播 / 回放 -->
          <el-tab-pane :label="`国标会话 (${data.gbStreamCount})`" name="gb">
            <el-table
              :data="data.gbSessions"
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
          </el-tab-pane>

          <!-- 第三方推流 -->
          <el-tab-pane :label="`推流 (${data.pushStreamCount})`" name="push">
            <el-table
              :data="data.pushes"
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
              <el-table-column prop="aliveSecond" label="存活(秒)" align="center" />
              <el-table-column prop="pushTime" label="推流开始时间" />
            </el-table>
          </el-tab-pane>

          <!-- 拉流代理 -->
          <el-tab-pane :label="`拉流代理 (${data.proxyStreamCount})`" name="proxy">
            <el-table
              :data="data.proxies"
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
          </el-tab-pane>

          <!-- ONVIF 实时点播 -->
          <el-tab-pane :label="`ONVIF 会话 (${data.onvifStreamCount})`" name="onvif">
            <el-table
              :data="data.onvifSessions"
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
</style>
