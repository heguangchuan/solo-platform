<script setup lang="ts">
/**
 * 国标设备详情弹窗（只读）
 * <p>父组件持有 ref，调 {@code open(device)} 显示全字段。</p>
 */
import { ref } from 'vue'
import type { GbDevice } from '@/types/api'

const visible = ref(false)
const detail = ref<GbDevice | null>(null)

/** 数据来源文案 */
const sourceMap: Record<number, string> = { 1: '系统新增', 2: '表格导入', 3: '设备注册', 4: '级联平台(历史)' }
function sourceText(source?: number) {
  return source == null ? '—' : sourceMap[source] ?? '—'
}

function open(device: GbDevice) {
  detail.value = device
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="设备详情"
    width="900px"
    top="8vh"
    :close-on-click-modal="false"
  >
    <el-descriptions v-if="detail" :column="2" border :label-width="100">
      <!-- 国标 20 位编码独占一行，避免与其他字段挤同行造成换行 -->
      <el-descriptions-item label="设备编码" :span="2">{{ detail.deviceId }}</el-descriptions-item>
      <el-descriptions-item label="设备名称" :span="2">{{ detail.name || '—' }}</el-descriptions-item>

      <el-descriptions-item label="设备厂商">{{ detail.manufacturer || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设备型号">{{ detail.model || '—' }}</el-descriptions-item>
      <el-descriptions-item label="固件版本">{{ detail.firmware || '—' }}</el-descriptions-item>
      <el-descriptions-item label="设备状态">
        <el-tag v-if="detail.status === 2" type="info" size="small">未注册</el-tag>
        <el-tag v-else :type="detail.status === 1 ? 'success' : 'danger'" size="small">
          {{ detail.status === 1 ? '在线' : '离线' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="数据来源">
        <el-tag size="small">{{ sourceText(detail.source) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="网络地址">{{ detail.hostAddress || '—' }}</el-descriptions-item>
      <el-descriptions-item label="收流地址">{{ detail.recvIp || '—' }}</el-descriptions-item>
      <el-descriptions-item label="传输协议">{{ detail.transport || '—' }}</el-descriptions-item>
      <el-descriptions-item label="本机地址">{{ detail.localIp || '—' }}</el-descriptions-item>
      <el-descriptions-item label="信令域名">{{ detail.sipDomain || '—' }}</el-descriptions-item>
      <el-descriptions-item label="字符编码">{{ detail.charset || '—' }}</el-descriptions-item>
      <el-descriptions-item label="注册有效">{{ detail.expires ? detail.expires + ' 秒' : '—' }}</el-descriptions-item>
      <el-descriptions-item label="最近注册">{{ detail.registerTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="最近心跳">{{ detail.keepaliveTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="目录同步">{{ detail.lastCatalogSyncTime || '—' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>
