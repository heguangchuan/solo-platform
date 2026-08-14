<script setup lang="ts">
/**
 * 上级平台配置管理组件
 * <p>维护本平台向上级注册所需的 SIP 连接参数（基础信息 CRUD）。
 * 注册/在线状态/推送功能后续 S6.b 接通。</p>
 */
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View, Share } from '@element-plus/icons-vue'
import type {
  GbParentPlatform,
  GbParentPlatformPageRequest,
} from '@/types/api'
import {
  getGbParentPlatformPageApi,
  saveGbParentPlatformApi,
  updateGbParentPlatformApi,
  deleteGbParentPlatformApi,
  batchDeleteGbParentPlatformApi,
} from '@/api/gb28181'
import ShareDirectoryDialog from './ShareDirectoryDialog.vue'

const loading = ref(false)
const tableData = ref<GbParentPlatform[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<GbParentPlatform[]>([])

const searchForm = ref<GbParentPlatformPageRequest>({ page: 1, pageSize: 10 })

// ---------------- 新增/编辑对话框 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const isEdit = ref(false)
const isView = ref(false)  // 详情模式：所有字段只读、隐藏确定按钮
const form = ref<Partial<GbParentPlatform>>(makeEmpty())

function makeEmpty(): Partial<GbParentPlatform> {
  return {
    name: '',
    serverGbId: '',
    serverGbDomain: '',
    serverIp: '',
    serverPort: 5060,
    username: '',
    password: '',
    expires: 3600,
    keepTimeout: 60,
    transport: 'UDP',
    characterSet: 'GB2312',
    enabled: 1,
    // catalogGroup 留空 → 走全局默认（gb28181.cascade.catalog-page-size）
    catalogGroup: undefined,
  }
}

// 推送节奏：per-platform catalogGroup 可选项。1/2/4/8 是 ADR-027 收敛出的"单包安全档位"
const catalogGroupOptions = [
  { label: '默认（走全局配置）', value: undefined },
  { label: '1 条/包', value: 1 },
  { label: '2 条/包', value: 2 },
  { label: '4 条/包', value: 4 },
  { label: '8 条/包', value: 8 },
]

const rules = {
  name: [{ required: true, message: '请输入上级平台名称', trigger: 'blur' }],
  serverGbId: [
    { required: true, message: '请输入上级平台国标编码（20位）', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '国标编码必须是20位数字', trigger: 'blur' },
  ],
  serverGbDomain: [
    { required: true, message: '请输入上级平台国标域', trigger: 'blur' },
  ],
  serverIp: [{ required: true, message: '请输入上级地址 IP', trigger: 'blur' }],
  serverPort: [{ required: true, message: '请输入端口号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入认证密码', trigger: 'blur' }],
}

// ---------------- 列表 ----------------
async function fetchData() {
  loading.value = true
  try {
    const params = { ...searchForm.value, page: currentPage.value, pageSize: pageSize.value }
    const res = await getGbParentPlatformPageApi(params)
    tableData.value = res.data.rows
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

function handleReset() {
  searchForm.value = { page: 1, pageSize: 10 }
  handleSearch()
}

function handlePageChange(p: number) {
  currentPage.value = p
  fetchData()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  currentPage.value = 1
  fetchData()
}

function handleSelectionChange(rows: GbParentPlatform[]) {
  selectedRows.value = rows
}

// ---------------- 新增/编辑 ----------------
function handleAdd() {
  isEdit.value = false
  isView.value = false
  dialogTitle.value = '新增上级平台'
  form.value = makeEmpty()
  dialogVisible.value = true
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

function handleEdit(row: GbParentPlatform) {
  isEdit.value = true
  isView.value = false
  dialogTitle.value = '编辑上级平台'
  form.value = { ...row }
  dialogVisible.value = true
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

function handleView(row: GbParentPlatform) {
  isEdit.value = true          // 复用编辑布局（编码字段禁用等逻辑）
  isView.value = true
  dialogTitle.value = '上级平台详情'
  form.value = { ...row }
  dialogVisible.value = true
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateGbParentPlatformApi(form.value)
    ElMessage.success('修改成功')
  } else {
    await saveGbParentPlatformApi(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(row: GbParentPlatform) {
  try {
    await ElMessageBox.confirm(`确定要删除上级平台 "${row.name}" 吗？`, '提示', { type: 'warning' })
    await deleteGbParentPlatformApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的平台')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 个上级平台吗？`,
      '提示',
      { type: 'warning' }
    )
    const ids = selectedRows.value.map(r => r.id)
    await batchDeleteGbParentPlatformApi(ids)
    ElMessage.success('删除成功')
    fetchData()
    selectedRows.value = []
  } catch {
    // cancelled
  }
}

// ---------------- 通道推送（推送目录管理弹窗） ----------------
const shareVisible = ref(false)
const sharePlatformId = ref<number | string>('')
const sharePlatformName = ref('')

function handleShare(row: GbParentPlatform) {
  sharePlatformId.value = row.id as number
  sharePlatformName.value = row.name || row.serverGbId || '上级平台'
  shareVisible.value = true
}

onMounted(() => {
  // 本平台身份（deviceGbId / domain / ip / port）走全局 gb28181.* 配置，per-upper 表不再存（ADR-036）
  fetchData()
})
</script>
<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="上级编码">
          <el-input v-model="searchForm.serverGbId" placeholder="国标编码模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="平台名称">
          <el-input v-model="searchForm.name" placeholder="名称模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="searchForm.enabled" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button type="danger" :icon="Delete" @click="handleBatchDelete" :disabled="selectedRows.length === 0">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 420px)" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="name" label="平台名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="serverGbId" label="上级编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="serverGbDomain" label="上级域" min-width="100" show-overflow-tooltip />
        <el-table-column label="上级地址" min-width="160">
          <template #default="{ row }">{{ row.serverIp }}:{{ row.serverPort }}</template>
        </el-table-column>
        <el-table-column prop="deviceGbId" label="本地编号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="transport" label="传输" align="center" width="70" />
        <el-table-column label="启用" align="center" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '在线' : '未接入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="详情">
              <el-button type="info" link :icon="View" @click="handleView(row)" />
            </el-tooltip>
            <el-tooltip content="通道推送">
              <el-button type="success" link :icon="Share" @click="handleShare(row)" />
            </el-tooltip>
            <el-tooltip content="编辑">
              <el-button type="primary" link :icon="Edit" @click="handleEdit(row)" />
            </el-tooltip>
            <el-tooltip content="删除">
              <el-button type="danger" link :icon="Delete" @click="handleDelete(row)" />
            </el-tooltip>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="920px" top="3vh" :close-on-click-modal="false" class="parent-platform-dialog">
      <div class="dialog-content">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">

          <!-- 第一组：上级平台信息（必填，连接目标 + 注册凭据；本平台身份走全局 gb28181.*，ADR-036） -->
          <div class="form-section">
            <div class="section-title">上级平台信息</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="平台名称" prop="name">
                  <el-input v-model="form.name" placeholder="上级平台显示名称" :disabled="isView" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="国标编码" prop="serverGbId">
                  <el-input v-model="form.serverGbId" placeholder="上级平台20位编码" :disabled="isEdit" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="国标域" prop="serverGbDomain">
                  <el-input v-model="form.serverGbDomain" placeholder="通常为编码前10位" :disabled="isView" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="上级地址" prop="serverIp">
                  <el-input v-model="form.serverIp" placeholder="上级 SIP 服务器 IP" :disabled="isView" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="上级端口" prop="serverPort">
                  <el-input v-model.number="form.serverPort" placeholder="上级 SIP 服务器端口" :disabled="isView" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="注册密码" prop="password">
                  <el-input v-model="form.password" type="password" show-password placeholder="SIP 注册密码" :disabled="isView" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <!-- 第三组：推送配置（连接 + 目录推送节奏） -->
          <div class="form-section">
            <div class="section-title">推送配置</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="目录分片">
                  <el-select v-model="form.catalogGroup" :disabled="isView" clearable placeholder="默认（走全局配置）">
                    <el-option
                      v-for="opt in catalogGroupOptions"
                      :key="opt.label"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                  <div class="form-tip">每次向上级发送目录时单个 SIP 报文携带的通道数。某些品牌上级 &gt; 8 会拒收，可单独配小档位；留空走全局默认。</div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="注册期限">
                  <el-input v-model.number="form.expires" placeholder="注册有效期" :disabled="isView">
                    <template #append>秒</template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="心跳间隔">
                  <el-input v-model.number="form.keepTimeout" placeholder="心跳发送间隔" :disabled="isView">
                    <template #append>秒</template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="传输协议">
                  <el-select v-model="form.transport" :disabled="isView">
                    <el-option label="UDP" value="UDP" />
                    <el-option label="TCP" value="TCP" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="字符编码">
                  <el-select v-model="form.characterSet" :disabled="isView">
                    <el-option label="GB2312" value="GB2312" />
                    <el-option label="UTF-8" value="UTF-8" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="是否启用">
                  <el-select v-model="form.enabled" :disabled="isView">
                    <el-option label="启用" :value="1" />
                    <el-option label="禁用" :value="0" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ isView ? '关 闭' : '取 消' }}</el-button>
        <el-button v-if="!isView" type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 推送目录管理弹窗（独立组件） -->
    <ShareDirectoryDialog v-model="shareVisible" :platform-id="sharePlatformId" :platform-name="sharePlatformName" />
  </div>
</template>

<style scoped>
.search-card {
  margin-bottom: 0;
}
.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.dialog-content {
  max-height: calc(80vh - 120px);
  overflow-y: auto;
  overflow-x: hidden;
}

/* 新增 / 编辑上级平台弹窗：三段式分组 + 两列布局 */
.form-section {
  margin-bottom: 8px;
}
.form-section:last-child {
  margin-bottom: 0;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 4px 0 14px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  line-height: 1.4;
}
/* el-form-item 默认 22px margin 偏大，两列布局下压到 16px 让节奏更紧凑 */
.parent-platform-dialog :deep(.el-form-item) {
  margin-bottom: 16px;
}
/* input/select 不撑满 col-12，留出右边距让视觉更紧凑（180px 在 92px label + 920 弹窗下视觉平衡） */
.parent-platform-dialog :deep(.el-form-item .el-form-item__content) {
  max-width: 180px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
