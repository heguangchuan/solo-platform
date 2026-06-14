<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SysPermission, PermissionRequest } from '@/types/api'
import { getPermissionTreeApi, addPermissionApi, editPermissionApi, deletePermissionApi } from '@/api/permission'

const loading = ref(false)
const treeData = ref<SysPermission[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const form = ref<PermissionRequest>({
  permissionName: '',
  permissionCode: '',
  url: '',
  type: 1,
  parentId: 0,
  sort: 0,
  icon: '',
  status: 1,
})

const rules = {
  permissionName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  permissionCode: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
}

const typeLabelMap: Record<number, string> = {
  1: '菜单',
  2: '按钮',
  3: '接口',
}

// 所有权限（一维列表，用于上级选择器）
const allPermissions = ref<SysPermission[]>([])

// 记录正在编辑的行 id
const editingId = ref<number>(0)

// 收集所有权限为一维列表（供上级选择器用）
function flattenTree(nodes: SysPermission[]): SysPermission[] {
  const result: SysPermission[] = []
  function walk(list: SysPermission[]) {
    list.forEach((node) => {
      result.push(node)
      if (node.children?.length) walk(node.children)
    })
  }
  walk(nodes)
  return result
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPermissionTreeApi()
    treeData.value = res.data
    allPermissions.value = flattenTree(res.data)
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handleAdd(parentId?: number) {
  dialogTitle.value = '新增权限'
  editingId.value = 0
  form.value = {
    permissionName: '',
    permissionCode: '',
    url: '',
    type: parentId ? 2 : 1,
    parentId: parentId ?? 0,
    sort: 0,
    icon: '',
    status: 1,
  }
  dialogVisible.value = true
}

function handleEdit(row: SysPermission) {
  dialogTitle.value = '编辑权限'
  editingId.value = row.id
  form.value = {
    permissionName: row.permissionName,
    permissionCode: row.permissionCode,
    url: row.url,
    type: row.type,
    parentId: row.parentId,
    sort: row.sort,
    icon: row.icon,
    status: row.status,
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (editingId.value === 0) {
      await addPermissionApi(form.value)
    } else {
      await editPermissionApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled by interceptor
  }
}

async function handleDelete(row: SysPermission) {
  try {
    await ElMessageBox.confirm(
      `确定要删除权限 "${row.permissionName}" 吗？其子权限也将被一并删除。`,
      '提示',
      { type: 'warning' },
    )
    await deletePermissionApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

function getTypeTag(type: number) {
  return typeLabelMap[type] || '未知'
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>权限管理</span>
          <el-button v-permission="'system:permission:add'" type="primary" @click="handleAdd()">新增顶级权限</el-button>
        </div>
      </template>

      <el-table
        :data="treeData"
        v-loading="loading"
        border
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        style="width: 100%"
      >
        <el-table-column prop="permissionName" label="权限名称" min-width="200">
          <template #default="{ row }">
            <el-tag v-if="row.type === 1" type="primary" size="small">M</el-tag>
            <el-tag v-else-if="row.type === 2" type="success" size="small">B</el-tag>
            <el-tag v-else type="warning" size="small">A</el-tag>
            {{ row.permissionName }}
          </template>
        </el-table-column>
        <el-table-column prop="permissionCode" label="权限编码" min-width="200" />
        <el-table-column prop="url" label="URL" min-width="180" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            {{ getTypeTag(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip :content="row.icon || '无'" v-if="row.icon">
              <el-icon :size="18"><component :is="row.icon" /></el-icon>
            </el-tooltip>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:permission:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:permission:add'" type="success" link @click="handleAdd(row.id)">添加子项</el-button>
            <el-button v-permission="'system:permission:delete'" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="550px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="权限名称" prop="permissionName">
          <el-input v-model="form.permissionName" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permissionCode">
          <el-input v-model="form.permissionCode" placeholder="如：system:user:list" />
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="form.url" placeholder="前端路由路径" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="菜单" :value="1" />
            <el-option label="按钮" :value="2" />
            <el-option label="接口" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级权限">
          <el-select
            v-model="form.parentId"
            placeholder="请选择上级权限"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option label="顶级权限" :value="0" />
            <el-option
              v-for="item in allPermissions"
              :key="item.id"
              :label="item.permissionName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.text-muted { color: #c0c4cc; }
</style>
