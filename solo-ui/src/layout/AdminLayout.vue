<script setup lang="ts">
import LayoutSidebar from './components/LayoutSidebar.vue'
import LayoutHeader from './components/LayoutHeader.vue'
</script>

<template>
  <el-container class="layout-container" direction="horizontal">
    <LayoutSidebar />
    <el-container class="layout-body" direction="vertical">
      <LayoutHeader />
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
  width: 100vw;
}

.layout-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.layout-main {
  background-color: var(--el-bg-color-page);
  padding: 20px;
}
</style>
