<script lang="ts">
export default { name: 'Gb28181Group' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { GbGroup } from '@/types/api'
import {
  getGbGroupTreeApi,
  saveGbGroupApi,
  updateGbGroupApi,
  deleteGbGroupApi,
} from '@/api/gb28181'

/** 默认分组主键（后端约定固定 0000，前端据此禁用编辑/删除） */
const DEFAULT_GROUP_ID = '0000'

const loading = ref(false)
const treeData = ref<GbGroup[]>([])
const tableRef = ref()
const allExpanded = ref(true)

// ---------------- 编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const form = ref<Partial<GbGroup>>(makeEmpty())

function makeEmpty(): Partial<GbGroup> {
  return {
    id: '',
    groupId: '',
    name: '',
    parentId: '0',
    sort: 0,
  }
}

/** 国标ID第11-13位校验：215业务分组 / 216虚拟组织 */
function validateGroupId(_r: unknown, val: string, cb: (e?: Error) => void) {
  if (!val) return cb(new Error('请输入国标ID'))
  if (!/^\d{20}$/.test(val)) return cb(new Error('国标ID必须是20位数字'))
  const code = val.substring(10, 13)
  if (code !== '215' && code !== '216') {
    return cb(new Error('国标ID第11-13位必须是 215（业务分组）或 216（虚拟组织）'))
  }
  cb()
}

const rules = {
  name: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
  groupId: [{ required: true, validator: validateGroupId, trigger: 'blur' }],
}

/** 由国标ID第11-13位推导类型标签 */
function typeLabel(groupId?: string) {
  const code = groupId && groupId.length >= 13 ? groupId.substring(10, 13) : ''
  if (code === '216') return '虚拟组织'
  if (code === '215') return '业务分组'
  return '-'
}

function typeTagType(groupId?: string) {
  return groupId && groupId.substring(10, 13) === '216' ? 'warning' : 'primary'
}

function isDefaultGroup(row: GbGroup) {
  return row.id === DEFAULT_GROUP_ID
}

/** 父级下拉选项：根 + 全部分组（树形）；编辑时排除自身及其子树，避免成环 */
const parentOptions = ref<GbGroup[]>([])

/** 深拷贝树并剔除 excludeId 节点（连带其子树一并剔除） */
function cloneTreeExclude(rows: GbGroup[], excludeId?: string): GbGroup[] {
  const result: GbGroup[] = []
  rows.forEach((row) => {
    if (excludeId && row.id === excludeId) return
    result.push({
      ...row,
      children: row.children?.length ? cloneTreeExclude(row.children, excludeId) : [],
    })
  })
  return result
}

function buildParentOptions(excludeId?: string) {
  parentOptions.value = [
    { id: '0', groupId: '', name: '根节点', children: cloneTreeExclude(treeData.value, excludeId) } as GbGroup,
  ]
}

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const res = await getGbGroupTreeApi()
    treeData.value = res.data
  } finally {
    loading.value = false
  }
}

// ---------------- 编辑/新增 ----------------
function handleAdd(parent?: GbGroup) {
  isEdit.value = false
  dialogTitle.value = parent ? `在「${parent.name}」下新增` : '新增分组'
  form.value = makeEmpty()
  if (parent) form.value.parentId = parent.id
  buildParentOptions()
  dialogVisible.value = true
}

function handleEdit(row: GbGroup) {
  isEdit.value = true
  dialogTitle.value = '编辑分组'
  form.value = { ...row, children: undefined }
  // 编辑时排除自身及子树，防止把节点挂到自己的后代下形成环
  buildParentOptions(row.id)
  dialogVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateGbGroupApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveGbGroupApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: GbGroup) {
  if (isDefaultGroup(row)) {
    ElMessage.warning('默认分组不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除分组 "${row.name}" 吗？其下通道将回落到默认分组，子节点回落到根节点。`,
      '提示',
      { type: 'warning' },
    )
    await deleteGbGroupApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

function toggleExpandAll() {
  allExpanded.value = !allExpanded.value
  setTreeExpanded(treeData.value, allExpanded.value)
}

/** 弹窗打开后清掉上一次残留的校验红字 */
function handleDialogOpened() {
  formRef.value?.clearValidate()
}

function setTreeExpanded(rows: GbGroup[], expanded: boolean) {
  rows.forEach((row) => {
    tableRef.value?.toggleRowExpansion(row, expanded)
    if (row.children?.length) setTreeExpanded(row.children, expanded)
  })
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="group-page">
    <el-card shadow="hover">
      <div class="table-toolbar">
        <el-button v-permission="'gb28181:group:add'" type="primary" :icon="Plus" @click="handleAdd()">新增</el-button>
        <el-button @click="toggleExpandAll">
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
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
        max-height="calc(100vh - 220px)"
      >
        <el-table-column prop="name" label="目录名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="groupId" label="国标编码" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" align="center" min-width="110">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.groupId)" disable-transitions>
              {{ typeLabel(row.groupId) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" align="center" min-width="80" />
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="新增子节点">
                <el-button
                  v-permission="'gb28181:group:add'"
                  type="primary"
                  link
                  :icon="Plus"
                  @click="handleAdd(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'gb28181:group:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEdit(row)"
                />
              </el-tooltip>
              <el-tooltip :content="isDefaultGroup(row) ? '默认分组不可删除' : '删除'">
                <el-button
                  v-permission="'gb28181:group:delete'"
                  type="danger"
                  link
                  :icon="Delete"
                  :disabled="isDefaultGroup(row)"
                  @click="handleDelete(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" :close-on-click-modal="false" @opened="handleDialogOpened">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            placeholder="不选则挂在根节点"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分组名称" prop="name">
          <el-input v-model="form.name" placeholder="上报上级 Catalog 的目录名" />
        </el-form-item>
        <el-form-item label="国标编码" prop="groupId">
          <el-input
            v-model="form.groupId"
            placeholder="20位数字；第11-13位 215业务分组 / 216虚拟组织"
          />
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.group-page {
  height: 100%;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.btn-icon {
  margin-right: 4px;
}
</style>
