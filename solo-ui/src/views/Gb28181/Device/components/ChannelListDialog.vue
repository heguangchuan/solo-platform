<script setup lang="ts">
/**
 * 通道列表弹窗（可复用组件）
 * <p>父组件持有 ref，调 {@code open(deviceId)} 显示指定设备的通道列表。</p>
 */
import { ref } from 'vue'
import ChannelListTable from './ChannelListTable.vue'

const visible = ref(false)
const currentDeviceId = ref('')
const tableRef = ref<InstanceType<typeof ChannelListTable> | null>(null)

function open(deviceId: string) {
  currentDeviceId.value = deviceId
  visible.value = true
  // 表格组件会自动监听 deviceId 变化刷新数据
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="通道列表"
    width="70%"
    :close-on-click-modal="false"
  >
    <ChannelListTable ref="tableRef" :device-id="currentDeviceId" :show-device-column="false" />
  </el-dialog>
</template>
