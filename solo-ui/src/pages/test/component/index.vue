<template>
  <div class="h-full overflow-auto p-5">
    <!-- 页头 -->
    <div class="mb-4">
      <div class="flex items-center gap-2">
        <h2 class="text-lg font-semibold">组件测试</h2>
        <Badge variant="secondary" class="text-[10px]">前端封装组件</Badge>
      </div>
      <p class="mt-1 text-[13px] text-muted-foreground">
        验证封装的通用能力：文件上传、字典回显、站内消息。
      </p>
    </div>

    <div class="rounded-xl border border-border bg-card">
      <Tabs v-model="active" class="flex-row gap-0">
        <TabsList
          class="h-auto w-40 shrink-0 flex-col items-stretch justify-start gap-0 border-b-0 border-r"
        >
          <TabsTrigger
            v-for="t in tabs"
            :key="t.value"
            :value="t.value"
            class="mb-0 w-full justify-start rounded-none border-b-0 border-r-2 border-transparent py-2.5 data-[state=active]:border-primary"
          >
            {{ t.label }}
          </TabsTrigger>
        </TabsList>

        <div class="min-w-0 flex-1 p-5">
          <TabsContent value="upload" class="mt-0">
            <UploadTestPanel />
          </TabsContent>
          <TabsContent value="dict" class="mt-0">
            <DictTestPanel />
          </TabsContent>
          <TabsContent value="message" class="mt-0">
            <MessageTestPanel />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Badge } from '@/components/ui/badge'
import UploadTestPanel from '@/features/test/UploadTestPanel.vue'
import DictTestPanel from '@/features/test/DictTestPanel.vue'
import MessageTestPanel from '@/features/test/MessageTestPanel.vue'

const active = ref('upload')
const tabs = [
  { value: 'upload', label: '上传组件' },
  { value: 'dict', label: '字典组件' },
  { value: 'message', label: '消息功能' },
]
</script>
