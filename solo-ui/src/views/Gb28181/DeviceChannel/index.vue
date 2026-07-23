<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import { ref } from 'vue'
import ChannelListTable from '@/views/Gb28181/Device/components/ChannelListTable.vue'
import ChannelBatchAddDialog from './components/ChannelBatchAddDialog.vue'

const tableRef = ref<InstanceType<typeof ChannelListTable> | null>(null)
const addDialogRef = ref<InstanceType<typeof ChannelBatchAddDialog> | null>(null)

function handleExternalAdd() {
  addDialogRef.value?.open()
}

function handleRefresh() {
  tableRef.value?.fetchData()
}
</script>

<template>
  <div>
    <ChannelListTable
      ref="tableRef"
      :show-device-column="true"
      :external-add="true"
      @external-add="handleExternalAdd"
    />
    <ChannelBatchAddDialog ref="addDialogRef" @refresh="handleRefresh" />
  </div>
</template>
