<script lang="ts">
export default { name: 'Gb28181DeviceChannel' }
</script>
<script setup lang="ts">
import ChannelListTable from '@/views/Gb28181/Device/components/ChannelListTable.vue'
</script>

<template>
  <div>
    <ChannelListTable :show-device-column="true" />
  </div>
</template>

<style scoped>
</style>
