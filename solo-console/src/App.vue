<template>
  <router-view />
  <Toaster />
  <ConfirmDialog />
</template>

<script setup lang="ts">
import Toaster from '@/components/Toaster.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
</script>
