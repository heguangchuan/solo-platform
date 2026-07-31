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
  GbShareTreeNode,
} from '@/types/api'
import {
  getGbParentPlatformPageApi,
  saveGbParentPlatformApi,
  updateGbParentPlatformApi,
  deleteGbParentPlatformApi,
  batchDeleteGbParentPlatformApi,
  getParentPlatformServerConfigApi,
  getParentPlatformShareTreeApi,
  getParentPlatformSharedApi,
  saveParentPlatformSharedApi,
} from '@/api/gb28181'

const loading = ref(false)
const tableData = ref<GbParentPlatform[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<GbParentPlatform[]>([])

const searchForm = ref<GbParentPlatformPageRequest>({ page: 1, pageSize: 10 })

// 本平台 SIP 配置（新增时回显为默认值）
const serverConfig = ref<{ deviceGbId: string; deviceIp: string; devicePort: number; domain: string }>({
  deviceGbId: '', deviceIp: '', devicePort: 5060, domain: '',
})

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
    recvIp: '',
    deviceGbId: '',
    deviceIp: '',
    devicePort: 5060,
    username: '',
    password: '',
    expires: 3600,
    keepTimeout: 60,
    transport: 'UDP',
    characterSet: 'GB2312',
    enabled: 1,
  }
}

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
  deviceGbId: [
    { required: true, message: '请输入本平台国标编号（20位）', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '国标编号必须是20位数字', trigger: 'blur' },
  ],
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
  form.value = {
    ...makeEmpty(),
    deviceGbId: serverConfig.value.deviceGbId,
    deviceIp: serverConfig.value.deviceIp,
    devicePort: serverConfig.value.devicePort,
  }
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

// ---------------- 通道推送（共享目录 + 通道） ----------------
const shareVisible = ref(false)
const shareLoading = ref(false)
const shareSubmitting = ref(false)
const shareTreeRef = ref<any>()
const shareTree = ref<GbShareTreeNode[]>([])   // 全平台共用同一棵资源目录树，首次加载后缓存
const shareTreeProps = { label: 'label', children: 'children' }
const sharePlatform = ref<GbParentPlatform | null>(null)

/** 递归收集所有通道叶子节点 id（c_ 前缀），用于过滤回填避免父节点强制勾选 */
function collectChannelIds(nodes: GbShareTreeNode[] | undefined, into: Set<string>) {
  if (!nodes?.length) return
  nodes.forEach((n) => {
    if (n.type === 'CHANNEL') into.add(n.id)
    if (n.children?.length) collectChannelIds(n.children, into)
  })
}

async function handleShare(row: GbParentPlatform) {
  sharePlatform.value = row
  shareVisible.value = true
  shareLoading.value = true
  let checkedLeaves: string[] = []
  try {
    if (!shareTree.value.length) {
      const treeRes = await getParentPlatformShareTreeApi()
      shareTree.value = treeRes.data
    }
    const sharedRes = await getParentPlatformSharedApi(row.id)
    // 只回填叶子（通道）节点。non-strict 模式下 setCheckedKeys 传入父分组会强制勾选其全部子通道，
    // 从而把"分组下只共享部分通道"的真实状态吃掉；父分组的勾选态由子节点联动自然得出。
    const leafSet = new Set<string>()
    collectChannelIds(shareTree.value, leafSet)
    checkedLeaves = (sharedRes.data.channelIds || []).filter((id) => leafSet.has(id))
  } finally {
    shareLoading.value = false
  }
  // loading=false 后 v-loading 容器内的 el-tree 才渲染完，等 DOM 更新再回填
  await nextTick()
  shareTreeRef.value?.setCheckedKeys([])
  shareTreeRef.value?.setCheckedKeys(checkedLeaves)
}

async function handleConfirmShare() {
  if (!sharePlatform.value) return
  // getCheckedKeys + getHalfCheckedKeys：把半选的父分组也算上，否则保存后重开会丢父分组本身
  const checked = (shareTreeRef.value?.getCheckedKeys() || []) as string[]
  const half = (shareTreeRef.value?.getHalfCheckedKeys() || []) as string[]
  const all = [...checked, ...half]
  const groupIds = all.filter((id) => id.startsWith('g_'))
  const channelIds = all.filter((id) => id.startsWith('c_'))
  shareSubmitting.value = true
  try {
    await saveParentPlatformSharedApi(sharePlatform.value.id, { groupIds, channelIds })
    ElMessage.success('推送配置已保存')
    shareVisible.value = false
  } finally {
    shareSubmitting.value = false
  }
}

onMounted(async () => {
  // 并行加载列表和本平台配置
  fetchData()
  try {
    const res = await getParentPlatformServerConfigApi()
    serverConfig.value = res.data
  } catch {
    // 配置获取失败不影响列表展示
  }
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" top="5vh" :close-on-click-modal="false">
      <div class="dialog-content">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="平台名称" prop="name">
            <el-input v-model="form.name" placeholder="上级平台显示名称" :disabled="isView" />
          </el-form-item>
          <el-form-item label="国标编码" prop="serverGbId">
            <el-input v-model="form.serverGbId" placeholder="上级平台20位编码" :disabled="isEdit" />
          </el-form-item>
          <el-form-item label="国标域" prop="serverGbDomain">
            <el-input v-model="form.serverGbDomain" placeholder="通常为编码前10位" :disabled="isView" />
          </el-form-item>
          <el-form-item label="上级地址" prop="serverIp">
            <el-input v-model="form.serverIp" placeholder="上级 SIP 服务器 IP" :disabled="isView" />
          </el-form-item>
          <el-form-item label="上级端口" prop="serverPort">
            <el-input v-model.number="form.serverPort" placeholder="上级 SIP 服务器端口" :disabled="isView" />
          </el-form-item>
          <el-form-item label="收流地址" prop="recvIp">
            <el-input v-model="form.recvIp" :disabled="isView" />
          </el-form-item>
          <el-form-item label="本地编号" prop="deviceGbId">
            <el-input v-model="form.deviceGbId" placeholder="本平台20位国标编号" disabled />
          </el-form-item>
          <el-form-item label="本地地址">
            <el-input v-model="form.deviceIp" placeholder="留空取全局配置IP" disabled />
          </el-form-item>
          <el-form-item label="本地端口">
            <el-input v-model.number="form.devicePort" placeholder="留空取全局配置端口" disabled />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="默认同本地编号" :disabled="isView" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="SIP 注册密码" :disabled="isView" />
          </el-form-item>
          <el-form-item label="注册期限">
            <el-input v-model.number="form.expires" placeholder="注册有效期" :disabled="isView">
              <template #append>秒</template>
            </el-input>
          </el-form-item>
          <el-form-item label="心跳间隔">
            <el-input v-model.number="form.keepTimeout" placeholder="心跳发送间隔" :disabled="isView">
              <template #append>秒</template>
            </el-input>
          </el-form-item>
          <el-form-item label="传输协议">
            <el-select v-model="form.transport" :disabled="isView">
              <el-option label="UDP" value="UDP" />
              <el-option label="TCP" value="TCP" />
            </el-select>
          </el-form-item>
          <el-form-item label="字符编码">
            <el-select v-model="form.characterSet" :disabled="isView">
              <el-option label="GB2312" value="GB2312" />
              <el-option label="UTF-8" value="UTF-8" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" :disabled="isView" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ isView ? '关 闭' : '取 消' }}</el-button>
        <el-button v-if="!isView" type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 通道推送（共享目录 + 通道）弹窗 -->
    <el-dialog
      v-model="shareVisible"
      title="通道推送"
      width="560px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-loading="shareLoading" style="min-height: 300px">
        <el-tree
          v-if="!shareLoading"
          ref="shareTreeRef"
          :data="shareTree"
          :props="shareTreeProps"
          node-key="id"
          show-checkbox
          default-expand-all
          class="share-tree"
        />
      </div>
      <template #footer>
        <el-button @click="shareVisible = false">取 消</el-button>
        <el-button type="primary" :loading="shareSubmitting" @click="handleConfirmShare">保 存</el-button>
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
}
/* 共享目录树：限高滚动，避免通道过多撑爆弹窗 */
.share-tree {
  max-height: 460px;
  overflow-y: auto;
}
</style>
