<template>
  <div class="flex h-full flex-col">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <div
        class="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary/10 text-primary"
      >
        <ShieldCheck class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <div class="text-base font-semibold">{{ creating ? '新建角色' : role?.roleName }}</div>
        <div class="mono text-xs text-muted-foreground">
          {{ creating ? '填写角色信息' : role?.roleCode }}
        </div>
      </div>
      <Button variant="ghost" size="icon-sm" @click="$emit('close')">
        <X class="h-4 w-4" />
      </Button>
    </div>

    <!-- 创建态：无 tab，仅基本信息 -->
    <template v-if="creating">
      <div class="my-4 h-px bg-border" />
      <RoleBasicForm
        :form="form"
        :errors="errors"
        :saving="saving"
        :creating="true"
        @save="onSave"
        @cancel="$emit('close')"
      />
    </template>

    <!-- 编辑态：基本信息 / 权限分配 -->
    <Tabs v-else v-model="activeTab" class="mt-3 flex-1">
      <TabsList class="w-full">
        <TabsTrigger value="basic" class="flex-1">基本信息</TabsTrigger>
        <TabsTrigger value="permission" class="flex-1">权限分配</TabsTrigger>
      </TabsList>

      <!-- forceMount + hidden：切 tab 不卸载，保留权限树勾选状态 -->
      <TabsContent value="basic" force-mount :hidden="activeTab !== 'basic'">
        <RoleBasicForm
          :form="form"
          :errors="errors"
          :saving="saving"
          :creating="false"
          @save="onSave"
          @cancel="$emit('close')"
        />

        <div class="my-4 h-px bg-border" />

        <section>
          <h4 class="mb-3.5 text-[13px] font-medium text-destructive">危险操作</h4>
          <Button
            v-access="PERM.ROLE_DELETE"
            variant="destructive"
            size="sm"
            class="w-full"
            @click="onDelete"
          >
            <Trash2 class="h-3.5 w-3.5" />
            删除该角色
          </Button>
        </section>
      </TabsContent>

      <TabsContent value="permission" force-mount :hidden="activeTab !== 'permission'">
        <div v-if="permLoading" class="grid place-items-center py-10">
          <svg class="h-5 w-5 animate-spin text-muted-foreground" viewBox="0 0 24 24" fill="none">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
            <path class="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
          </svg>
        </div>
        <template v-else>
          <PermissionTree ref="treeRef" :data="permissionTree" :disabled="!canAssign" />
          <div class="mt-3 flex items-center justify-end gap-2">
            <p v-if="!canAssign" class="text-[11px] text-muted-foreground">无角色授权权限</p>
            <Button
              v-else
              variant="outline"
              size="sm"
              :loading="assignLoading"
              @click="onAssignPermissions"
            >
              保存权限
            </Button>
          </div>
        </template>
      </TabsContent>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue'
import { ShieldCheck, Trash2, X } from 'lucide-vue-next'
import type { SysRole, SysPermission, RoleRequest } from '@/types/api'
import {
  addRoleApi,
  editRoleApi,
  deleteRoleApi,
  getRoleAssignedPermissionsApi,
  assignRolePermissionsApi,
} from '@/api/role'
import { getPermissionTreeApi } from '@/api/permission'
import PermissionTree from './PermissionTree.vue'
import RoleBasicForm from './RoleBasicForm.vue'
import { PERM } from '@/features/access/permissions'
import { useAccess } from '@/features/access/useAccess'
import { toast } from '@/lib/toast'
import { confirm } from '@/lib/confirm'
import Button from '@/components/ui/button/Button.vue'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const props = defineProps<{ role?: SysRole; creating?: boolean }>()
const emit = defineEmits<{
  close: []
  saved: []
  created: []
  deleted: [role: SysRole]
}>()

const { can } = useAccess()
const canAssign = ref(can(PERM.ROLE_P2R))

const activeTab = ref<'basic' | 'permission'>('basic')

const form = reactive<RoleRequest>({
  roleCode: props.role?.roleCode ?? '',
  roleName: props.role?.roleName ?? '',
  description: props.role?.description ?? '',
  status: props.role?.status ?? 1,
})
const saving = ref(false)
const errors = reactive<{ roleCode: string; roleName: string }>({ roleCode: '', roleName: '' })

/* ---------- 权限树加载与回填 ---------- */
const treeRef = ref<InstanceType<typeof PermissionTree>>()
const permissionTree = ref<SysPermission[]>([])
const permLoading = ref(false)
const assignLoading = ref(false)

const loadPermissionTree = async () => {
  if (!props.role) return
  permLoading.value = true
  let assigned: (string | number)[] = []
  try {
    const [treeRes, assignedRes] = await Promise.all([
      getPermissionTreeApi(),
      getRoleAssignedPermissionsApi(props.role.id),
    ])
    permissionTree.value = treeRes.data
    assigned = assignedRes.data || []
  } finally {
    // 必须先置 false：PermissionTree 在 v-else 分支，loading 期间并未挂载
    permLoading.value = false
  }
  // 树挂载渲染完再回填，否则 treeRef 为空，回填被吞掉
  await nextTick()
  treeRef.value?.setChecked(assigned)
}

watch(
  () => props.role?.id,
  async () => {
    activeTab.value = 'basic'
    if (props.creating || !props.role) {
      Object.assign(form, { roleCode: '', roleName: '', description: '', status: 1 })
      errors.roleCode = ''
      errors.roleName = ''
      permissionTree.value = []
      return
    }
    Object.assign(form, {
      roleCode: props.role.roleCode,
      roleName: props.role.roleName,
      description: props.role.description,
      status: props.role.status,
    })
    errors.roleCode = ''
    errors.roleName = ''
    await loadPermissionTree()
  },
  { immediate: true },
)

const validate = (): boolean => {
  errors.roleCode = form.roleCode.trim() ? '' : '请输入角色编码'
  errors.roleName = form.roleName.trim() ? '' : '请输入角色名称'
  return !errors.roleCode && !errors.roleName
}

const onSave = async () => {
  if (!validate()) return
  saving.value = true
  try {
    if (props.creating) {
      await addRoleApi({ ...form })
      toast.success('角色创建成功')
      emit('created')
    } else if (props.role) {
      await editRoleApi(props.role.id, form)
      toast.success('已保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}

const onAssignPermissions = async () => {
  if (!props.role) return
  const permissionIds = treeRef.value?.getChecked() ?? []
  assignLoading.value = true
  try {
    await assignRolePermissionsApi(props.role.id, permissionIds)
    toast.success('授权成功')
  } finally {
    assignLoading.value = false
  }
}

const onDelete = async () => {
  if (!props.role) return
  const ok = await confirm({
    title: '删除角色',
    description: `确认删除角色「${props.role.roleName}」？已分配该角色的用户将同步失去对应权限。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  await deleteRoleApi(props.role.id)
  toast.success('角色已删除')
  emit('deleted', props.role)
}
</script>
