<script lang="ts">
export default { name: 'Gb28181Platform' }
</script>
<script setup lang="ts">
/**
 * 国标平台管理（Tab 容器）
 * <p>使用 el-tabs 切换"接入禁用"（黑名单：默认自由注册，仅拦截登记的国标 ID）
 * 和"上级平台"（S6.b 连接配置）。两部分各为独立组件，便于在其他场景复用。</p>
 */
import { ref } from 'vue'
import ChildPlatform from './components/ChildPlatform.vue'
import ParentPlatform from './components/ParentPlatform.vue'

const activeTab = ref('child')
</script>
<template>
  <div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="接入禁用" name="child">
        <ChildPlatform />
      </el-tab-pane>
      <el-tab-pane label="上级平台" name="parent">
        <ParentPlatform />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
