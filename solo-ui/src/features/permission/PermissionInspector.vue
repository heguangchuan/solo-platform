<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  IconX,
  IconShieldLock,
  IconTrash,
  IconPlus,
} from '@tabler/icons-vue'
import Button from '@/components/ui/button/Button.vue'
import { Switch } from '@/components/ui/switch'
import type { SysPermission, PermissionRequest } from '@/types/api'
import { addPermissionApi, editPermissionApi } from '@/api/permission'
import { PERM } from '@/features/access/permissions'
import { toast } from '@/lib/toast'
import IconPicker from './IconPicker.vue'
import ParentTreeSelect from './ParentTreeSelect.vue'

const props = defineProps<{
  /** 编辑/查看时传入；新建为 undefined */
  permission?: SysPermission
  /** 新建时的预置父级（"新增子权限"） */
  presetParentId?: string
  /** 菜单树，供上级权限选择 */
  menuTree: SysPermission[]
  /** 新建态 */
  creating?: boolean
}>()

const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [permission: SysPermission]
  addChild: [permission: SysPermission]
}>()

const form = reactive<PermissionRequest>({
  permissionName: '',
  permissionCode: '',
  url: '',
  type: 1,
  parentId: 0,
  sort: 1,
  icon: '',
  status: 1,
})
const errors = reactive({ permissionName: '', permissionCode: '' })
const saving = ref(false)

const resetForm = () => {
  if (props.permission) {
    Object.assign(form, {
      permissionName: props.permission.permissionName,
      permissionCode: props.permission.permissionCode,
      url: props.permission.url,
      type: props.permission.type,
      parentId: props.permission.parentId,
      sort: props.permission.sort,
      icon: props.permission.icon,
      status: props.permission.status,
    })
  } else {
    Object.assign(form, {
      permissionName: '',
      permissionCode: '',
      url: '',
      type: props.presetParentId ? 2 : 1,
      parentId: props.presetParentId ?? 0,
      sort: 1,
      icon: '',
      status: 1,
    })
  }
  errors.permissionName = ''
  errors.permissionCode = ''
}

watch(
  () => [props.permission, props.creating, props.presetParentId],
  resetForm,
  { immediate: true },
)

const validate = () => {
  errors.permissionName = form.permissionName.trim() ? '' : '请输入权限名称'
  errors.permissionCode = form.permissionCode.trim() ? '' : '请输入权限编码'
  return !errors.permissionName && !errors.permissionCode
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.permission) {
      await editPermissionApi(props.permission.id, { ...form })
      toast.success('保存成功')
      emit('saved')
    } else {
      await addPermissionApi({ ...form })
      toast.success('创建成功')
      emit('created')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <IconShieldLock class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold">
          {{ permission ? permission.permissionName : '新建权限' }}
        </div>
        <div class="truncate font-mono text-xs text-muted-foreground">
          {{ permission ? permission.permissionCode : '填写权限信息' }}
        </div>
      </div>
      <Button
        v-if="permission"
        v-access="PERM.PERMISSION_ADD"
        variant="ghost"
        size="icon-sm"
        title="新增子权限"
        @click="emit('addChild', permission)"
      >
        <IconPlus class="h-4 w-4" />
      </Button>
      <Button variant="ghost" size="icon-sm" @click="emit('close')">
        <IconX class="h-4 w-4" />
      </Button>
    </div>

    <div class="my-4 h-px bg-border" />

    <!-- 表单 -->
    <div class="space-y-3">
      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground"><span class="text-destructive">*</span> 权限名称</label>
        <input
          v-model="form.permissionName"
          class="h-8 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
          :class="errors.permissionName ? 'border-destructive' : ''"
          placeholder="请输入权限名称"
        />
        <span v-if="errors.permissionName" class="text-[10px] text-destructive">{{ errors.permissionName }}</span>
      </div>

      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground"><span class="text-destructive">*</span> 权限编码</label>
        <input
          v-model="form.permissionCode"
          class="h-8 rounded-md border border-input bg-transparent px-2 text-xs font-mono outline-none focus:border-ring"
          :class="errors.permissionCode ? 'border-destructive' : ''"
          placeholder="如：system:user:list"
        />
        <span v-if="errors.permissionCode" class="text-[10px] text-destructive">{{ errors.permissionCode }}</span>
      </div>

      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground">路由地址</label>
        <input
          v-model="form.url"
          class="h-8 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
          placeholder="前端路由路径"
        />
      </div>

      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground">权限类型</label>
        <div class="flex gap-1">
          <button
            type="button"
            class="h-8 flex-1 rounded-md border text-xs"
            :class="form.type === 1 ? 'border-primary bg-primary text-primary-foreground' : 'border-input hover:bg-muted/60'"
            @click="form.type = 1"
          >
            菜单
          </button>
          <button
            type="button"
            class="h-8 flex-1 rounded-md border text-xs"
            :class="form.type === 2 ? 'border-primary bg-primary text-primary-foreground' : 'border-input hover:bg-muted/60'"
            @click="form.type = 2"
          >
            按钮
          </button>
        </div>
      </div>

      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground">上级权限</label>
        <ParentTreeSelect
          v-model="form.parentId"
          :options="menuTree"
          :exclude-id="permission?.id"
        />
      </div>

      <div class="grid gap-1.5">
        <label class="text-xs text-muted-foreground">菜单图标</label>
        <IconPicker v-model="form.icon" />
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div class="grid gap-1.5">
          <label class="text-xs text-muted-foreground">显示排序</label>
          <input
            v-model.number="form.sort"
            type="number"
            min="0"
            class="h-8 rounded-md border border-input bg-transparent px-2 text-xs outline-none focus:border-ring"
          />
        </div>
        <div class="grid gap-1.5">
          <label class="text-xs text-muted-foreground">权限状态</label>
          <div class="flex h-8 items-center">
            <Switch
              :model-value="form.status === 1"
              @update:model-value="(v: boolean) => (form.status = v ? 1 : 0)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="mt-5 flex gap-2">
      <Button variant="outline" size="sm" class="flex-1" @click="emit('close')">取消</Button>
      <Button size="sm" class="flex-1" :disabled="saving" @click="onSave">
        {{ permission ? '保存' : '创建' }}
      </Button>
    </div>

    <!-- 危险操作 -->
    <template v-if="permission">
      <div class="my-4 h-px bg-border" />
      <section>
        <h4 class="mb-2.5 text-[13px] font-medium text-destructive">危险操作</h4>
        <Button
          v-access="PERM.PERMISSION_DELETE"
          variant="destructive"
          size="sm"
          class="w-full"
          @click="emit('deleted', permission)"
        >
          <IconTrash class="h-3.5 w-3.5" />
          删除该权限（含子权限）
        </Button>
      </section>
    </template>
  </div>
</template>
