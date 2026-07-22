<script lang="ts">
export default { name: 'SettingDict' }
</script>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, List } from '@element-plus/icons-vue'
import type {
  SysDictType,
  DictTypeRequest,
  DictTypePageRequest,
  SysDictData,
  DictDataRequest,
  DictDataPageRequest,
} from '@/types/api'
import {
  getDictTypePageApi,
  addDictTypeApi,
  editDictTypeApi,
  deleteDictTypeApi,
  deleteDictTypeBatchApi,
  getDictDataPageApi,
  addDictDataApi,
  editDictDataApi,
  deleteDictDataApi,
  deleteDictDataBatchApi,
} from '@/api/dict'
import { clearDictCache } from '@/composables/useDict'

// ====== 字典类型 ======
const typeLoading = ref(false)
const typeTable = ref<SysDictType[]>([])
const typeSelected = ref<SysDictType[]>([])
const typeTotal = ref(0)
const typePage = ref(1)
const typeSize = ref(10)
const typeSearch = ref<DictTypePageRequest>({ page: 1, pageSize: 10 })

const typeDialogVisible = ref(false)
const typeDialogTitle = ref('')
const typeFormRef = ref()
const typeEditingId = ref('')
const typeForm = ref<DictTypeRequest>({
  dictName: '',
  dictType: '',
  status: 1,
})
const typeRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型编码', trigger: 'blur' }],
}

async function fetchTypes() {
  typeLoading.value = true
  try {
    const params = { ...typeSearch.value, page: typePage.value, pageSize: typeSize.value }
    const res = await getDictTypePageApi(params)
    typeTable.value = res.data.rows
    typeTotal.value = res.data.total
  } catch {
    // handled
  } finally {
    typeLoading.value = false
  }
}

function handleTypeSearch() {
  typePage.value = 1
  fetchTypes()
}

function handleTypeReset() {
  typeSearch.value = { page: 1, pageSize: 10 }
  handleTypeSearch()
}

function handleTypePageChange(page: number) {
  typePage.value = page
  fetchTypes()
}

function handleTypeSizeChange(size: number) {
  typeSize.value = size
  typePage.value = 1
  fetchTypes()
}

function handleTypeSelectionChange(rows: SysDictType[]) {
  typeSelected.value = rows
}

function handleAddType() {
  typeDialogTitle.value = '新增字典类型'
  typeEditingId.value = ''
  typeForm.value = { dictName: '', dictType: '', status: 1 }
  typeDialogVisible.value = true
}

function handleEditType(row: SysDictType) {
  typeDialogTitle.value = '编辑字典类型'
  typeEditingId.value = row.id
  typeForm.value = {
    dictName: row.dictName,
    dictType: row.dictType,
    status: row.status,
  }
  typeDialogVisible.value = true
}

async function handleConfirmType() {
  const valid = await typeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (!typeEditingId.value) {
      await addDictTypeApi(typeForm.value)
    } else {
      await editDictTypeApi(typeEditingId.value, typeForm.value)
    }
    ElMessage.success('操作成功')
    typeDialogVisible.value = false
    fetchTypes()
  } catch {
    // handled
  }
}

async function handleDeleteType(row: SysDictType) {
  try {
    await ElMessageBox.confirm(
      `确定要删除字典类型 "${row.dictName}" 吗？其下的字典数据将一并删除。`,
      '提示',
      { type: 'warning' },
    )
    await deleteDictTypeApi(row.id)
    ElMessage.success('删除成功')
    fetchTypes()
  } catch {
    // cancelled
  }
}

async function handleBatchDeleteType() {
  if (!typeSelected.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${typeSelected.value.length} 条数据吗？其下的字典数据将一并删除。`,
      '提示',
      { type: 'warning' },
    )
    await deleteDictTypeBatchApi(typeSelected.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchTypes()
  } catch {
    // cancelled
  }
}

// ====== 字典数据弹窗 ======
const dataDrawerVisible = ref(false)
const activeType = ref<SysDictType | null>(null)

const dataLoading = ref(false)
const dataTable = ref<SysDictData[]>([])
const dataSelected = ref<SysDictData[]>([])
const dataTotal = ref(0)
const dataPage = ref(1)
const dataSize = ref(10)
const dataSearch = ref<DictDataPageRequest>({ page: 1, pageSize: 10 })

const dataDialogVisible = ref(false)
const dataDialogTitle = ref('')
const dataFormRef = ref()
const dataEditingId = ref('')
const dataForm = ref<DictDataRequest>({
  dictType: '',
  dictLabel: '',
  dictValue: '',
  sort: 1,
  status: 1,
})
const dataRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }],
}

function handleOpenData(row: SysDictType) {
  activeType.value = row
  dataSearch.value = { page: 1, pageSize: 10, dictType: row.dictType }
  dataPage.value = 1
  dataSize.value = 10
  dataSelected.value = []
  dataDrawerVisible.value = true
  fetchData()
}

async function fetchData() {
  if (!activeType.value) return
  dataLoading.value = true
  try {
    const params = {
      ...dataSearch.value,
      page: dataPage.value,
      pageSize: dataSize.value,
      dictType: activeType.value.dictType,
    }
    const res = await getDictDataPageApi(params)
    dataTable.value = res.data.rows
    dataTotal.value = res.data.total
  } catch {
    // handled
  } finally {
    dataLoading.value = false
  }
}

function handleDataSearch() {
  dataPage.value = 1
  fetchData()
}

function handleDataReset() {
  dataSearch.value = { page: 1, pageSize: 10, dictType: activeType.value?.dictType }
  handleDataSearch()
}

function handleDataPageChange(page: number) {
  dataPage.value = page
  fetchData()
}

function handleDataSizeChange(size: number) {
  dataSize.value = size
  dataPage.value = 1
  fetchData()
}

function handleDataSelectionChange(rows: SysDictData[]) {
  dataSelected.value = rows
}

function handleAddData() {
  if (!activeType.value) return
  dataDialogTitle.value = '新增字典数据'
  dataEditingId.value = ''
  dataForm.value = {
    dictType: activeType.value.dictType,
    dictLabel: '',
    dictValue: '',
    sort: 1,
    status: 1,
  }
  dataDialogVisible.value = true
}

function handleEditData(row: SysDictData) {
  dataDialogTitle.value = '编辑字典数据'
  dataEditingId.value = row.id
  dataForm.value = {
    dictType: row.dictType,
    dictLabel: row.dictLabel,
    dictValue: row.dictValue,
    sort: row.sort,
    status: row.status,
  }
  dataDialogVisible.value = true
}

function refreshDataAfterChange() {
  if (activeType.value) clearDictCache(activeType.value.dictType)
  fetchData()
}

async function handleConfirmData() {
  const valid = await dataFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (!dataEditingId.value) {
      await addDictDataApi(dataForm.value)
    } else {
      await editDictDataApi(dataEditingId.value, dataForm.value)
    }
    ElMessage.success('操作成功')
    dataDialogVisible.value = false
    refreshDataAfterChange()
  } catch {
    // handled
  }
}

async function handleDeleteData(row: SysDictData) {
  try {
    await ElMessageBox.confirm(`确定要删除字典项 "${row.dictLabel}" 吗？`, '提示', { type: 'warning' })
    await deleteDictDataApi(row.id)
    ElMessage.success('删除成功')
    refreshDataAfterChange()
  } catch {
    // cancelled
  }
}

async function handleBatchDeleteData() {
  if (!dataSelected.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${dataSelected.value.length} 条数据吗？`, '提示', {
      type: 'warning',
    })
    await deleteDictDataBatchApi(dataSelected.value.map((row) => row.id))
    ElMessage.success('删除成功')
    refreshDataAfterChange()
  } catch {
    // cancelled
  }
}

const statusTypeMap: Record<number, string> = { 1: 'success', 0: 'danger' }

onMounted(() => {
  fetchTypes()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="typeSearch" inline>
        <el-form-item label="字典名称">
          <el-input v-model="typeSearch.dictName" placeholder="请输入字典名称" clearable />
        </el-form-item>
        <el-form-item label="字典编码">
          <el-input v-model="typeSearch.dictType" placeholder="请输入字典编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'setting:dict:type:list'" type="primary" :icon="Search" @click="handleTypeSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleTypeReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'setting:dict:type:add'" type="primary" :icon="Plus" @click="handleAddType">新增</el-button>
        <el-button v-permission="'setting:dict:type:delete'" type="danger" :icon="Delete" @click="handleBatchDeleteType">删除</el-button>
      </div>

      <el-table
        :data="typeTable"
        v-loading="typeLoading"
        border
        stripe
        @selection-change="handleTypeSelectionChange"
       max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="dictName" label="字典名称" min-width="160" />
        <el-table-column prop="dictType" label="字典编码" min-width="180" />
        <el-table-column label="字典状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="字典数据">
                <el-button
                  v-permission="'setting:dict:data:list'"
                  type="primary"
                  link
                  :icon="List"
                  @click="handleOpenData(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'setting:dict:type:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEditType(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'setting:dict:type:delete'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDeleteType(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="typePage"
        v-model:page-size="typeSize"
        :total="typeTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handleTypePageChange"
        @size-change="handleTypeSizeChange"
      />
    </el-card>

    <!-- 字典类型新增/编辑 dialog -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="520px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="如：用户性别" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictType">
          <el-input v-model="typeForm.dictType" placeholder="如：sys_user_sex" />
        </el-form-item>
        <el-form-item label="字典状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirmType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据 dialog -->
    <el-dialog
      v-model="dataDrawerVisible"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="dataSearch" inline class="search-form">
        <el-form-item label="字典标签">
          <el-input v-model="dataSearch.dictLabel" placeholder="请输入字典标签" clearable />
        </el-form-item>
        <el-form-item label="数据状态">
          <el-select v-model="dataSearch.status" placeholder="状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'setting:dict:data:list'" type="primary" :icon="Search" @click="handleDataSearch">
            查询
          </el-button>
          <el-button :icon="Refresh" @click="handleDataReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="dialog-actions">
        <el-button v-permission="'setting:dict:data:add'" type="primary" :icon="Plus" @click="handleAddData">新增</el-button>
        <el-button v-permission="'setting:dict:data:delete'" type="danger" :icon="Delete" @click="handleBatchDeleteData">
          删除
        </el-button>
      </div>

      <el-table
        :data="dataTable"
        v-loading="dataLoading"
        border
        stripe
        @selection-change="handleDataSelectionChange"
       max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="dictLabel" label="字典标签" min-width="120" />
        <el-table-column prop="dictValue" label="字典键值" min-width="120" />
        <el-table-column prop="sort" label="显示排序" align="center" />
        <el-table-column label="数据状态" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'setting:dict:data:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEditData(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'setting:dict:data:delete'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDeleteData(row)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="dataPage"
        v-model:page-size="dataSize"
        :total="dataTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="handleDataPageChange"
        @size-change="handleDataSizeChange"
      />
    </el-dialog>

    <!-- 字典数据新增/编辑 dialog -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="dataDialogTitle"
      width="520px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="100px">
        <el-form-item label="字典类型">
          <el-input v-model="dataForm.dictType" disabled />
        </el-form-item>
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="如：男" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="如：1" />
        </el-form-item>
        <el-form-item label="显示排序">
          <el-input-number v-model="dataForm.sort" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据状态">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirmData">确定</el-button>
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

.search-form {
  margin-bottom: 20px;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
