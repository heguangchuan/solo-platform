<script lang="ts">
export default { name: 'SystemRole' }
</script>
<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Upload, Download, Close, Check, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { SysRole, SysPermission, RoleRequest, RolePageRequest } from '@/types/api'
import {
  getRolePageApi,
  addRoleApi,
  editRoleApi,
  deleteRoleApi,
  deleteRoleBatchApi,
  exportRoleApi,
  getRoleAssignedPermissionsApi,
  assignRolePermissionsApi,
} from '@/api/role'
import { getPermissionTreeApi } from '@/api/permission'
import { downloadBlob } from '@/utils/download'

const loading = ref(false)
const tableData = ref<SysRole[]>([])
const selectedRows = ref<SysRole[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<RolePageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const form = ref<RoleRequest>({
  roleCode: '',
  roleName: '',
  description: '',
  status: 1,
})

const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 10 }
  handleSearch()
}

async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getRolePageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

function handleAdd() {
  dialogTitle.value = '新增角色'
  editingId.value = ''
  form.value = { roleCode: '', roleName: '', description: '', status: 1 }
  dialogVisible.value = true
}

function handleEdit(row: SysRole) {
  dialogTitle.value = '编辑角色'
  editingId.value = row.id
  form.value = {
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description,
    status: row.status,
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (!editingId.value) {
      await addRoleApi(form.value)
    } else {
      await editRoleApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: SysRole[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
    await deleteRoleBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleExport() {
  const ids = selectedRows.value.map((row) => row.id)
  const res = await exportRoleApi({ ...searchForm.value, ids: ids.length ? ids : undefined })
  downloadBlob(res.data, '角色列表.xlsx')
}

async function handleDelete(row: SysRole) {
  try {
    await ElMessageBox.confirm(`确定要删除角色 "${row.roleName}" 吗？`, '提示', { type: 'warning' })
    await deleteRoleApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

const statusTypeMap: Record<number, string> = {
  1: 'success',
  0: 'danger',
}

/* ========================== 角色授权（p2r） ========================== */
const p2rVisible = ref(false)
const p2rRole = ref<SysRole | null>(null)
const p2rLoading = ref(false)
const p2rSubmitting = ref(false)
const p2rTreeRef = ref<any>()
const p2rStrictly = ref(false)
const permissionTree = ref<SysPermission[]>([])
const treeProps = { label: 'permissionName', children: 'children' }

/**
 * 收集树中所有"叶子节点"的 ID。
 * el-tree 在非严格模式下，半选/全选父节点时，getCheckedKeys 仅返回叶子，
 * 但我们前端把"已分配"完整 ID 列表回填，需要先过滤出叶子来 setCheckedKeys，
 * 否则父节点会被强制勾选，影响子节点状态。
 */
function collectLeafIds(nodes: SysPermission[] | undefined, into: Set<string>) {
  if (!nodes?.length) return
  nodes.forEach((n) => {
    if (n.children?.length) collectLeafIds(n.children, into)
    else into.add(n.id)
  })
}

async function handleAssignPermissions(row: SysRole) {
  p2rRole.value = row
  p2rVisible.value = true
  p2rLoading.value = true
  p2rStrictly.value = false
  let assignedLeaves: string[] = []
  try {
    if (!permissionTree.value.length) {
      const treeRes = await getPermissionTreeApi()
      permissionTree.value = treeRes.data
    }
    const assignedRes = await getRoleAssignedPermissionsApi(row.id)

    // 后端 List<Long> 直接序列化为 number；权限树的 id 经 ToStringSerializer 是 string。
    // 必须在前端把已分配的 ID 转成 string，否则 setCheckedKeys 全部匹配不上 node-key
    const assignedAll = (assignedRes.data || []).map((v) => String(v))

    // 只保留叶子节点 ID。non-strict 模式下，setCheckedKeys 传入父节点会强制
    // 勾选其全部子节点，从而把"父分配但只勾了部分子节点"的真实状态吃掉。
    const leafSet = new Set<string>()
    collectLeafIds(permissionTree.value, leafSet)
    assignedLeaves = assignedAll.filter((id) => leafSet.has(id))
  } finally {
    p2rLoading.value = false
  }
  // loading=false 之后 v-if 才会渲染 el-tree；必须等 DOM 更新完再回填
  await nextTick()
  // 切换角色时先清空，避免上一次的勾选残留（尤其在 strict / non-strict 切换时）
  p2rTreeRef.value?.setCheckedKeys([])
  p2rTreeRef.value?.setCheckedKeys(assignedLeaves)
}

async function handleConfirmAssignPermissions() {
  if (!p2rRole.value) return
  // getCheckedKeys + getHalfCheckedKeys：父节点被半选时也得算上，
  // 否则保存后再次打开会丢父节点本身的权限点。
  const checked = p2rTreeRef.value?.getCheckedKeys() || []
  const half = p2rTreeRef.value?.getHalfCheckedKeys() || []
  const all = [...checked, ...half] as string[]
  p2rSubmitting.value = true
  try {
    await assignRolePermissionsApi(p2rRole.value.id, all)
    ElMessage.success('授权成功')
    p2rVisible.value = false
  } finally {
    p2rSubmitting.value = false
  }
}

function expandAllPermissions(expand: boolean) {
  // 通过修改 store，直接重渲染时让所有节点 default-expand-all
  // 这里直接调用内部 store 节点
  const tree = p2rTreeRef.value
  if (!tree) return
  const nodes = tree.store?.nodesMap || {}
  Object.keys(nodes).forEach((k) => (nodes[k].expanded = expand))
}

const p2rDialogTitle = computed(
  () => `角色授权 - ${p2rRole.value?.roleName || p2rRole.value?.roleCode || ''}`,
)

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="角色编码">
          <el-input v-model="searchForm.roleCode" placeholder="请输入角色编码" clearable />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="searchForm.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:role:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'system:role:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'system:role:delete'" type="primary" :icon="Delete" @click="handleBatchDelete">删除</el-button>
        <el-button type="primary" :icon="Upload">导入</el-button>
        <el-button v-permission="'system:role:export'" type="primary" :icon="Download" @click="handleExport">导出</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange" max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="description" label="角色描述" min-width="200" />
        <el-table-column label="角色状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:role:p2r'" type="primary" link @click="handleAssignPermissions(row)">授权</el-button>
            <el-button v-permission="'system:role:delete'" type="primary" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如：ADMIN、USER" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
        </el-form-item>
        <el-form-item label="角色状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="p2rVisible"
      :title="p2rDialogTitle"
      width="560px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-loading="p2rLoading" class="p2r-panel">
        <div class="p2r-toolbar">
          <el-checkbox v-model="p2rStrictly">父子节点独立</el-checkbox>
          <div class="p2r-actions">
            <el-button :icon="ArrowDown" @click="expandAllPermissions(true)">全部展开</el-button>
            <el-button :icon="ArrowUp" @click="expandAllPermissions(false)">全部折叠</el-button>
          </div>
        </div>
        <el-tree
          ref="p2rTreeRef"
          :data="permissionTree"
          :props="treeProps"
          node-key="id"
          show-checkbox
          :check-strictly="p2rStrictly"
          class="p2r-tree"
        />
      </div>
      <template #footer>
        <el-button @click="p2rVisible = false">取消</el-button>
        <el-button type="primary" :loading="p2rSubmitting" @click="handleConfirmAssignPermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.p2r-panel {
  min-height: 360px;
  max-height: 60vh;
  overflow-y: auto;
}

.p2r-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.p2r-actions {
  display: flex;
  gap: 8px;
}

.p2r-tree {
  padding-top: 4px;
}
</style>
