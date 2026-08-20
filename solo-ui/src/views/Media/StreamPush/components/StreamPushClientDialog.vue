<script setup lang="ts">
/**
 * 推流方（授权）管理弹窗
 * <p>推流管理页 toolbar 「授权」按钮打开。管理 stream_push_client：新增/编辑/启停/重置 token/删除。
 * 数据量小（业务上一个客户端就够所有 stream 用），不做分页与筛选。</p>
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, CopyDocument, Delete, Edit, RefreshRight } from '@element-plus/icons-vue'
import type { StreamPushClient } from '@/types/api'
import {
  getStreamPushClientPageApi,
  saveStreamPushClientApi,
  updateStreamPushClientApi,
  resetStreamPushClientTokenApi,
  deleteStreamPushClientApi,
} from '@/api/gb28181'

const visible = ref(false)
const loading = ref(false)
const tableData = ref<StreamPushClient[]>([])

const formVisible = ref(false)
const formTitle = ref('')
const isEdit = ref(false)
const formRef = ref()
const form = ref<Partial<StreamPushClient>>({})

const rules = {
  clientId: [
    { required: true, message: '请输入推流方标识', trigger: 'blur' },
  ],
}

function open() {
  visible.value = true
  fetchData()
}
defineExpose({ open })

async function fetchData() {
  loading.value = true
  try {
    const res = await getStreamPushClientPageApi({ page: 1, pageSize: 1000 })
    tableData.value = res.data.rows
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  formTitle.value = '新增推流方'
  form.value = { clientId: '', enabled: 1 }
  formVisible.value = true
}

function handleEdit(row: StreamPushClient) {
  isEdit.value = true
  formTitle.value = '编辑推流方'
  form.value = { ...row }
  formVisible.value = true
}

async function handleConfirm() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (isEdit.value) {
    await updateStreamPushClientApi(form.value)
    ElMessage.success('修改成功')
  } else {
    const res = await saveStreamPushClientApi(form.value)
    ElMessage.success('新增成功')
    showToken(res.data.token, res.data.clientId)
  }
  formVisible.value = false
  fetchData()
}

async function handleResetToken(row: StreamPushClient) {
  try {
    await ElMessageBox.confirm(
      `确定重置 "${row.clientId}" 的 token 吗？老 token 立即失效，正在推流的连接不会断开（需在推流列表手动中止）。`,
      '提示',
      { type: 'warning' }
    )
    const res = await resetStreamPushClientTokenApi(row.id)
    ElMessage.success('已重置')
    showToken(res.data.token, row.clientId)
    fetchData()
  } catch {
    // cancelled
  }
}

async function handleDelete(row: StreamPushClient) {
  try {
    await ElMessageBox.confirm(`确定要删除推流方 "${row.clientId}" 吗？删除后该 token 立即失效。`, '提示', {
      type: 'warning',
    })
    await deleteStreamPushClientApi(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    // cancelled
  }
}

function showToken(token: string, clientId: string) {
  ElMessageBox.alert(
    `<div style="font-size:12px;color:#909399;margin-bottom:6px">推流方 ${clientId} 的 token：</div>` +
      `<div style="padding:8px;background:#f5f7fa;border-radius:4px;word-break:break-all;font-family:monospace">${token}</div>` +
      `<div style="margin-top:8px;font-size:12px;color:#909399">推流 URL 示例：<br/><code style="color:#409eff">rtmp://&lt;zlm&gt;:1935/&lt;app&gt;/&lt;stream&gt;?token=${token}</code></div>`,
    'Token',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  ).catch(() => {})
}

async function copyToken(token: string) {
  try {
    await navigator.clipboard.writeText(token)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="推流方授权" width="1100px" top="6vh" :close-on-click-modal="false">
    <div class="toolbar">
      <el-button v-permission="'media:stream-push-client:add'" type="primary" :icon="Plus" @click="handleAdd">
        新增
      </el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe max-height="60vh">
      <el-table-column prop="clientId" label="推流方" width="200" show-overflow-tooltip />
      <el-table-column label="Token" min-width="420" show-overflow-tooltip>
        <template #default="{ row }">
          <span style="font-family: monospace; font-size: 12px">{{ row.token }}</span>
          <el-button link type="primary" :icon="CopyDocument" @click="copyToken(row.token)" />
        </template>
      </el-table-column>
      <el-table-column label="启用" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-tooltip content="编辑">
            <el-button
              v-permission="'media:stream-push-client:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            />
          </el-tooltip>
          <el-tooltip content="重置 Token">
            <el-button
              v-permission="'media:stream-push-client:edit'"
              type="warning"
              link
              :icon="RefreshRight"
              @click="handleResetToken(row)"
            />
          </el-tooltip>
          <el-tooltip content="删除">
            <el-button
              v-permission="'media:stream-push-client:delete'"
              type="danger"
              link
              :icon="Delete"
              @click="handleDelete(row)"
            />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="formTitle" width="480px" :close-on-click-modal="false" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="推流方" prop="clientId">
          <el-input v-model="form.clientId" placeholder="唯一标识" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取 消</el-button>
        <el-button type="primary" @click="handleConfirm">确 定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
