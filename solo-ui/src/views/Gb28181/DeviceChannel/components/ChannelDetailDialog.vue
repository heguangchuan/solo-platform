<script setup lang="ts">
/**
 * 国标通道详情弹窗（只读）
 * <p>父组件持有 ref，调 {@code open(channel)} 显示全字段。</p>
 */
import { ref } from 'vue'
import type { GbDeviceChannelVO } from '@/types/api'

const visible = ref(false)
const detail = ref<GbDeviceChannelVO | null>(null)

const ptzTypeMap: Record<number, string> = {
  0: '未知',
  1: '球机',
  2: '半球',
  3: '固定枪机',
  4: '遥控枪机',
}

function open(channel: GbDeviceChannelVO) {
  detail.value = channel
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="通道详情"
    width="900px"
    top="8vh"
    :close-on-click-modal="false"
  >
    <el-descriptions v-if="detail" :column="2" border :label-width="100">
      <!-- 国标 20 位编码独占一行，避免与其他字段挤同行造成换行 -->
      <el-descriptions-item label="通道编码" :span="2">{{ detail.channelId }}</el-descriptions-item>
      <el-descriptions-item label="通道名称" :span="2">{{ detail.name || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设备编码" :span="2">{{ detail.deviceId }}</el-descriptions-item>
      <el-descriptions-item label="设备名称" :span="2">{{ detail.deviceName || '—' }}</el-descriptions-item>

      <el-descriptions-item label="云台类型">{{ ptzTypeMap[detail.ptzType ?? 0] }}</el-descriptions-item>
      <el-descriptions-item label="在线状态">
        <el-tag :type="detail.status === 'ON' ? 'success' : 'info'" size="small">
          {{ detail.status }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="所属平台" :span="2">
        <el-tag :type="detail.platformId === '0000' || !detail.platformId ? 'primary' : 'warning'" size="small">
          {{ detail.platformId === '0000' || !detail.platformId ? '自有平台' : '级联 ' + detail.platformId }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="设备厂商">{{ detail.manufacturer || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设备型号">{{ detail.model || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设备归属">{{ detail.owner || '—' }}</el-descriptions-item>
      <el-descriptions-item label="行政区划">{{ detail.civilCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="所属警区">{{ detail.block || '—' }}</el-descriptions-item>
      <el-descriptions-item label="父级节点">{{ detail.parentId || '—' }}</el-descriptions-item>
      <el-descriptions-item label="地理经度">{{ detail.longitude ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="地理纬度">{{ detail.latitude ?? '—' }}</el-descriptions-item>

      <el-descriptions-item label="安装地址" :span="2">{{ detail.address || '—' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>
