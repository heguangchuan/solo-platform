<template>
  <PopoverRoot>
    <PopoverTrigger as-child>
      <button
        type="button"
        class="flex min-h-8 w-full flex-wrap items-center gap-1 rounded-md border border-border bg-transparent px-2 py-1 text-left text-xs outline-none hover:bg-muted/40 focus:bg-muted/70"
      >
        <template v-if="selectedCodes.length">
          <span
            v-for="code in selectedCodes"
            :key="code"
            class="inline-flex max-w-full items-center gap-1 rounded bg-primary/10 px-1.5 py-0.5 text-[11px] text-primary"
            :title="code"
          >
            <span class="truncate">{{ nameOf(code) }}</span>
            <span
              class="shrink-0 cursor-pointer rounded-sm p-0.5 hover:bg-primary/15"
              title="移除"
              @click.stop="toggle(code)"
            >
              <IconX class="size-2.5" />
            </span>
          </span>
        </template>
        <span v-else class="text-muted-foreground">未授权任何接口</span>
        <IconChevronDown class="ml-auto size-3.5 shrink-0 text-muted-foreground" />
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="4"
        class="z-50 w-[320px] rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md outline-none"
      >
        <div v-if="loading" class="flex items-center gap-2 px-2 py-6 text-xs text-muted-foreground">
          <IconLoader2 class="size-3.5 animate-spin" />
          正在加载授权范围…
        </div>

        <div v-else-if="error" class="flex flex-col items-start gap-1 px-2 py-4 text-xs">
          <span class="text-destructive">{{ error }}</span>
          <Button variant="ghost" size="sm" class="h-7 text-xs" @click="load">重试</Button>
        </div>

        <template v-else>
          <div v-for="group in groups" :key="group.tag">
            <div class="px-2 pb-1 pt-2 text-[11px] font-medium text-muted-foreground">
              {{ group.tag }}
            </div>
            <button
              v-for="scope in group.items"
              :key="scope.code"
              type="button"
              class="flex w-full items-start gap-2 rounded-sm px-2 py-1.5 text-left text-xs hover:bg-accent"
              :title="endpointTitle(scope)"
              @click="toggle(scope.code)"
            >
              <Checkbox
                as="span"
                :model-value="selectedCodes.includes(scope.code)"
                tabindex="-1"
                class="pointer-events-none mt-0.5 inline-block"
              />
              <span class="min-w-0 flex-1">
                <span class="block truncate">{{ scope.name }}</span>
                <span class="block truncate font-mono text-[11px] text-muted-foreground">
                  {{ scope.code }}
                </span>
              </span>
              <span class="shrink-0 text-[11px] text-muted-foreground">
                {{ scope.endpoints.length }} 个接口
              </span>
            </button>
          </div>

          <div v-if="unknownCodes.length">
            <div class="px-2 pb-1 pt-2 text-[11px] font-medium text-destructive/80">
              目录中不存在（保存前请移除）
            </div>
            <button
              v-for="code in unknownCodes"
              :key="code"
              type="button"
              class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-xs text-destructive hover:bg-destructive/10"
              @click="toggle(code)"
            >
              <Checkbox as="span" :model-value="true" tabindex="-1" class="pointer-events-none inline-block" />
              <span class="min-w-0 flex-1 truncate font-mono text-[11px]">{{ code }}</span>
            </button>
          </div>

          <div v-if="loaded && !scopes.length" class="px-2 py-6 text-center text-xs text-muted-foreground">
            暂无可授权接口
          </div>
        </template>

        <div
          class="mt-1 flex items-center justify-between border-t border-border px-2 pt-1.5 text-[11px] text-muted-foreground"
        >
          <span>已选 {{ selectedCodes.length }} 项</span>
          <button
            v-if="selectedCodes.length"
            type="button"
            class="hover:text-foreground"
            @click="clearAll"
          >
            清空
          </button>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent } from 'reka-ui'
import { IconChevronDown, IconLoader2, IconX } from '@tabler/icons-vue'
import { Checkbox } from '@/components/ui/checkbox'
import Button from '@/components/ui/button/Button.vue'
import { getOpenApiScopeListApi } from '@/api/openapi'
import type { OpenApiScope } from '@/types/api'

const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const scopes = ref<OpenApiScope[]>([])
const loading = ref(false)
const loaded = ref(false)
const error = ref('')

const selectedCodes = computed(() =>
  (props.modelValue ?? '')
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean),
)

const scopeByCode = computed(() => new Map(scopes.value.map((scope) => [scope.code, scope])))

const groups = computed(() => {
  const map = new Map<string, OpenApiScope[]>()
  for (const scope of scopes.value) {
    const list = map.get(scope.tag) ?? []
    list.push(scope)
    map.set(scope.tag, list)
  }
  return [...map.entries()].map(([tag, items]) => ({ tag, items }))
})

const unknownCodes = computed(() =>
  loaded.value ? selectedCodes.value.filter((code) => !scopeByCode.value.has(code)) : [],
)

const nameOf = (code: string) => scopeByCode.value.get(code)?.name || code

const endpointTitle = (scope: OpenApiScope) =>
  scope.endpoints.map((item) => `${item.method} ${item.path} ${item.summary}`.trim()).join('\n')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    scopes.value = (await getOpenApiScopeListApi()).data
    loaded.value = true
  } catch {
    error.value = '授权范围加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const toggle = (code: string) => {
  const next = selectedCodes.value.includes(code)
    ? selectedCodes.value.filter((item) => item !== code)
    : [...selectedCodes.value, code]
  emit('update:modelValue', next.join(','))
}

const clearAll = () => emit('update:modelValue', '')
</script>
