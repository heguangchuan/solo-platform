<script setup lang="ts">
/**
 * 流媒体节点详情弹窗（只读）
 * <p>父组件持有 ref，调 {@code open(server)} 显示全字段。</p>
 */
import { ref } from 'vue'
import type { GbMediaServer } from '@/types/api'

const visible = ref(false)
const detail = ref<GbMediaServer | null>(null)

function open(server: GbMediaServer) {
  detail.value = server
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="节点详情"
    width="900px"
    top="8vh"
    :close-on-click-modal="false"
  >
    <el-descriptions v-if="detail" :column="2" border :label-width="100">
      <el-descriptions-item label="节点标识" :span="2">{{ detail.serverId }}</el-descriptions-item>
      <el-descriptions-item label="节点名称" :span="2">{{ detail.name || '—' }}</el-descriptions-item>

      <el-descriptions-item label="节点状态">
        <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
          {{ detail.status === 1 ? '在线' : '离线' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="管理地址">{{ detail.hostIp }}</el-descriptions-item>
      <el-descriptions-item label="管理端口">{{ detail.httpPort }}</el-descriptions-item>
      <el-descriptions-item label="接口密钥">{{ detail.secret || '—' }}</el-descriptions-item>
      <el-descriptions-item label="播放地址">{{ detail.streamIp || '—' }}</el-descriptions-item>
      <el-descriptions-item label="播放端口">{{ detail.streamHttpPort && detail.streamHttpPort > 0 ? detail.streamHttpPort : '—（同管理端口）' }}</el-descriptions-item>
      <el-descriptions-item label="推流端口">
        {{ detail.rtpPortStart }} - {{ detail.rtpPortEnd }}
      </el-descriptions-item>
      <el-descriptions-item label="RTSP端口">{{ detail.rtspPort && detail.rtspPort > 0 ? detail.rtspPort : '—（未启用）' }}</el-descriptions-item>
      <el-descriptions-item label="实时端口">{{ detail.rtcPort && detail.rtcPort > 0 ? detail.rtcPort : '—（未启用）' }}</el-descriptions-item>
      <el-descriptions-item label="最近心跳">{{ detail.hookAliveTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ detail.createTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ detail.updateTime || '—' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>
