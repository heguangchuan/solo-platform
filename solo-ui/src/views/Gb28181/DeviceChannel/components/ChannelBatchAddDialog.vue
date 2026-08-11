<script setup lang="ts">
/**
 * 通道批量新增弹窗（独立通道管理页专用）
 * <p>左侧列出全部设备供选择，右侧填写多条通道并一次性提交。</p>
 */
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, VideoCamera } from '@element-plus/icons-vue'
import type { GbDevice, GbDeviceChannel } from '@/types/api'
import { getGbDevicePageApi, saveGbDeviceChannelApi, getNextGbChannelCodeApi } from '@/api/gb28181'
import { CHANNEL_TYPE_OPTIONS, DEFAULT_CHANNEL_TYPE, buildGbCode, nextSeqFromCodes } from '@/utils/gbCode'

interface Emits {
  (e: 'refresh'): void
}

const emit = defineEmits<Emits>()

const visible = ref(false)
const submitting = ref(false)
const deviceLoading = ref(false)
const devices = ref<GbDevice[]>([])
const keyword = ref('')
const selectedDevice = ref<GbDevice | null>(null)
const channels = ref<Partial<GbDeviceChannel>[]>([])
const formRef = ref()

/** 自动生成编码用的类型码（11-13位），默认 131 摄像机 */
const typeCode = ref(DEFAULT_CHANNEL_TYPE)
/** 选中设备后从后端取到的下一个可用编码（作为本次本地递增的基准，已计入已入库通道） */
const baseCode = ref('')

const rules = {
  'channels.*.channelId': [
    { required: true, message: '请输入通道编码', trigger: 'blur' },
    { pattern: /^\d{20}$/, message: '通道国标ID为20位数字', trigger: 'blur' },
  ],
  'channels.*.name': [
    { required: true, message: '请输入通道名称', trigger: 'blur' },
  ],
}

const filteredDevices = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return devices.value
  return devices.value.filter(d =>
    (d.deviceId || '').toLowerCase().includes(kw) ||
    (d.name || '').toLowerCase().includes(kw) ||
    (d.reportName || '').toLowerCase().includes(kw)
  )
})

async function loadDevices() {
  deviceLoading.value = true
  try {
    // 一次性拉全部设备（管理侧场景通常几十台内）
    const res = await getGbDevicePageApi({ page: 1, pageSize: 1000 })
    devices.value = res.data.rows
  } finally {
    deviceLoading.value = false
  }
}

function open() {
  visible.value = true
  keyword.value = ''
  selectedDevice.value = null
  channels.value = []
  typeCode.value = DEFAULT_CHANNEL_TYPE
  baseCode.value = ''
  loadDevices()
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSelectDevice(device: GbDevice) {
  selectedDevice.value = device
  channels.value = []
  await refreshBaseCode()
  addEmptyChannel()
}

/** 切换类型码 / 选中设备后，从后端取该设备下下一个可用编码作为本次批量本地递增的基准 */
async function refreshBaseCode() {
  baseCode.value = ''
  const device = selectedDevice.value
  if (!device?.deviceId) return
  try {
    const res = await getNextGbChannelCodeApi(device.deviceId, typeCode.value)
    baseCode.value = res.data || ''
  } catch {
    baseCode.value = ''
  }
}

/** 类型码变更：重算基准并把已生成的空/自动编码行重新填一遍 */
async function handleTypeChange() {
  await refreshBaseCode()
  regenerateAll()
}

/** 依据基准编码 + 当前所有行已填编码，算出下一个不冲突的编码 */
function computeNextCode(): string {
  if (!baseCode.value) return ''
  const prefix13 = baseCode.value.slice(0, 13)
  const domain10 = prefix13.slice(0, 10)
  const type = prefix13.slice(10)
  // baseCode 是后端给的「下一个可用」= 已入库最大 +1；减 1 得已占用最大值作为比较基线
  const baseSeq = Number.parseInt(baseCode.value.slice(13), 10)
  const occupiedMax = Number.isNaN(baseSeq) ? '' : prefix13 + String(baseSeq - 1).padStart(7, '0')
  // 与当前所有行已填编码一起取最大 +1，保证不与未保存行冲突
  const pool = [occupiedMax, ...channels.value.map(c => c.channelId || '')]
  const seq = nextSeqFromCodes(pool, prefix13)
  return buildGbCode(domain10, type, seq)
}

/** 重填所有「非手改」的自动编码（简单起见：仅覆盖空编码行） */
function regenerateAll() {
  for (const ch of channels.value) {
    if (!ch.channelId) ch.channelId = computeNextCode()
  }
}

function addEmptyChannel() {
  channels.value.push({ channelId: computeNextCode(), name: '' })
}

function removeChannel(index: number) {
  channels.value.splice(index, 1)
}

async function handleConfirm() {
  if (!selectedDevice.value) {
    ElMessage.warning('请先在左侧选择所属设备')
    return
  }
  if (channels.value.length === 0) {
    ElMessage.warning('请至少添加一条通道')
    return
  }
  await nextTick()
  await formRef.value?.validate()

  submitting.value = true
  try {
    const device = selectedDevice.value
    for (const ch of channels.value) {
      await saveGbDeviceChannelApi({
        ...ch,
        deviceId: device.deviceId,
      })
    }
    ElMessage.success(`成功新增 ${channels.value.length} 条通道`)
    visible.value = false
    emit('refresh')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="新增通道"
    width="1080px"
    top="8vh"
    :close-on-click-modal="false"
  >
    <div class="split">
      <div class="left">
        <div class="left-header">
          <el-input
            v-model="keyword"
            placeholder="搜索设备编码/名称"
            clearable
            :prefix-icon="Search"
            size="small"
          />
        </div>
        <div class="device-list" v-loading="deviceLoading">
          <div
            v-for="d in filteredDevices"
            :key="d.id"
            class="device-item"
            :class="{ active: selectedDevice?.id === d.id }"
            @click="handleSelectDevice(d)"
          >
            <el-icon class="dev-icon" :class="{ online: d.status === 1 }">
              <VideoCamera />
            </el-icon>
            <div class="dev-meta">
              <div class="dev-name" :title="d.name || d.reportName || '—'">
                {{ d.name || d.reportName || '—' }}
              </div>
              <div class="dev-id" :title="d.deviceId">{{ d.deviceId }}</div>
            </div>
          </div>
          <el-empty
            v-if="!deviceLoading && filteredDevices.length === 0"
            description="暂无设备"
            :image-size="60"
          />
        </div>
      </div>

      <div class="right">
        <div v-if="!selectedDevice" class="empty-tip">
          <el-empty description="请先在左侧选择所属设备" />
        </div>
        <template v-else>
          <el-descriptions :column="2" size="small" border class="dev-desc">
            <el-descriptions-item label="所属设备">
              {{ selectedDevice.name || selectedDevice.reportName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="设备编码">
              {{ selectedDevice.deviceId }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="type-bar">
            <span class="type-label">通道类型码</span>
            <el-select v-model="typeCode" size="small" class="type-select" @change="handleTypeChange">
              <el-option
                v-for="opt in CHANNEL_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>

          <el-form ref="formRef" :model="{ channels }" :rules="rules">
            <el-table :data="channels" border style="width: 100%; margin-top: 12px" :show-header="true">
              <el-table-column label="通道编码" width="260">
                <template #default="{ row, $index }">
                  <el-form-item :prop="`channels.${$index}.channelId`" class="no-margin">
                    <el-input v-model="row.channelId" placeholder="20位数字" />
                  </el-form-item>
                </template>
              </el-table-column>
              <el-table-column label="通道名称" min-width="120">
                <template #default="{ row, $index }">
                  <el-form-item :prop="`channels.${$index}.name`" class="no-margin">
                    <el-input v-model="row.name" placeholder="通道名称" />
                  </el-form-item>
                </template>
              </el-table-column>
              <el-table-column width="90" align="center">
                <template #header>
                  <el-button type="primary" plain :icon="Plus" size="small" @click="addEmptyChannel">
                    添加
                  </el-button>
                </template>
                <template #default="{ $index }">
                  <el-button type="danger" link @click="removeChannel($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-form>
        </template>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleConfirm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.split {
  display: flex;
  gap: 12px;
  height: 60vh;
}

.left {
  width: 280px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.left-header {
  padding: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.device-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.device-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.device-item:hover {
  background: var(--el-fill-color-light);
}

.device-item.active {
  background: rgba(64, 158, 255, 0.12);
  color: var(--el-color-primary);
}

.dev-icon {
  color: #909399;
  margin-right: 8px;
  font-size: 16px;
}

.dev-icon.online {
  color: #67c23a;
}

.dev-meta {
  flex: 1;
  min-width: 0;
}

.dev-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dev-id {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right {
  flex: 1;
  overflow-y: auto;
}

.empty-tip {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dev-desc {
  margin-bottom: 4px;
}

.type-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.type-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  flex-shrink: 0;
}

.type-select {
  flex: 1;
}

.no-margin {
  margin-bottom: 0;
}

:deep(.el-table td .el-form-item__content) {
  margin-left: 0 !important;
}
</style>
