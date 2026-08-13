/**
 * Catalog 同步进度 store
 * 纯 HTTP 轮询 GET /sync-status，每 2s 由弹窗主动拉一次。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDeviceSyncStatusApi, parseSyncStatus } from '@/api/gb28181'
import type { GbSyncStatus } from '@/types/api'

export const useSyncStore = defineStore('sync', () => {
  /** deviceId → 当前进度快照（最近一次更新为准） */
  const statusByDevice = ref<Record<string, GbSyncStatus>>({})

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

  return { statusByDevice, get, fetch }
})
