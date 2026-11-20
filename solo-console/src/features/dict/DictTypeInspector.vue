<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconBook2 class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ creating ? '新建字典类型' : type?.dictName }}
        </div>
        <div v-if="!creating" class="truncate font-mono text-xs text-muted-foreground">
          {{ type?.dictType }}
        </div>
        <div v-else class="text-xs text-muted-foreground">创建一组枚举字典</div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 类型信息 -->
    <section>
      <h4 class="mb-3.5 text-[13px] font-medium">{{ creating ? '类型信息' : '基本信息' }}</h4>

      <div class="space-y-3">
        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            名称<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.dictName"
              class="h-8 text-xs"
              placeholder="如：用户性别"
              @blur="errors.dictName = !form.dictName.trim() ? '请输入字典名称' : ''"
            />
            <p v-if="errors.dictName" class="mt-1 text-[11px] text-destructive">
              {{ errors.dictName }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">
            编码<span class="ml-0.5 text-destructive">*</span>
          </Label>
          <div class="flex-1">
            <Input
              v-model="form.dictType"
              class="h-8 font-mono text-xs"
              placeholder="如：sys_user_sex"
              @blur="errors.dictType = !form.dictType.trim() ? '请输入字典编码' : ''"
            />
            <p v-if="errors.dictType" class="mt-1 text-[11px] text-destructive">
              {{ errors.dictType }}
            </p>
            <p v-if="!creating" class="mt-1 text-[11px] text-amber-600 dark:text-amber-500">
              修改编码将同步更新该类型下全部数据项
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Label class="w-14 shrink-0 text-xs text-muted-foreground">状态</Label>
          <div class="flex items-center gap-2">
            <Switch
              :model-value="form.status === 1"
              @update:model-value="(v) => (form.status = v ? 1 : 0)"
            />
            <span class="text-xs text-muted-foreground">{{ form.status === 1 ? '启用' : '禁用' }}</span>
          </div>
        </div>
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <Button size="sm" variant="outline" @click="emit('close')">取消</Button>
        <Button size="sm" :loading="saving" @click="onSave">
          {{ creating ? '创建类型' : '保存' }}
        </Button>
      </div>
    </section>

    <!-- 字典数据区块 -->
    <template v-if="!creating && type">
      <div class="my-4 h-px bg-border" />

      <section>
        <div class="mb-3 flex items-center gap-2">
          <h4 class="text-[13px] font-medium">字典数据</h4>
          <span class="text-xs text-muted-foreground">{{ dataTotal }} 项</span>
          <div class="flex-1" />
          <Button
            v-access="PERM.DICT_DATA_ADD"
            variant="ghost"
            size="sm"
            class="h-7 gap-1 text-xs"
            @click="onAddData"
          >
            <IconPlus class="h-3.5 w-3.5" />
            添加
          </Button>
        </div>

        <!-- 标签搜索 -->
        <div class="mb-2 flex items-center gap-1.5 rounded-md bg-muted/50 px-2">
          <IconSearch class="h-3 w-3 shrink-0 text-muted-foreground" />
          <input
            v-model="dataLabel"
            class="h-7 min-w-0 flex-1 bg-transparent text-xs outline-none placeholder:text-muted-foreground"
            placeholder="筛选字典标签"
          />
          <button v-if="dataLabel" class="text-muted-foreground hover:text-foreground" @click="dataLabel = ''">
            <IconX class="h-3 w-3" />
          </button>
        </div>

        <!-- 新建表单 -->
        <DictDataForm
          v-if="addingData"
          :dict-type="type.dictType"
          @cancel="addingData = false"
          @saved="onDataSaved"
        />

        <!-- 数据列表 -->
        <div class="mt-2 space-y-0.5">
          <template v-for="d in dataItems" :key="d.id">
            <DictDataForm
              v-if="editingId === d.id"
              :dict-type="type.dictType"
              :data="d"
              @cancel="editingId = ''"
              @saved="onDataSaved"
            />
            <div
              v-else
              class="group flex items-center gap-2 rounded-md px-2 py-1.5 text-xs hover:bg-muted/40"
            >
              <button class="min-w-0 flex-1 truncate text-left" :title="d.dictLabel" @click="onEditData(d)">
                <span class="font-medium">{{ d.dictLabel }}</span>
              </button>
              <span class="w-16 shrink-0 truncate font-mono text-[11px] text-muted-foreground" :title="d.dictValue">
                {{ d.dictValue }}
              </span>
              <span class="w-8 shrink-0 text-center text-[11px] text-muted-foreground">{{ d.sort }}</span>
              <Badge
                class="h-4 px-1.5 py-0 text-[10px] leading-none"
                :variant="d.status === 1 ? 'secondary' : 'outline'"
                :class="d.status === 1 ? '' : 'text-muted-foreground'"
              >
                {{ d.status === 1 ? '启用' : '禁用' }}
              </Badge>
              <Button
                v-access="PERM.DICT_DATA_DELETE"
                variant="ghost"
                size="icon-sm"
                class="size-6 shrink-0 text-muted-foreground opacity-0 hover:text-destructive group-hover:opacity-100"
                @click="onDeleteData(d)"
              >
                <IconTrash class="h-3 w-3" />
              </Button>
            </div>
          </template>
        </div>

        <!-- 加载态 / 空态 / 更多 -->
        <div v-if="dataLoading" class="grid place-items-center py-4">
          <IconLoader2 class="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
        <div
          v-else-if="!dataItems.length"
          class="py-6 text-center text-xs text-muted-foreground"
        >
          {{ dataLabel ? '没有匹配的字典项' : '暂无字典数据，点击「添加」创建' }}
        </div>
        <div v-else-if="dataItems.length < dataTotal" class="py-2 text-center">
          <Button variant="ghost" size="sm" class="h-7 text-xs text-muted-foreground" @click="loadData(false)">
            加载更多（剩余 {{ dataTotal - dataItems.length }}）
          </Button>
        </div>
      </section>

      <!-- 危险操作 -->
      <div class="my-4 h-px bg-border" />

      <section>
        <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.DICT_TYPE_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="onDeleteType"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该字典类型
        </Button>
        <p class="mt-2 text-[11px] text-muted-foreground">
          将同时删除该类型下全部 {{ dataTotal }} 个字典数据项，不可恢复。
        </p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconBook2,
  IconX,
  IconPlus,
  IconTrash,
  IconSearch,
  IconLoader2,
} from '@tabler/icons-vue'
import type {
  SysDictType,
  DictTypeRequest,
  SysDictData,
} from '@/types/api'
import {
  addDictTypeApi,
  editDictTypeApi,
  deleteDictTypeApi,
  getDictDataPageApi,
  deleteDictDataApi,
} from '@/api/dict'
import { PERM } from '@/features/access/permissions'
import { clearDictCache } from '@/features/dict/useDict'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import Input from '@/components/ui/input/Input.vue'
import Label from '@/components/ui/label/Label.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import { Badge } from '@/components/ui/badge'
import DictDataForm from '@/features/dict/DictDataForm.vue'

const props = defineProps<{ type?: SysDictType; creating?: boolean }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
  (e: 'created'): void
  (e: 'deleted', type: SysDictType): void
}>()

/* ---------- 类型表单 ---------- */
const buildForm = (): DictTypeRequest => ({
  dictName: props.type?.dictName ?? '',
  dictType: props.type?.dictType ?? '',
  status: props.type?.status ?? 1,
})

const form = reactive<DictTypeRequest>(buildForm())
const saving = ref(false)
const errors = reactive<{ dictName: string; dictType: string }>({
  dictName: '',
  dictType: '',
})

watch(
  () => props.type?.id,
  () => {
    Object.assign(form, buildForm())
    errors.dictName = ''
    errors.dictType = ''
  },
)

const validate = (): boolean => {
  errors.dictName = form.dictName.trim() ? '' : '请输入字典名称'
  errors.dictType = form.dictType.trim() ? '' : '请输入字典编码'
  return !errors.dictName && !errors.dictType
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addDictTypeApi({
        ...form,
        dictName: form.dictName.trim(),
        dictType: form.dictType.trim(),
      })
      toast.success('字典类型创建成功')
      emit('created')
    } else if (props.type) {
      await editDictTypeApi(props.type.id, { ...form })
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onDeleteType = async () => {
  if (!props.type) return
  const ok = await confirm({
    title: '删除字典类型',
    description: `确认删除字典类型「${props.type.dictName}」？其下全部字典数据项将一并删除，不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteDictTypeApi(props.type.id)
  clearDictCache(props.type.dictType)
  toast.success('字典类型已删除')
  emit('deleted', props.type)
}

/* ---------- 字典数据 ---------- */
const DATA_PAGE_SIZE = 100
const dataItems = ref<SysDictData[]>([])
const dataTotal = ref(0)
const dataLoading = ref(false)
const dataPage = ref(1)
const dataLabel = ref('')
const addingData = ref(false)
const editingId = ref('')

const loadData = async (append = false) => {
  if (!props.type) return
  dataLoading.value = true
  try {
    const res = await getDictDataPageApi({
      page: dataPage.value,
      pageSize: DATA_PAGE_SIZE,
      dictType: props.type.dictType,
      dictLabel: dataLabel.value.trim() || undefined,
    })
    const rows = res.data.rows
    dataItems.value = append ? [...dataItems.value, ...rows] : rows
    dataTotal.value = res.data.total
  } finally {
    dataLoading.value = false
  }
}

const resetData = () => {
  dataItems.value = []
  dataTotal.value = 0
  dataPage.value = 1
  addingData.value = false
  editingId.value = ''
}

/* 切换类型 → 重置加载；类型编码保存后变化 → 按新编码重新加载；immediate 保证首次挂载即加载 */
watch(
  () => [props.type?.id, props.type?.dictType] as const,
  (cur, prev) => {
    const id = cur[0]
    if (!id || props.creating) return
    if (!prev || id !== prev[0] || cur[1] !== prev[1]) {
      resetData()
      loadData()
    }
  },
  { immediate: true },
)

/* 标签筛选防抖 */
let labelTimer: ReturnType<typeof setTimeout> | null = null
watch(dataLabel, () => {
  if (labelTimer) clearTimeout(labelTimer)
  labelTimer = setTimeout(() => {
    dataPage.value = 1
    loadData()
  }, 300)
})

const onAddData = () => {
  editingId.value = ''
  addingData.value = true
}

const onEditData = (d: SysDictData) => {
  addingData.value = false
  editingId.value = d.id
}

const onDataSaved = () => {
  addingData.value = false
  editingId.value = ''
  dataPage.value = 1
  loadData()
}

const onDeleteData = async (d: SysDictData) => {
  const ok = await confirm({
    title: '删除字典项',
    description: `确认删除字典项「${d.dictLabel}」？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteDictDataApi(d.id)
  clearDictCache(props.type?.dictType)
  dataItems.value = dataItems.value.filter((i) => i.id !== d.id)
  dataTotal.value = Math.max(0, dataTotal.value - 1)
  toast.success('字典项已删除')
}
</script>
