<script lang="ts">
export default { name: 'SystemServer' }
</script>
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Monitor, Close, Check, SwitchButton } from '@element-plus/icons-vue'
import type { SysServer, ServerRequest, ServerPageRequest } from '@/types/api'
import XTerminal from '@/components/XTerminal.vue'
import ServerFileTree from '@/components/ServerFileTree.vue'
import { useUserStore } from '@/stores/user'
import {
  getServerPageApi,
  addServerApi,
  editServerApi,
  deleteServerApi,
  deleteServerBatchApi,
} from '@/api/server'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref<SysServer[]>([])
const selectedRows = ref<SysServer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchForm = ref<ServerPageRequest>({ page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const editingId = ref<string>('')
const terminalVisible = ref(false)
const terminalServer = ref<SysServer | null>(null)
const terminalRef = ref<InstanceType<typeof XTerminal>>()
const fileTreeCollapsed = ref(false)
const terminalStatus = ref<'connecting' | 'connected' | 'closed' | 'error'>('closed')
const terminalStatusReason = ref('')
const form = ref<ServerRequest>({
  serverName: '',
  host: '',
  port: 22,
  username: '',
  authType: 'PASSWORD',
  password: '',
  privateKey: '',
  osType: '',
  status: 1,
  description: '',
})

const rules = {
  serverName: [{ required: true, message: '请输入主机名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [
    { required: true, message: '请输入SSH端口', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: number, callback: (error?: Error) => void) => {
        if (!value || value < 1 || value > 65535) {
          callback(new Error('端口范围必须为1-65535'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  username: [{ required: true, message: '请输入登录用户名', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
}

const statusTypeMap: Record<number, string> = {
  1: 'success',
  0: 'danger',
}

const terminalWsUrl = computed(() => {
  if (!terminalServer.value) return ''
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/api/ws/terminal/${terminalServer.value.id}?token=${encodeURIComponent(userStore.token)}`
})

const terminalStatusText = computed(() => {
  if (terminalStatus.value === 'connecting') return '连接中'
  if (terminalStatus.value === 'connected') return '已连接'
  if (terminalStatus.value === 'error') return '连接异常'
  return '已断开'
})

const terminalStatusType = computed(() => {
  if (terminalStatus.value === 'connected') return 'success'
  if (terminalStatus.value === 'connecting') return 'warning'
  if (terminalStatus.value === 'error') return 'danger'
  return 'info'
})

function authTypeText(type: string) {
  return type === 'PRIVATE_KEY' ? '密钥' : '密码'
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
    const res = await getServerPageApi(params)
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

function resetForm() {
  form.value = {
    serverName: '',
    host: '',
    port: 22,
    username: '',
    authType: 'PASSWORD',
    password: '',
    privateKey: '',
    osType: '',
    status: 1,
    description: '',
  }
}

function handleAdd() {
  dialogTitle.value = '新增服务器'
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: SysServer) {
  dialogTitle.value = '编辑服务器'
  editingId.value = row.id
  form.value = {
    serverName: row.serverName,
    host: row.host,
    port: row.port,
    username: row.username,
    authType: row.authType === 'PRIVATE_KEY' ? 'PRIVATE_KEY' : 'PASSWORD',
    password: '',
    privateKey: '',
    osType: row.osType || '',
    status: row.status,
    description: row.description || '',
  }
  dialogVisible.value = true
}

async function handleConfirm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!editingId.value) {
    if (form.value.authType === 'PASSWORD' && !form.value.password) {
      ElMessage.warning('请输入密码')
      return
    }
    if (form.value.authType === 'PRIVATE_KEY' && !form.value.privateKey) {
      ElMessage.warning('请输入私钥')
      return
    }
  }

  try {
    if (!editingId.value) {
      await addServerApi(form.value)
    } else {
      await editServerApi(editingId.value, form.value)
    }
    ElMessage.success('操作成功')
    dialogVisible.value = false
    fetchData()
  } catch {
    // handled
  }
}

function handleSelectionChange(rows: SysServer[]) {
  selectedRows.value = rows
}

async function handleBatchDelete() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要删除的数据')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 台服务器吗？`, '提示', {
      type: 'warning',
    })
    await deleteServerBatchApi(selectedRows.value.map((row) => row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

async function handleDelete(row: SysServer) {
  try {
    await ElMessageBox.confirm(`确定要删除服务器 "${row.serverName}" 吗？删除后 Web 终端和文件管理入口将不可用。`, '提示', { type: 'warning' })
    await deleteServerApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled or error
  }
}

function handleOpenTerminal(row: SysServer) {
  if (row.status !== 1) {
    ElMessage.warning('服务器已停用，无法连接终端')
    return
  }
  terminalServer.value = row
  terminalStatus.value = 'closed'
  terminalStatusReason.value = ''
  terminalVisible.value = true
}

function handleTerminalStatus(payload: { status: 'connecting' | 'connected' | 'closed' | 'error'; reason?: string }) {
  terminalStatus.value = payload.status
  terminalStatusReason.value = payload.reason || ''
}

function handleTerminalClose() {
  terminalRef.value?.disconnect()
  terminalVisible.value = false
}

function clearTerminal() {
  terminalRef.value?.clear()
}

function reconnectTerminal() {
  terminalRef.value?.connect()
}

function disconnectTerminal() {
  terminalRef.value?.disconnect()
}

function handleFileTreeCollapseChange(value: boolean) {
  fileTreeCollapsed.value = value
  setTimeout(() => terminalRef.value?.fit(), 120)
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="hover" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="服务器名称">
          <el-input v-model="searchForm.serverName" placeholder="请输入服务器名称" clearable />
        </el-form-item>
        <el-form-item label="主机地址">
          <el-input v-model="searchForm.host" placeholder="请输入IP或域名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'server:list'" type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" style="margin-top: 16px">
      <div class="table-toolbar">
        <el-button v-permission="'server:add'" type="primary" :icon="Plus" @click="handleAdd">新增</el-button>
        <el-button v-permission="'server:delete'" type="danger" :icon="Delete" @click="handleBatchDelete">删除</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="handleSelectionChange" max-height="calc(100vh - 400px)">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="serverName" label="服务器名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="host" label="主机地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="port" label="端口" width="90" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="认证方式" width="100">
          <template #default="{ row }">
            {{ authTypeText(row.authType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status] || 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <div>
              <el-tooltip content="终端">
                <el-button
                  v-permission="'server:terminal'"
                  type="primary"
                  link
                  :icon="Monitor"
                  :disabled="row.status !== 1"
                  @click="handleOpenTerminal(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑">
                <el-button
                  v-permission="'server:edit'"
                  type="primary"
                  link
                  :icon="Edit"
                  @click="handleEdit(row)"
                />
              </el-tooltip>
              <el-tooltip content="删除">
                <el-button
                  v-permission="'server:delete'"
                  type="danger"
                  link
                  :icon="Delete"
                  @click="handleDelete(row)"
                />
              </el-tooltip>
            </div>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="secret-tip"
      >
        <template #default>
          认证信息不会在编辑时回显；编辑时留空表示保持原认证信息不变。
        </template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="主机名称" prop="serverName">
          <el-input v-model="form.serverName" placeholder="请输入主机名称" />
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="请输入主机地址" />
        </el-form-item>
        <el-form-item label="SSH端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 180px" />
        </el-form-item>
        <el-form-item label="登录用户" prop="username">
          <el-input v-model="form.username" placeholder="请输入登录用户" />
        </el-form-item>
        <el-form-item label="认证方式" prop="authType">
          <el-radio-group v-model="form.authType">
            <el-radio value="PASSWORD">密码认证</el-radio>
            <el-radio value="PRIVATE_KEY">密钥认证</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.authType === 'PASSWORD'" label="主机密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="editingId ? '请输入以覆盖，不填则保持不变' : '请输入主机密码'" />
        </el-form-item>
        <el-form-item v-if="form.authType === 'PRIVATE_KEY'" label="主机私钥">
          <el-input v-model="form.privateKey" type="textarea" :rows="4" :placeholder="editingId ? '请输入以覆盖，不填则保持不变' : '请输入主机私钥'" />
        </el-form-item>
        <el-form-item label="操作系统">
          <el-input v-model="form.osType" placeholder="如：Ubuntu / CentOS / Rocky Linux" />
        </el-form-item>
        <el-form-item label="主机状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="主机描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入主机描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :icon="Close" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="terminalVisible"
      width="96vw"
      top="3vh"
      destroy-on-close
      :show-close="false"
      :close-on-click-modal="false"
      :body-style="{ overflowY: 'auto' }"
      class="terminal-dialog"
      @close="handleTerminalClose"
    >
      <div class="terminal-dialog-header">
        <div class="mac-dots" aria-hidden="true">
          <span class="dot red" @click="handleTerminalClose" />
          <span class="dot yellow" />
          <span class="dot green" />
        </div>
        <div class="terminal-meta">
          <span class="server-name">{{ terminalServer?.serverName }}</span>
          <span>{{ terminalServer?.host }}:{{ terminalServer?.port }}</span>
          <span class="terminal-tip">真实 SSH 终端，支持任意命令，请谨慎操作</span>
          <el-tag :type="terminalStatusType" size="small">{{ terminalStatusText }}</el-tag>
          <span v-if="terminalStatusReason" class="terminal-status-reason">{{ terminalStatusReason }}</span>
        </div>
        <div class="terminal-actions">
          <el-button class="terminal-tool-button" circle :icon="Delete" title="清屏" @click="clearTerminal" />
          <el-button class="terminal-tool-button" circle :icon="Refresh" title="重连" @click="reconnectTerminal" />
          <el-button class="terminal-tool-button danger" circle :icon="SwitchButton" title="断开" @click="disconnectTerminal" />
          <el-button class="terminal-tool-button close" circle :icon="Close" title="关闭" @click="handleTerminalClose" />
        </div>
      </div>
      <div class="terminal-dialog-body">
        <div class="terminal-shell-layout">
          <ServerFileTree
            v-if="terminalVisible && terminalServer"
            :server-id="terminalServer.id"
            :collapsed="fileTreeCollapsed"
            @update:collapsed="handleFileTreeCollapseChange"
          />
          <div class="terminal-main-panel">
            <XTerminal
              v-if="terminalVisible && terminalServer"
              ref="terminalRef"
              :ws-url="terminalWsUrl"
              @status="handleTerminalStatus"
            />
          </div>
        </div>
      </div>
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

.secret-tip {
  margin-bottom: 16px;
}

.terminal-dialog-header {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  min-height: 30px;
}

.mac-dots {
  display: flex;
  align-items: center;
  gap: 7px;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.12);
}

.dot.red {
  background: #ff5f57;
  cursor: pointer;
}

.dot.yellow {
  background: #febc2e;
}

.dot.green {
  background: #28c840;
}

.terminal-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.server-name {
  color: var(--el-text-color-primary);
  font-weight: 600;
  font-size: 13px;
}

.terminal-tip {
  color: var(--el-color-warning);
}

.terminal-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.terminal-tool-button {
  width: 24px !important;
  height: 24px !important;
  padding: 0 !important;
  border: 0;
  color: #64748b;
  background: #f1f5f9;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
}

.terminal-tool-button:hover {
  color: #1f2937;
  background: #e2e8f0;
}

.terminal-tool-button.danger {
  color: #b45309;
  background: #fef3c7;
}

.terminal-tool-button.danger:hover {
  color: #92400e;
  background: #fde68a;
}

.terminal-tool-button.close {
  color: #dc2626;
  background: #fee2e2;
}

.terminal-tool-button.close:hover {
  color: #991b1b;
  background: #fecaca;
}

.terminal-status-reason {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.terminal-dialog-body {
  height: calc(90vh - 76px);
  min-height: 0;
}

.terminal-shell-layout {
  height: 100%;
  display: flex;
  min-width: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
}

.terminal-main-panel {
  flex: 1;
  min-width: 0;
  height: 100%;
}

.terminal-dialog :deep(.el-dialog__header) {
  display: none;
}

.terminal-dialog :deep(.el-dialog__body) {
  height: 90vh;
  max-height: 90vh;
  overflow: hidden;
  padding: 10px 12px 12px;
}

.terminal-dialog :deep(.el-dialog) {
  height: 90vh;
  margin: 0 auto;
  border-radius: 8px;
  overflow: hidden;
}
</style>
