<template>
  <div class="h-full overflow-auto p-5">
    <!-- 页头 -->
    <div class="mb-4">
      <div class="flex items-center gap-2">
        <h2 class="text-lg font-semibold">AOP测试</h2>
        <Badge variant="secondary" class="bg-amber-500/10 text-amber-600 dark:text-amber-500">后端切面防护</Badge>
      </div>
      <p class="mt-1 text-[13px] text-muted-foreground">
        验证服务端切面能力：接口限流、防重复提交、XSS 注入清洗。
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
          <TabsContent value="rateLimit" class="mt-0">
            <RateLimitPanel />
          </TabsContent>
          <TabsContent value="repeatSubmit" class="mt-0">
            <RepeatSubmitPanel />
          </TabsContent>
          <TabsContent value="xss" class="mt-0">
            <XssPanel />
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
import RateLimitPanel from '@/features/test/RateLimitPanel.vue'
import RepeatSubmitPanel from '@/features/test/RepeatSubmitPanel.vue'
import XssPanel from '@/features/test/XssPanel.vue'

const active = ref('rateLimit')
const tabs = [
  { value: 'rateLimit', label: '限流测试' },
  { value: 'repeatSubmit', label: '防重复提交' },
  { value: 'xss', label: 'XSS注入' },
]
</script>
