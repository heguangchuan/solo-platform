<script lang="ts">
export default { name: 'SystemPermission' }
</script>
<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { Plus, Delete, Close, Check } from '@element-plus/icons-vue'
import type { SysPermission, PermissionRequest } from '@/types/api'
import {
  getPermissionTreeApi,
  addPermissionApi,
  editPermissionApi,
  deletePermissionApi,
  deletePermissionBatchApi,
} from '@/api/permission'

const loading = ref(false)
const treeData = ref<SysPermission[]>([])
const selectedRows = ref<SysPermission[]>([])
const tableRef = ref()
const allExpanded = ref(false)

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
}

// 菜单级别权限树（用于"上级权限"选择器）
const menuTree = ref<SysPermission[]>([])

// Element Plus 图标列表（kebab-case 名，例如 'el-icon-user' 风格不在 EP 中，这里用 PascalCase 名直接渲染）
const iconOptions = Object.keys(ElementPlusIconsVue)

const iconPickerVisible = ref(false)
const iconKeyword = ref('')

const filteredIcons = computed(() => {
  const kw = iconKeyword.value.trim().toLowerCase()
  if (!kw) return iconOptions
  return iconOptions.filter((name) => name.toLowerCase().includes(kw))
})

function selectIcon(name: string) {
  form.value.icon = name
  iconPickerVisible.value = false
}

// 上级权限的树形数据，前面挂一个"顶级权限"的虚拟根节点
const parentTreeData = computed(() => {
  return [
    {
      id: 0 as unknown as string,
      permissionName: '顶级权限',
      permissionCode: '',
      url: '',
      type: 0,
      parentId: -1 as unknown as string,
      sort: 0,
      icon: '',
      status: 1,
      children: menuTree.value,
    } as unknown as SysPermission,
  ]
})

// 记录正在编辑的行 id
const editingId = ref<string>('')

async function fetchData() {
  loading.value = true
  try {
    const [treeRes, menuRes] = await Promise.all([
      getPermissionTreeApi(),
      getPermissionTreeApi(1),
    ])
    treeData.value = treeRes.data
    menuTree.value = menuRes.data
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handleAdd(parentId?: string) {
  dialogTitle.value = '新增权限'
  editingId.value = ''
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
    if (!editingId.value) {
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

function handleSelectionChange(rows: SysPermission[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deletePermissionBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
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

async function handleToggleStatus(row: SysPermission) {
  const nextStatus = row.status === 1 ? 0 : 1
  await editPermissionApi(row.id, {
    permissionName: row.permissionName,
    permissionCode: row.permissionCode,
    url: row.url,
    type: row.type,
    parentId: row.parentId,
    sort: row.sort,
    icon: row.icon,
    status: nextStatus,
  })
  ElMessage.success(nextStatus === 1 ? '已启用' : '已禁用')
  fetchData()
}

function getTypeTag(type: number) {
  return typeLabelMap[type] || '未知'
}

/**
 * 递归遍历树，按目标展开状态调用 toggleRowExpansion，
 * 实现"一键全部展开 / 全部折叠"。
 */
function setTreeExpanded(rows: SysPermission[] | undefined, expanded: boolean) {
  if (!rows?.length) return
  rows.forEach((row) => {
    if (row.children?.length) {
      tableRef.value?.toggleRowExpansion(row, expanded)
      setTreeExpanded(row.children, expanded)
    }
  })
}

async function toggleExpandAll() {
  allExpanded.value = !allExpanded.value
  await nextTick()
  setTreeExpanded(treeData.value, allExpanded.value)
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover">
      <div class="table-toolbar">
        <el-button v-permission="'system:permission:add'" type="primary" :icon="Plus" @click="handleAdd()">新增</el-button>
        <el-button v-permission="'system:permission:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
        <el-button type="primary" @click="toggleExpandAll">
          <el-icon class="btn-icon"><component :is="allExpanded ? 'Fold' : 'Expand'" /></el-icon>
          {{ allExpanded ? '全部折叠' : '全部展开' }}
        </el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="treeData"
        v-loading="loading"
        border
        row-key="id"
        :tree-props="{ children: 'children' }"
        style="width: 100%"
        @selection-change="handleSelectionChange"
       max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" / max-height="calc(100vh - 400px)">
        <el-table-column prop="permissionName" label="权限名称" min-width="200" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-tag v-if="row.type === 1" type="primary" size="small">M</el-tag>
            <el-tag v-else-if="row.type === 2" type="success" size="small">B</el-tag>
            {{ row.permissionName }}
          </template>
        </el-table-column>
        <el-table-column prop="permissionCode" label="权限编码" min-width="200" / max-height="calc(100vh - 400px)">
        <el-table-column prop="url" label="路由地址" min-width="180" / max-height="calc(100vh - 400px)">
        <el-table-column label="权限类型" width="100" align="center" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            {{ getTypeTag(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="菜单图标" width="100" align="center" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-tooltip :content="row.icon || '无'" v-if="row.icon">
              <el-icon :size="18"><component :is="row.icon" /></el-icon>
            </el-tooltip>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="显示排序" width="100" align="center" / max-height="calc(100vh - 400px)">
        <el-table-column label="权限状态" width="100" align="center" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : 'danger'"
              size="small"
              class="status-tag"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" max-height="calc(100vh - 400px)">
          <template #default="{ row }">
            <el-button v-permission="'system:permission:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:permission:add'" type="primary" link @click="handleAdd(row.id)">新增</el-button>
            <el-button v-permission="'system:permission:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
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
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="权限名称" prop="permissionName">
          <el-input v-model="form.permissionName" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permissionCode">
          <el-input v-model="form.permissionCode" placeholder="如：system:user:list" />
        </el-form-item>
        <el-form-item label="路由地址">
          <el-input v-model="form.url" placeholder="前端路由路径" />
        </el-form-item>
        <el-form-item label="权限类型">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="菜单" :value="1" />
            <el-option label="按钮" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级权限">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeData"
            :props="{ label: 'permissionName', value: 'id', children: 'children' }"
            node-key="id"
            placeholder="请选择上级权限"
            check-strictly
            default-expand-all
            filterable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单图标">
          <el-popover
            v-model:visible="iconPickerVisible"
            placement="bottom-start"
            :width="380"
            trigger="click"
          >
            <template #reference>
              <div class="icon-picker-input">
                <el-input
                  :model-value="form.icon"
                  placeholder="请选择图标"
                  readonly
                  clearable
                  @clear="form.icon = ''"
                >
                  <template #prefix>
                    <el-icon v-if="form.icon"><component :is="form.icon" /></el-icon>
                  </template>
                  <template #suffix>
                    <el-icon><ArrowDown /></el-icon>
                  </template>
                </el-input>
              </div>
            </template>
            <div class="icon-picker-panel">
              <el-input
                v-model="iconKeyword"
                placeholder="搜索图标"
                clearable
                size="small"
                class="icon-search"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <div class="icon-grid">
                <div
                  v-for="name in filteredIcons"
                  :key="name"
                  class="icon-cell"
                  :class="{ active: form.icon === name }"
                  :title="name"
                  @click="selectIcon(name)"
                >
                  <el-icon :size="20"><component :is="name" /></el-icon>
                </div>
                <div v-if="!filteredIcons.length" class="icon-empty">未找到匹配图标</div>
              </div>
            </div>
          </el-popover>
        </el-form-item>
        <el-form-item label="显示排序">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="权限状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.btn-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

.text-muted { color: #c0c4cc; }

.status-tag {
  cursor: pointer;
}

.icon-picker-input {
  width: 100%;
  cursor: pointer;
}

.icon-picker-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-search {
  width: 100%;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.icon-cell {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
  transition: all 0.15s;
}

.icon-cell:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.icon-cell.active {
  background: var(--el-color-primary);
  color: #fff;
}

.icon-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 24px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
