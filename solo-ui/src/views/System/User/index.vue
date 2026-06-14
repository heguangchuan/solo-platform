<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { SysUser, UserRequest, UserPageRequest } from '@/types/api'
import { getUserPageApi, addUserApi, editUserApi, switchUserStatusApi, deleteUserApi, deleteUserBatchApi } from '@/api/user'

const loading = ref(false)
const tableData = ref<SysUser[]>([])
const selectedRows = ref<SysUser[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<UserPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const avatarPreview = ref('')
const form = ref<UserRequest>({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  status: 1,
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
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
    const res = await getUserPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } catch {
    // handled by interceptor
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

function resetForm() {
  avatarPreview.value = ''
  form.value = {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    avatar: '',
    status: 1,
  }
}

function handleAdd() {
  dialogTitle.value = '新增用户'
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: SysUser) {
  dialogTitle.value = '编辑用户'
  avatarPreview.value = row.avatar
  form.value = {
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    avatar: row.avatar,
    status: row.status,
  }
  dialogVisible.value = true
}

function handleAvatarSelect(file: any) {
  avatarPreview.value = URL.createObjectURL(file.raw)
  return false
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    if (dialogTitle.value === '新增用户') {
      await addUserApi(form.value)
    } else {
      const current = tableData.value.find((item) => item.username === form.value.username)
      if (current) {
        await editUserApi(current.id, form.value)
      }
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

async function handleToggleStatus(row: SysUser) {
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await switchUserStatusApi(row.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '已启用' : '已禁用')
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: SysUser[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', {
      type: 'warning',
    })
    await deleteUserBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(`确定要删除用户 "${row.nickname}" 吗？`, '提示', {
      type: 'warning',
    })
    await deleteUserApi(row.id)
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="searchForm.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'system:user:list'" type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <el-button v-permission="'system:user:add'" type="primary" @click="handleAdd">新增</el-button>
          <el-button v-permission="'system:user:delete'" type="primary" @click="handleBatchDelete">删除</el-button>
          <el-button type="primary">导入</el-button>
          <el-button type="primary">导出</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickname" label="昵称" width="150" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:user:switch'"
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button v-permission="'system:user:delete'" type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="680px"
      destroy-on-close
    >
      <div class="user-form-layout">
        <div class="avatar-panel">
          <el-upload
            class="avatar-uploader"
            action="#"
            :show-file-list="false"
            :before-upload="handleAvatarSelect"
          >
            <div class="avatar-box">
              <el-avatar v-if="avatarPreview" :size="88" :src="avatarPreview" />
              <el-icon v-else class="avatar-placeholder"><Plus /></el-icon>
            </div>
          </el-upload>
          <div class="avatar-title">用户头像</div>
          <div class="avatar-tip">点击上传头像，当前仅展示样式</div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="86px" class="user-form">
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入登录账号"
              :disabled="dialogTitle === '编辑用户'"
            />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" placeholder="请输入用户昵称" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱地址" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="请输入手机号码" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.user-form-layout {
  display: flex;
  gap: 32px;
  padding: 8px 4px 0;
}

.avatar-panel {
  width: 180px;
  padding: 24px 20px;
  border-radius: 12px;
  background: #f7f8fb;
  text-align: center;
  flex-shrink: 0;
}

.avatar-uploader {
  display: flex;
  justify-content: center;
}

.avatar-box {
  width: 104px;
  height: 104px;
  border: 1px dashed #c8d0dd;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}

.avatar-box:hover {
  border-color: #409eff;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.12);
}

.avatar-placeholder {
  font-size: 28px;
  color: #9aa6b2;
}

.avatar-title {
  margin-top: 16px;
  font-weight: 600;
  color: #303133;
}

.avatar-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}

.user-form {
  flex: 1;
  padding-right: 8px;
}
</style>
