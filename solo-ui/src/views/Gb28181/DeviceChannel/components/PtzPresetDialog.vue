<script setup lang="ts">
/**
 * 云台设置弹窗（预置位 + 巡航路线）
 * <p>
 * 布局：左侧 - 通道信息 + Tab(预置位/巡航)；右侧 - 播放器实时预览。
 * </p>
 */
import { ref, nextTick, computed } from 'vue'
import type { GbPtzPreset, GbPtzCruise, GbPtzCruisePoint } from '@/types/api'
import {
  getPtzPresetListApi,
  savePtzPresetApi,
  updatePtzPresetApi,
  deletePtzPresetApi,
  callPtzPresetApi,
  ptzControlApi,
  ptzFocusApi,
  ptzIrisApi,
  ptzWiperApi,
  getCruiseListApi,
  getCruiseDetailApi,
  saveCruiseApi,
  updateCruiseApi,
  deleteCruiseApi,
  cruiseAddPointApi,
  cruiseRemovePointApi,
  startCruiseApi,
  stopCruiseApi,
} from '@/api/gb28181'
import { useUserStore } from '@/stores/user'
import JessibucaPlayer from '@/components/JessibucaPlayer.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Aim, Edit, Delete, VideoPlay, VideoPause } from '@element-plus/icons-vue'

const userStore = useUserStore()
function hasPerm(code: string): boolean {
  return userStore.permissions.includes(code)
}

const deviceId = ref('')
const channelDbId = ref('')
const channelId = ref('')
const channelName = ref('')

const visible = ref(false)
const loading = ref(false)
const activeTab = ref<'preset' | 'cruise'>('preset')

// ===== 预置位 =====
const presetList = ref<GbPtzPreset[]>([])
const presetDialogVisible = ref(false)
const presetDialogTitle = ref('')
const presetIsEdit = ref(false)
const presetFormRef = ref()
const presetForm = ref<Partial<GbPtzPreset>>({})

const presetRules = {
  name: [
    { required: true, message: '请输入预置位名称', trigger: 'blur' },
  ],
}

// ===== 巡航路线 =====
const cruiseList = ref<GbPtzCruise[]>([])
const activeCruiseId = ref<string | null>(null)
const cruisePoints = ref<GbPtzCruisePoint[]>([])
const cruisePointsLoading = ref(false)
const cruiseDialogVisible = ref(false)
const cruiseDialogTitle = ref('')
const cruiseIsEdit = ref(false)
const cruiseFormRef = ref()
const cruiseForm = ref<Partial<GbPtzCruise>>({})

// 新增巡航点表单
const addPointVisible = ref(false)
const addPointForm = ref({ presetId: 0, staySeconds: null as number | null })
const addPointPresetOptions = computed(() =>
  presetList.value.map(p => ({ label: `${p.presetId} - ${p.name}`, value: p.presetId }))
)

const cruiseRules = {
  name: [
    { required: true, message: '请输入巡航路线名称', trigger: 'blur' },
  ],
  speed: [
    { type: 'number', min: 1, max: 255, message: '巡航速度范围 1-255', trigger: 'blur' },
  ],
  staySeconds: [
    { type: 'number', min: 1, message: '停留时间至少 1 秒', trigger: 'blur' },
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

// PTZ 速度
const ptzSpeeds = ref({
  move: 100,
  zoom: 5,
  focus: 5,
  iris: 5,
})

let pressingTimer: number | null = null

function open(params: { deviceId: string; channelDbId: string; channelId: string; channelName: string }) {
  deviceId.value = params.deviceId
  channelDbId.value = params.channelDbId
  channelId.value = params.channelId
  channelName.value = params.channelName
  visible.value = true
  fetchPresetList()
  fetchCruiseList()
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

// ========== 预置位 ==========
async function fetchPresetList() {
  try {
    const res = await getPtzPresetListApi(channelDbId.value)
    presetList.value = res.data || []
  } catch {
    presetList.value = []
  }
}

function handlePresetAdd() {
  presetIsEdit.value = false
  presetDialogTitle.value = '新增预置位'
  presetForm.value = {
    channelDbId: channelDbId.value,
    name: '',
  }
  presetDialogVisible.value = true
  nextTick(() => presetFormRef.value?.clearValidate())
}

function handlePresetEdit(row: GbPtzPreset) {
  presetIsEdit.value = true
  presetDialogTitle.value = '编辑预置位'
  presetForm.value = { ...row }
  presetDialogVisible.value = true
  nextTick(() => presetFormRef.value?.clearValidate())
}

async function handlePresetConfirm() {
  await presetFormRef.value?.validate()
  if (presetIsEdit.value) {
    await updatePtzPresetApi(presetForm.value as GbPtzPreset)
    ElMessage.success('修改成功')
  } else {
    await savePtzPresetApi(presetForm.value as GbPtzPreset)
    ElMessage.success('新增成功')
  }
  presetDialogVisible.value = false
  await fetchPresetList()
}

async function handlePresetDelete(row: GbPtzPreset) {
  try {
    await ElMessageBox.confirm(`确定要删除预置位「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deletePtzPresetApi(String(row.id!))
    ElMessage.success('删除成功')
    await fetchPresetList()
  } catch (_) {
    // 用户取消
  }
}

async function handlePresetCall(row: GbPtzPreset) {
  try {
    await callPtzPresetApi(String(row.id!))
    ElMessage.success(`已调用预置位「${row.name}」`)
  } catch (_) {
    // 失败不打断
  }
}

// ========== 巡航路线 ==========
async function fetchCruiseList() {
  try {
    const res = await getCruiseListApi(channelDbId.value)
    cruiseList.value = res.data || []
    // 若当前选中的路线还在，保留选择并刷新巡航点
    if (activeCruiseId.value && cruiseList.value.some(c => c.id === activeCruiseId.value)) {
      fetchCruisePoints(activeCruiseId.value)
    } else {
      activeCruiseId.value = null
      cruisePoints.value = []
    }
  } catch {
    cruiseList.value = []
    activeCruiseId.value = null
    cruisePoints.value = []
  }
}

async function fetchCruisePoints(id: string) {
  cruisePointsLoading.value = true
  try {
    const res = await getCruiseDetailApi(id)
    cruisePoints.value = res.data?.points || []
  } catch {
    cruisePoints.value = []
  } finally {
    cruisePointsLoading.value = false
  }
}

function handleSelectCruise(cruise: GbPtzCruise) {
  activeCruiseId.value = cruise.id!
  fetchCruisePoints(cruise.id!)
}

function handleCruiseAdd() {
  cruiseIsEdit.value = false
  cruiseDialogTitle.value = '新增巡航路线'
  cruiseForm.value = {
    channelDbId: channelDbId.value,
    name: '',
    speed: 50,
    staySeconds: 10,
  }
  cruiseDialogVisible.value = true
  nextTick(() => cruiseFormRef.value?.clearValidate())
}

function handleCruiseEdit(row: GbPtzCruise) {
  cruiseIsEdit.value = true
  cruiseDialogTitle.value = '编辑巡航路线'
  cruiseForm.value = { ...row }
  cruiseDialogVisible.value = true
  nextTick(() => cruiseFormRef.value?.clearValidate())
}

async function handleCruiseConfirm() {
  await cruiseFormRef.value?.validate()
  if (cruiseIsEdit.value) {
    await updateCruiseApi(cruiseForm.value as GbPtzCruise)
    ElMessage.success('修改成功')
  } else {
    await saveCruiseApi({
      channelDbId: cruiseForm.value.channelDbId!,
      name: cruiseForm.value.name!,
      speed: cruiseForm.value.speed,
      staySeconds: cruiseForm.value.staySeconds,
      points: [],
    })
    ElMessage.success('新增成功')
  }
  cruiseDialogVisible.value = false
  await fetchCruiseList()
}

async function handleCruiseDelete(row: GbPtzCruise) {
  try {
    await ElMessageBox.confirm(`确定要删除巡航路线「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteCruiseApi(String(row.id!))
    ElMessage.success('删除成功')
    if (activeCruiseId.value === row.id) {
      activeCruiseId.value = null
      cruisePoints.value = []
    }
    await fetchCruiseList()
  } catch (_) {
    // 用户取消
  }
}

async function handleCruiseStart(row: GbPtzCruise) {
  try {
    await startCruiseApi(String(row.id!))
    ElMessage.success(`已开始巡航「${row.name}」`)
  } catch (e) {
    ElMessage.error('开始巡航失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

async function handleCruiseStop(row: GbPtzCruise) {
  try {
    await stopCruiseApi(String(row.id!))
    ElMessage.success('已停止巡航')
  } catch (e) {
    ElMessage.error('停止巡航失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

// ===== 巡航点 =====
function handleOpenAddPoint() {
  if (!activeCruiseId.value) return
  addPointForm.value = { presetId: 0, staySeconds: null }
  addPointVisible.value = true
}

async function handleAddPointConfirm() {
  if (!addPointForm.value.presetId) {
    ElMessage.warning('请选择预置位')
    return
  }
  try {
    await cruiseAddPointApi(activeCruiseId.value!, {
      presetId: addPointForm.value.presetId,
      staySeconds: addPointForm.value.staySeconds ?? undefined,
    })
    ElMessage.success('添加成功')
    addPointVisible.value = false
    fetchCruisePoints(activeCruiseId.value!)
  } catch (e) {
    ElMessage.error('添加失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

async function handleRemovePoint(point: GbPtzCruisePoint) {
  try {
    await ElMessageBox.confirm(`确定要删除巡航点「${point.presetName || point.presetId}」吗？`, '删除确认', { type: 'warning' })
    await cruiseRemovePointApi(activeCruiseId.value!, point.presetId)
    ElMessage.success('删除成功')
    fetchCruisePoints(activeCruiseId.value!)
  } catch (_) {
    // 用户取消
  }
}

// ========== 云台方向控制（使用 JessibucaPlayer 面板，这里保留备用，暂不显示） ==========
function startPtz(command: string) {
  if (deviceId.value && channelId.value) {
    ptzControlApi(deviceId.value, channelId.value, command, ptzSpeeds.value.move, ptzSpeeds.value.zoom)
  }
  pressingTimer = window.setInterval(() => {
    if (deviceId.value && channelId.value) {
      ptzControlApi(deviceId.value, channelId.value, command, ptzSpeeds.value.move, ptzSpeeds.value.zoom)
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

function handlePtz(direction: string, pressing: boolean) {
  const command = direction.replace(/-/g, '')
  if (pressing) {
    startPtz(command)
  } else {
    stopPtz()
  }
}

function handleFocus(direction: 'near' | 'far' | 'stop', speed: number) {
  if (!deviceId.value || !channelId.value) return
  ptzFocusApi(deviceId.value, channelId.value, direction, speed)
}

function handleIris(direction: 'open' | 'close' | 'stop', speed: number) {
  if (!deviceId.value || !channelId.value) return
  ptzIrisApi(deviceId.value, channelId.value, direction, speed)
}

function handleWiper(action: 'on' | 'off') {
  if (!deviceId.value || !channelId.value) return
  ptzWiperApi(deviceId.value, channelId.value, action)
}

function handleSpeedChange(speeds: { move: number; zoom: number; focus: number; iris: number }) {
  ptzSpeeds.value = speeds
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
  activeCruiseId.value = null
  cruisePoints.value = []
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="'云台设置 - ' + (channelName ?? '')"
    width="1180px"
    top="5vh"
    :close-on-click-modal="false"
    :before-close="onDialogClose"
  >
    <div class="container">
      <!-- 左侧：Tab 切换 + 内容 -->
      <div class="left-panel">
        <div class="channel-info">
          <div><span class="label">通道：</span>{{ channelName }} ({{ channelId }})</div>
        </div>

        <el-tabs v-model="activeTab" class="ptz-tabs">
          <!-- 预置位 Tab -->
          <el-tab-pane label="预置位" name="preset">
            <div class="tab-content">
              <el-table :data="presetList" v-loading="loading" border stripe size="small" style="width: 100%" height="100%">
                <el-table-column prop="presetId" label="编号" width="70" align="center" />
                <el-table-column prop="name" label="名称" />
                <el-table-column width="160" fixed="right" align="center">
                  <template #header>
                    <el-button
                      v-permission="'gb28181:ptz:preset:add'"
                      type="primary"
                      size="small"
                      text
                      :icon="Plus"
                      @click="handlePresetAdd"
                    />
                  </template>
                  <template #default="{ row }">
                    <el-tooltip content="调用">
                      <el-button type="primary" link :icon="Aim" @click="handlePresetCall(row)" />
                    </el-tooltip>
                    <el-tooltip content="编辑">
                      <el-button v-permission="'gb28181:ptz:preset:edit'" type="primary" link :icon="Edit" @click="handlePresetEdit(row)" />
                    </el-tooltip>
                    <el-tooltip content="删除">
                      <el-button v-permission="'gb28181:ptz:preset:delete'" type="danger" link :icon="Delete" @click="handlePresetDelete(row)" />
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 巡航路线 Tab -->
          <el-tab-pane label="巡航路线" name="cruise">
            <div class="tab-content cruise-layout">
              <!-- 上：路线列表 -->
              <div class="cruise-list-section">
                <div class="section-header">
                  <span class="section-title">路线列表</span>
                  <el-button
                    v-permission="'gb28181:ptz:cruise:add'"
                    type="primary"
                    size="small"
                    text
                    :icon="Plus"
                    @click="handleCruiseAdd"
                  >新增</el-button>
                </div>
                <el-table :data="cruiseList" border stripe size="small" height="100%" @row-click="handleSelectCruise">
                  <el-table-column prop="cruiseId" label="编号" width="60" align="center" />
                  <el-table-column prop="name" label="名称" min-width="100" show-overflow-tooltip />
                  <el-table-column prop="speed" label="速度" width="60" align="center" />
                  <el-table-column width="110" fixed="right" align="center">
                    <template #default="{ row }">
                      <el-tooltip content="开始">
                        <el-button type="success" link :icon="VideoPlay" @click.stop="handleCruiseStart(row)" />
                      </el-tooltip>
                      <el-tooltip content="停止">
                        <el-button type="warning" link :icon="VideoPause" @click.stop="handleCruiseStop(row)" />
                      </el-tooltip>
                      <el-tooltip v-if="hasPerm('gb28181:ptz:cruise:edit')" content="编辑">
                        <el-button type="primary" link :icon="Edit" @click.stop="handleCruiseEdit(row)" />
                      </el-tooltip>
                      <el-tooltip v-if="hasPerm('gb28181:ptz:cruise:delete')" content="删除">
                        <el-button type="danger" link :icon="Delete" @click.stop="handleCruiseDelete(row)" />
                      </el-tooltip>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <!-- 下：巡航点列表 -->
              <div class="cruise-points-section">
                <div class="section-header">
                  <span class="section-title">
                    巡航点
                    <span v-if="activeCruiseId" class="sub-text">
                      ({{ cruiseList.find(c => c.id === activeCruiseId)?.name || '' }})
                    </span>
                  </span>
                  <el-button
                    v-if="activeCruiseId && hasPerm('gb28181:ptz:cruise:edit')"
                    type="primary"
                    size="small"
                    text
                    :icon="Plus"
                    @click="handleOpenAddPoint"
                  >添加巡航点</el-button>
                </div>
                <div v-if="!activeCruiseId" class="empty-hint">请先选择一条巡航路线</div>
                <el-table
                  v-else
                  :data="cruisePoints"
                  v-loading="cruisePointsLoading"
                  border
                  stripe
                  size="small"
                  height="100%"
                >
                  <el-table-column prop="sortOrder" label="序号" width="60" align="center" />
                  <el-table-column prop="presetId" label="预置位" width="80" align="center" />
                  <el-table-column prop="presetName" label="名称" min-width="80" show-overflow-tooltip />
                  <el-table-column prop="staySeconds" label="停留(秒)" width="90" align="center">
                    <template #default="{ row }">
                      {{ row.staySeconds ?? '默认' }}
                    </template>
                  </el-table-column>
                  <el-table-column width="70" fixed="right" align="center">
                    <template #default="{ row }">
                      <el-tooltip content="删除">
                        <el-button type="danger" link :icon="Delete" @click="handleRemovePoint(row)" />
                      </el-tooltip>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 右侧：播放器实时预览 -->
      <div class="right-player">
        <JessibucaPlayer
          v-if="visible"
          ref="playerRef"
          height="100%"
          :streams="[]"
          current-stream=""
          transport="TCP-passive"
          @ptz="handlePtz"
          @focus="handleFocus"
          @iris="handleIris"
          @wiper="handleWiper"
          @speed-change="handleSpeedChange"
        />
      </div>
    </div>

    <!-- 预置位新增/编辑对话框 -->
    <el-dialog
      v-model="presetDialogVisible"
      :title="presetDialogTitle"
      width="500px"
      top="15vh"
      :close-on-click-modal="false"
    >
      <el-form ref="presetFormRef" :model="presetForm" :rules="presetRules" label-width="100px">
        <el-form-item label="预置位名称" prop="name">
          <el-input v-model="presetForm.name" placeholder="例如：大门入口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="presetDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handlePresetConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 巡航路线新增/编辑对话框 -->
    <el-dialog
      v-model="cruiseDialogVisible"
      :title="cruiseDialogTitle"
      width="500px"
      top="15vh"
      :close-on-click-modal="false"
    >
      <el-form ref="cruiseFormRef" :model="cruiseForm" :rules="cruiseRules" label-width="100px">
        <el-form-item label="路线名称" prop="name">
          <el-input v-model="cruiseForm.name" placeholder="例如：周边巡逻" />
        </el-form-item>
        <el-form-item label="巡航速度" prop="speed">
          <el-input-number v-model="cruiseForm.speed" :min="1" :max="255" />
          <span class="form-tip">范围 1-255，默认 50</span>
        </el-form-item>
        <el-form-item label="停留时间" prop="staySeconds">
          <el-input-number v-model="cruiseForm.staySeconds" :min="1" :max="3600" />
          <span class="form-tip">秒，每点默认停留时间</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cruiseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCruiseConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增巡航点对话框 -->
    <el-dialog
      v-model="addPointVisible"
      title="添加巡航点"
      width="460px"
      top="18vh"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="选择预置位">
          <el-select v-model="addPointForm.presetId" placeholder="请选择预置位" style="width: 100%">
            <el-option
              v-for="opt in addPointPresetOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="停留时间">
          <el-input-number v-model="addPointForm.staySeconds" :min="1" :max="3600" :controls="false" placeholder="留空用路线默认" />
          <span class="form-tip">秒，留空使用路线默认值</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addPointVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddPointConfirm">确定</el-button>
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
  height: 65vh;
  min-height: 600px;
}

/* 左侧 */
.left-panel {
  flex: 0 0 480px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
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

.ptz-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ptz-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.ptz-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.tab-content {
  height: 100%;
  min-height: 0;
}

/* 巡航布局：上下两块 */
.cruise-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
}
.cruise-list-section {
  flex: 0 0 180px;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 0;
}
.cruise-points-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 0;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.section-title {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}
.sub-text {
  font-weight: normal;
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
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
