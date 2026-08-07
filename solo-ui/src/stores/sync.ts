/**
 * Catalog 同步进度 store
 * 整合 WebSocket GB_SYNC_PROGRESS 推送 + HTTP /sync-status 拉取
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { wsClient } from '@/utils/websocket'
import { getDeviceSyncStatusApi, parseSyncStatus } from '@/api/gb28181'
import type { GbSyncStatus } from '@/types/api'

const WS_MSG_TYPE = 'GB_SYNC_PROGRESS'

export const useSyncStore = defineStore('sync', () => {
  /** deviceId → 当前进度快照（最近一次更新为准） */
  const statusByDevice = ref<Record<string, GbSyncStatus>>({})
  const isInitialized = ref(false)

  function init() {
    if (isInitialized.value) return
    // 确保 ws 已连接（依赖 message store，但保险起见自己 connect 一次）
    wsClient.connect()
    wsClient.subscribe((msg: any) => {
      if (msg && msg.type === WS_MSG_TYPE && msg.data) {
        const p = msg.data as GbSyncStatus
        statusByDevice.value[p.deviceId] = p
      }
    })
    isInitialized.value = true
  }

  function get(deviceId: string): GbSyncStatus | undefined {
    return statusByDevice.value[deviceId]
  }

  /** HTTP 拉一次（用于 dialog 打开时初始化进度）。失败/无数据 → IDLE */
  async function fetch(deviceId: string): Promise<GbSyncStatus> {
    try {
      const res = await getDeviceSyncStatusApi(deviceId)
      const parsed = parseSyncStatus(res.data, deviceId)
      statusByDevice.value[deviceId] = parsed
      return parsed
    } catch {
      const idle: GbSyncStatus = {
        deviceId,
        status: 'IDLE',
        received: 0,
        total: 0,
        percent: 0,
        startedAtMs: 0,
        lastUpdateAtMs: 0,
        duplicateCount: 0,
      }
      statusByDevice.value[deviceId] = idle
      return idle
    }
  }

  return { statusByDevice, isInitialized, init, get, fetch }
})
