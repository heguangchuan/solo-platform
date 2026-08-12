<script setup lang="ts">
/**
 * 云台预置位管理弹窗
 * <p>
 * 布局：左侧 - 通道信息 + 云台控制 + 预置位CRUD；右侧 - 播放器实时预览。
 * 操作流程：按住方向转动云台 → 看右侧画面确认位置 → 设备保存 → 新增预置位记录 → 重复添加多个。
 * </p>
 */
import { ref, nextTick } from 'vue'
import type { GbPtzPreset } from '@/types/api'
import {
  getPtzPresetListApi,
  savePtzPresetApi,
  updatePtzPresetApi,
  deletePtzPresetApi,
  callPtzPresetApi,
} from '@/api/gb28181'
import { ptzControlApi } from '@/api/gb28181'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Aim, Edit, Delete } from '@element-plus/icons-vue'

const deviceId = ref('')
const channelDbId = ref('')
const channelId = ref('')
const channelName = ref('')

const visible = ref(false)
const loading = ref(false)
const tableData = ref<GbPtzPreset[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref()
const form = ref<Partial<GbPtzPreset>>({})

let pressingTimer: number | null = null

const rules = {
  name: [
    { required: true, message: '请输入预置位名称', trigger: 'blur' },
  ],
}

// 当前播放状态
const streamInfo = ref<{
  streamId: string
  wsFlvUrl?: string
  flvUrl?: string
} | null>(null)
const playerRef = ref<InstanceType<typeof JessibucaPlayer> | null>(null)
const currentStream = ref('')
const transport = ref<'UDP' | 'TCP-passive' | 'TCP-active'>('TCP-passive')

function open(params: { deviceId: string; channelDbId: string; channelId: string; channelName: string }) {
  deviceId.value = params.deviceId
  channelDbId.value = params.channelDbId
  channelId.value = params.channelId
  channelName.value = params.channelName
  visible.value = true
  fetchData()
  // 播放
  startPlay()
}

async function startPlay() {
  loading.value = true
  try {
    const { startPlayApi } = await import('@/api/gb28181')
    const res = await startPlayApi({
      deviceId: deviceId.value,
      channelId: channelId.value,
      transport: transport.value,
      streamNumber: currentStream.value,
    })
    streamInfo.value = res.data
    await nextTick()
    const url = pickPlayUrl(res.data)
    if (url) {
      playerRef.value?.play(url)
    }
  } catch (_) {
    // 错误不拦截，用户看得到就行
  } finally {
    loading.value = false
  }
}

function pickPlayUrl(info: { wsFlvUrl?: string; flvUrl?: string }): string | undefined {
  if (window.location.protocol === 'https:' && info.wsFlvUrl) {
    return info.wsFlvUrl.replace(/^ws:\/\//, 'wss://')
  }
  return info.wsFlvUrl || info.flvUrl
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPtzPresetListApi(channelDbId.value)
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增预置位'
  form.value = {
    channelDbId: channelDbId.value,
    name: '',
  }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function handleEdit(row: GbPtzPreset) {
  isEdit.value = true
  dialogTitle.value = '编辑预置位'
  form.value = { ...row }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleConfirm() {
  await formRef.value?.validate()
  if (isEdit.value) {
    await updatePtzPresetApi(form.value as GbPtzPreset)
    ElMessage.success('修改成功')
  } else {
    await savePtzPresetApi(form.value as GbPtzPreset)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  await fetchData()
}

async function handleDelete(row: GbPtzPreset) {
  try {
    await ElMessageBox.confirm(`确定要删除预置位「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deletePtzPresetApi(String(row.id!))
    ElMessage.success('删除成功')
    await fetchData()
  } catch (_) {
    // 用户取消
  }
}

async function handleCall(row: GbPtzPreset) {
  try {
    await callPtzPresetApi(String(row.id!))
    ElMessage.success(`已调用预置位「${row.name}」`)
  } catch (_) {
    // 失败不打断
  }
}

// ========== 云台方向控制 ==========
const speed = 100
const zoomSpeed = 5

function startPtz(command: string) {
  if (deviceId.value && channelId.value) {
    ptzControlApi(deviceId.value, channelId.value, command, speed, zoomSpeed)
  }
  pressingTimer = window.setInterval(() => {
    if (deviceId.value && channelId.value) {
      ptzControlApi(deviceId.value, channelId.value, command, speed, zoomSpeed)
    }
  }, 200)
}

function stopPtz() {
  if (pressingTimer) {
    clearInterval(pressingTimer)
    pressingTimer = null
  }
  if (deviceId.value && channelId.value) {
    ptzControlApi(deviceId.value, channelId.value, 'stop', 0, 0)
  }
}

async function handleStopBackend() {
  if (streamInfo.value?.streamId) {
    const { stopPlayApi } = await import('@/api/gb28181')
    try {
      await stopPlayApi({ streamId: streamInfo.value.streamId })
    } catch (_) {
      // ignore
    }
  }
}

async function onDialogClose() {
  playerRef.value?.destroy()
  await handleStopBackend()
  visible.value = false
  streamInfo.value = null
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'预置位管理 - ' + (channelName ?? '')"
    width="1100px"
    top="5vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div class="container">
      <!-- 左侧：通道信息 + 云台控制 + 预置位CRUD -->
      <div class="left-panel">
        <div class="channel-info">
          <div><span class="label">通道：</span>{{ channelName }} ({{ channelId }})</div>
        </div>

        <div class="ptz-panel">
          <div class="panel-title">云台控制</div>
          <div class="ptz-grid">
            <div class="grid-cell" @mousedown="startPtz('upleft')" @mouseup="stopPtz" @mouseleave="stopPtz">↖</div>
            <div class="grid-cell" @mousedown="startPtz('up')" @mouseup="stopPtz" @mouseleave="stopPtz">↑</div>
            <div class="grid-cell" @mousedown="startPtz('upright')" @mouseup="stopPtz" @mouseleave="stopPtz">↗</div>
            <div class="grid-cell" @mousedown="startPtz('left')" @mouseup="stopPtz" @mouseleave="stopPtz">←</div>
            <div class="grid-cell zoom-cell">
              <div class="zoom-btn" @mousedown="startPtz('zoomin')" @mouseup="stopPtz" @mouseleave="stopPtz">+</div>
              <div class="zoom-label">变焦</div>
              <div class="zoom-btn" @mousedown="startPtz('zoomout')" @mouseup="stopPtz" @mouseleave="stopPtz">−</div>
            </div>
            <div class="grid-cell" @mousedown="startPtz('right')" @mouseup="stopPtz" @mouseleave="stopPtz">→</div>
            <div class="grid-cell" @mousedown="startPtz('downleft')" @mouseup="stopPtz" @mouseleave="stopPtz">↙</div>
            <div class="grid-cell" @mousedown="startPtz('down')" @mouseup="stopPtz" @mouseleave="stopPtz">↓</div>
            <div class="grid-cell" @mousedown="startPtz('downright')" @mouseup="stopPtz" @mouseleave="stopPtz">↘</div>
          </div>
          <div class="tip-text">按住方向键持续移动，松开自动停止</div>
        </div>

        <div class="preset-panel">
          <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
            <el-table-column prop="presetId" label="编号" width="70" align="center" />
            <el-table-column prop="name" label="名称" />
            <el-table-column width="180" fixed="right" align="center">
              <template #header>
                <el-button
                  v-permission="'gb28181:ptz:preset:add'"
                  type="primary"
                  size="small"
                  text
                  :icon="Plus"
                  @click="handleAdd"
                />
              </template>
              <template #default="{ row }">
                <el-tooltip content="调用">
                  <el-button type="primary" link :icon="Aim" @click="handleCall(row)" />
                </el-tooltip>
                <el-tooltip content="编辑">
                  <el-button v-permission="'gb28181:ptz:preset:edit'" type="primary" link :icon="Edit" @click="handleEdit(row)" />
                </el-tooltip>
                <el-tooltip content="删除">
                  <el-button v-permission="'gb28181:ptz:preset:delete'" type="danger" link :icon="Delete" @click="handleDelete(row)" />
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 右侧：播放器实时预览 -->
      <div class="right-player">
      <JessibucaPlayer
        ref="playerRef"
        height="100%"
        v-if="visible"
        :streams="[]"
        current-stream=""
        transport="TCP-passive"
      />
    </div>
  </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      top="15vh"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="预置位名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：大门入口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="onDialogClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.container {
  display: flex;
  gap: 16px;
  height: 60vh;
  min-height: 560px;
}

/* 左侧 */
.left-panel {
  flex: 0 0 420px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.channel-info {
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.channel-info .label {
  font-weight: 500;
  color: #606266;
}

/* 云台控制面板 - 紧凑网格布局 */
.ptz-panel {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 10px 12px;
}
.panel-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 10px;
}
.ptz-grid {
  display: grid;
  grid-template-columns: 40px 40px 40px;
  grid-template-rows: 40px 40px 40px;
  gap: 4px;
  justify-content: center;
}
.ptz-grid .grid-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.ptz-grid .grid-cell:hover {
  background: #e4e7ed;
  color: #409eff;
}
.ptz-grid .grid-cell:active {
  background: #409eff;
  color: #fff;
}
.zoom-cell {
  grid-column: 2 / 3;
  flex-direction: column;
}
.zoom-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 18px;
  background: #f5f7fa;
  border-radius: 3px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.15s;
}
.zoom-btn:hover {
  background: #e4e7ed;
  color: #409eff;
}
.zoom-btn:active {
  background: #409eff;
  color: #fff;
}
.zoom-label {
  font-size: 10px;
  color: #909399;
}
.tip-text {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  text-align: center;
}

/* 预置位列表 - 占据剩余高度 */
.preset-panel {
  flex: 1;
  min-height: 0;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px;
  display: flex;
  flex-direction: column;
}

/* 右侧播放器 */
.right-player {
  flex: 1;
  min-width: 0;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
}
</style>
