<template>
  <div class="space-y-6">
    <div>
      <h4 class="text-sm font-medium">字典下拉（useDict + Select）</h4>
      <p class="mt-0.5 text-xs text-muted-foreground">
        字典类型 <code class="rounded bg-muted px-1 font-mono text-[11px]">sys_file_types</code>，验证单选、回显与禁用。
      </p>
    </div>

    <!-- 单选字符串 -->
    <div class="flex items-center gap-4">
      <span class="w-24 shrink-0 text-xs text-muted-foreground">单选（字符串）</span>
      <Select v-model="dictValue">
        <SelectTrigger class="h-8 w-52 text-xs">
          <SelectValue placeholder="请选择文件类型" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="d in items" :key="d.id" :value="d.dictValue" class="text-xs">
            {{ d.dictLabel }}
          </SelectItem>
        </SelectContent>
      </Select>
      <span class="text-xs text-muted-foreground">选中：{{ dictValue || '—' }}</span>
    </div>

    <!-- 数值型 -->
    <div class="flex items-center gap-4">
      <span class="w-24 shrink-0 text-xs text-muted-foreground">数值型</span>
      <Select :model-value="String(dictNum)" @update:model-value="(v) => (dictNum = Number(v))">
        <SelectTrigger class="h-8 w-52 text-xs">
          <SelectValue placeholder="请选择文件类型" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="d in items" :key="d.id" :value="d.dictValue" class="text-xs">
            {{ d.dictLabel }}
          </SelectItem>
        </SelectContent>
      </Select>
      <span class="text-xs text-muted-foreground">
        选中：{{ dictNum }}（{{ typeof dictNum }}）
      </span>
    </div>

    <!-- 禁用 -->
    <div class="flex items-center gap-4">
      <span class="w-24 shrink-0 text-xs text-muted-foreground">禁用状态</span>
      <Select model-value="1" disabled>
        <SelectTrigger class="h-8 w-52 text-xs">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="d in items" :key="d.id" :value="d.dictValue" class="text-xs">
            {{ d.dictLabel }}
          </SelectItem>
        </SelectContent>
      </Select>
    </div>

    <div class="h-px bg-border" />

    <!-- 字典标签回显 -->
    <div>
      <h4 class="text-sm font-medium">字典标签回显（dictLabel / Badge）</h4>
      <div class="mt-3 space-y-3 text-[13px]">
        <div class="flex items-center gap-3">
          <span class="w-24 shrink-0 text-xs text-muted-foreground">单值</span>
          <Badge variant="secondary" class="font-mono text-[11px]">1</Badge>
          <span class="text-muted-foreground">→</span>
          <span class="font-medium">{{ dictLabel(items, '1') }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="w-24 shrink-0 text-xs text-muted-foreground">数值型值</span>
          <Badge variant="secondary" class="font-mono text-[11px]">2</Badge>
          <span class="text-muted-foreground">→</span>
          <span class="font-medium">{{ dictLabel(items, 2) }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span class="w-24 shrink-0 text-xs text-muted-foreground">多值</span>
          <template v-for="v in ['1', '2']" :key="v">
            <Badge variant="secondary" class="font-mono text-[11px]">{{ dictLabel(items, v) }}</Badge>
          </template>
        </div>
        <div class="flex items-center gap-3">
          <span class="w-24 shrink-0 text-xs text-muted-foreground">未知值</span>
          <Badge variant="outline" class="font-mono text-[11px] text-muted-foreground">99</Badge>
          <span class="text-muted-foreground">→</span>
          <span class="text-muted-foreground">{{ dictLabel(items, '99') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { useDict, dictLabel } from '@/features/dict/useDict'

const { items } = useDict('sys_file_types')

const dictValue = ref('')
const dictNum = ref(1)
</script>
