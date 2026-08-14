<script setup lang="ts">
/**
 * 推送目录挂载管理弹窗（per-platform 推送树 + catalog item 挂载）
 *
 * <p>左右双栏布局：
 * <ul>
 *   <li>左侧：推送目录树（gb_platform_group） + CRUD（新建/编辑/删除），share 表的 group_id
 *       指向这里——必须先在左侧选中分组才能挂载/卸载/清除</li>
 *   <li>右侧：<b>必须先在左侧选中分组</b>展示该组已挂载通道（gb_platform_share，只存通道）；
 *       搜索 + 卸载按钮 + 添加 / 一键添加 / 清除三个操作按钮</li>
 * </ul>
 *
 * <p>挂载语义（gb_platform_share，2026-08 改造）：
 * <ul>
 *   <li>{@code group_id} → 推送分组（必选，UI 强制）；{@code resource_id} → gb_device_channel.id
 *       （<b>只存通道</b>，parental=0 的行；分组不入 share 表）</li>
 *   <li>同一通道在同一平台下只能挂载一次（uk_platform_resource 是 (platform_id, resource_id)
 *       联合唯一），换组要先卸载旧组</li>
 *   <li>{@code exposed_code}：撞码时按 (deviceId, channelId) SHA-256 派生
 *       （ADR-026 决策 2），平台内唯一</li>
 *   <li>推送时 XML {@code <DeviceID>} = exposedCode ?? channelId（{@code GbDeviceChannel.getExposedOrChannelId()}）</li>
 * </ul>
 * </p>
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import {
  Plus,
  Edit,
  Delete,
  Folder,
  FolderOpened,
  VideoCamera,
  Search,
  QuestionFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { GbDeviceChannel, GbPlatformGroup, GbPlatformGroupSaveRequest } from '@/types/api'
import {
  getParentPlatformShareGroupTreeApi,
  getParentPlatformSharedByGroupPageApi,
  getParentPlatformUnmountedChannelsApi,
  clearAllSharesByPlatformApi,
  addSharesApi,
  addAllSharesApi,
  removeShareApi,
  clearSharesApi,
  savePlatformGroupApi,
  updatePlatformGroupApi,
  deletePlatformGroupApi,
} from '@/api/gb28181'

const props = defineProps<{
  modelValue: boolean
  platformId?: number | string
  platformName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
}>()

const ROOT_ID = 0

// ---------------- 数据 ----------------
const loading = ref(false)
// 区分当前正在进行的操作，避免"一键清除"和"一键添加"等按钮互抢 loading 状态
const pendingAction = ref<'' | 'addAll' | 'clearAll' | 'clearGroup'>('')
const groupTree = ref<GbPlatformGroup[]>([])

// 右侧"已共享"列表
const rightKeyword = ref('')
const rightPage = ref(1)
const rightPageSize = ref(20)
const rightTotal = ref(0)
const rightList = ref<GbDeviceChannel[]>([])
let searchTimer: number | null = null

// 左侧"推送目录树"当前选中（仅高亮，不影响右侧）
const selectedGroupId = ref<number | null>(null)

const selectedGroup = computed<GbPlatformGroup | null>(() => {
  if (selectedGroupId.value == null) return null
  function find(nodes: GbPlatformGroup[]): GbPlatformGroup | null {
    for (const n of nodes) {
      if (n.id === selectedGroupId.value) return n
      const r = find(n.children || [])
      if (r) return r
    }
    return null
  }
  return find(groupTree.value)
})

// ---------------- 加载 ----------------
async function loadTree() {
  if (!props.platformId) return
  const res = await getParentPlatformShareGroupTreeApi(props.platformId)
  groupTree.value = res.data || []
}

async function loadShared() {
  if (!props.platformId || selectedGroupId.value == null) {
    rightList.value = []
    rightTotal.value = 0
    return
  }
  loading.value = true
  try {
    const res = await getParentPlatformSharedByGroupPageApi(
      props.platformId,
      selectedGroupId.value,
      {
        page: rightPage.value,
        pageSize: rightPageSize.value,
        keyword: rightKeyword.value.trim() || undefined,
      }
    )
    rightList.value = res.data?.rows || []
    rightTotal.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error('加载已挂载列表失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function loadData() {
  if (!props.platformId) {
    ElMessage.warning('缺少上级平台 id，无法加载数据')
    return
  }
  loading.value = true
  try {
    await loadTree()
    // 默认进：<b>不</b>自动选中分组——必须由用户在左侧手动选才能挂载（"必须要选中左侧的目录树后才能挂载"）。
    selectedGroupId.value = null
  } catch (e: any) {
    ElMessage.error('加载推送目录失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

watch(selectedGroupId, () => {
  // 切换分组：重置分页 + 关键字 + 拉该组已挂载
  rightPage.value = 1
  rightKeyword.value = ''
  loadShared()
})

watch(
  () => props.modelValue,
  (v) => {
    if (v) loadData()
  }
)

onBeforeUnmount(() => {
  if (searchTimer) window.clearTimeout(searchTimer)
  if (pickerSearchTimer) window.clearTimeout(pickerSearchTimer)
})

// ---------------- 搜索 / 分页 ----------------
function onSearchInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    rightPage.value = 1
    loadShared()
  }, 250)
}

function onPageChange(p: number) {
  rightPage.value = p
  loadShared()
}

function onSizeChange(s: number) {
  rightPageSize.value = s
  rightPage.value = 1
  loadShared()
}

// ---------------- 树交互（仅高亮 / 导航） ----------------
function onNodeClick(data: GbPlatformGroup) {
  selectedGroupId.value = data.id
}

// ---------------- 新建 / 编辑分组（嵌套弹窗） ----------------
const editDialogVisible = ref(false)
const editDialogTitle = ref('新建分组')
const editingGroupId = ref<number | null>(null)
const editFormRef = ref()
const editForm = ref<GbPlatformGroupSaveRequest>({ name: '', code: '', parentId: ROOT_ID })
const editSubmitting = ref(false)

const editRules = {
  name: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入分组编码', trigger: 'blur' },
    { pattern: /^\d{10}(215|216)\d{7}$/, message: '必须是 20 位数字，且第 11-13 位为 215 或 216', trigger: 'blur' },
  ],
}

function handleCreateGroup() {
  if (!props.platformId) {
    ElMessage.warning('缺少上级平台 id')
    return
  }
  openEditDialog('新建顶级分组', null, ROOT_ID)
}

function handleAddChildGroup(parent: GbPlatformGroup) {
  if (!props.platformId) {
    ElMessage.warning('缺少上级平台 id')
    return
  }
  openEditDialog(`新建子分组（父：${parent.name}）`, null, parent.id)
}

function handleEditGroup(g: GbPlatformGroup) {
  openEditDialog('编辑分组', g.id, g.parentId, g.name, g.code)
}

function openEditDialog(
  title: string,
  groupId: number | null,
  parentId: number,
  name = '',
  code = ''
) {
  editDialogTitle.value = title
  editingGroupId.value = groupId
  editForm.value = { name, code, parentId }
  editDialogVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

async function handleConfirmEdit() {
  if (!editFormRef.value) return
  if (!props.platformId) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  editSubmitting.value = true
  try {
    if (editingGroupId.value == null) {
      await savePlatformGroupApi(props.platformId, editForm.value)
      ElMessage.success('新建成功')
    } else {
      await updatePlatformGroupApi(props.platformId, editingGroupId.value, editForm.value)
      ElMessage.success('修改成功')
    }
    editDialogVisible.value = false
    await loadTree()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || '未知错误'))
  } finally {
    editSubmitting.value = false
  }
}

async function handleDeleteGroup(g: GbPlatformGroup) {
  if (!props.platformId) return
  try {
    await ElMessageBox.confirm(
      `确定删除分组「${g.name}」？该操作不影响已挂载的 catalog item。`,
      '提示',
      { type: 'warning' }
    )
    await deletePlatformGroupApi(props.platformId, g.id)
    ElMessage.success('删除成功')
    if (selectedGroupId.value === g.id) selectedGroupId.value = null
    await loadTree()
  } catch {
    // cancelled
  }
}

// ---------------- 添加（嵌套弹窗：未挂载通道多选） ----------------
const pickerVisible = ref(false)
const pickerSelected = ref<number[]>([])
const pickerKeyword = ref('')
const pickerPage = ref(1)
const pickerPageSize = ref(20)
const pickerTotal = ref(0)
const pickerList = ref<GbDeviceChannel[]>([])
const pickerLoading = ref(false)
let pickerSearchTimer: number | null = null

async function loadPickerChannels() {
  if (!props.platformId || selectedGroupId.value == null) return
  pickerLoading.value = true
  try {
    const res = await getParentPlatformUnmountedChannelsApi(
      props.platformId,
      selectedGroupId.value,
      {
        page: pickerPage.value,
        pageSize: pickerPageSize.value,
        keyword: pickerKeyword.value.trim() || undefined,
      }
    )
    pickerList.value = res.data?.rows || []
    pickerTotal.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error('加载未挂载通道失败：' + (e?.message || '未知错误'))
  } finally {
    pickerLoading.value = false
  }
}

function openPicker() {
  if (selectedGroupId.value == null) {
    ElMessage.warning('请先在左侧选择一个分组')
    return
  }
  pickerSelected.value = []
  pickerKeyword.value = ''
  pickerPage.value = 1
  pickerVisible.value = true
  loadPickerChannels()
}

/** 全选本页：把当前页所有未挂载通道加到选择集合（不冲掉已选跨页项） */
function selectAllOnPage() {
  const ids = pickerList.value.map((c) => c.id)
  const merged = new Set(pickerSelected.value)
  for (const id of ids) merged.add(id)
  pickerSelected.value = Array.from(merged)
}

/** 全不选：清空选择 */
function clearAllSelected() {
  pickerSelected.value = []
}

function onPickerSearchInput() {
  if (pickerSearchTimer) window.clearTimeout(pickerSearchTimer)
  pickerSearchTimer = window.setTimeout(() => {
    pickerPage.value = 1
    pickerSelected.value = []
    loadPickerChannels()
  }, 250)
}

function onPickerPageChange(p: number) {
  pickerPage.value = p
  loadPickerChannels()
}

function onPickerSizeChange(s: number) {
  pickerPageSize.value = s
  pickerPage.value = 1
  loadPickerChannels()
}

async function handlePickerConfirm() {
  if (!props.platformId || selectedGroupId.value == null) return
  if (pickerSelected.value.length === 0) {
    ElMessage.warning('请先勾选要挂载的通道')
    return
  }
  try {
    const res = await addSharesApi(props.platformId, selectedGroupId.value, {
      resourceIds: pickerSelected.value,
    })
    ElMessage.success(`挂载成功（新增 ${res.data?.added ?? pickerSelected.value.length} 条）`)
    pickerVisible.value = false
    await loadShared()
  } catch (e: any) {
    ElMessage.error('挂载失败：' + (e?.message || '未知错误'))
  }
}

// ---------------- 卸载（右侧每行） ----------------
async function handleUnmount(row: GbDeviceChannel) {
  if (!props.platformId || selectedGroupId.value == null) return
  try {
    await ElMessageBox.confirm(
      `确定从「${selectedGroup.value?.name || ''}」卸载「${row.name || row.channelId}」？该操作不影响 catalog item 本身。`,
      '提示',
      { type: 'warning' }
    )
    await removeShareApi(props.platformId, selectedGroupId.value, row.id)
    ElMessage.success('卸载成功')
    await loadShared()
  } catch {
    // cancelled
  }
}

// ---------------- 清除本组挂载（树节点上"清除本组"按钮） ----------------
async function handleClearGroup(g: GbPlatformGroup) {
  if (!props.platformId) return
  // 先查本组当前挂载条数（带在 tree 上时右侧不一定加载过）
  let count = 0
  try {
    const res = await getParentPlatformSharedByGroupPageApi(
      props.platformId,
      g.id,
      { page: 1, pageSize: 1, keyword: '' }
    )
    count = res.data?.total ?? 0
  } catch {
    /* 查不到就走下面 0 的分支提示 */
  }
  if (count === 0) {
    ElMessage.warning(`「${g.name}」下没有挂载通道`)
    return
  }
  let confirmed = false
  try {
    await ElMessageBox.confirm(
      `确定清空「${g.name}」的挂载吗`,
      '清空确认',
      { type: 'warning' }
    )
    confirmed = true
  } catch {
    // 用户取消
  }
  if (!confirmed) return

  loading.value = true
  pendingAction.value = 'clearGroup'
  try {
    const res = await clearSharesApi(props.platformId, g.id)
    ElMessage.success(`已清空「${g.name}」的挂载`)
    // 当前选中的就是被清的组时才需要重新拉右侧列表
    if (selectedGroupId.value === g.id) await loadShared()
  } catch (e: any) {
    ElMessage.error('清除失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
    pendingAction.value = ''
  }
}

// ---------------- 清除该平台下全部挂载（右侧大按钮，不分分组） ----------------
async function handleClearAll() {
  if (!props.platformId) return
  let confirmed = false
  try {
    await ElMessageBox.confirm('确定全部清除吗', '清空确认', { type: 'warning' })
    confirmed = true
  } catch {
    // 用户取消
  }
  if (!confirmed) return

  loading.value = true
  pendingAction.value = 'clearAll'
  try {
    const res = await clearAllSharesByPlatformApi(props.platformId)
    ElMessage.success('已清空该平台的全部挂载')
    // 当前选中组可能也被清了 → 重新拉已挂列表
    await loadShared()
  } catch (e: any) {
    ElMessage.error('清除失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
    pendingAction.value = ''
  }
}

// ---------------- 一键添加全部未挂载 ----------------
async function handleAddAll() {
  if (!props.platformId || selectedGroupId.value == null) return
  loading.value = true
  pendingAction.value = 'addAll'
  try {
    const res = await addAllSharesApi(props.platformId, selectedGroupId.value)
    ElMessage.success(`一键添加完成（新增 ${res.data?.added ?? 0} 条）`)
    await loadShared()
  } catch (e: any) {
    ElMessage.error('一键添加失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
    pendingAction.value = ''
  }
}

// ---------------- 关闭 ----------------
function handleClose() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`推送目录 - ${platformName || '上级平台'}`"
    width="1200px"
    top="3vh"
    :close-on-click-modal="false"
    class="share-directory-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-content">
      <div class="split-layout">
        <!-- 左侧：推送目录树（与 share 表解耦，仅 admin UI 组织树） -->
        <div class="left-pane">
          <div class="pane-header">
            <span class="pane-title">推送目录树</span>
            <div class="pane-actions">
              <el-button v-permission="'gb28181:share-group:add'" type="primary" size="small" :icon="Plus" @click="handleCreateGroup">新建</el-button>
            </div>
          </div>
          <el-tree
            :data="groupTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            highlight-current
            :expand-on-click-node="false"
            :default-expanded-keys="groupTree.map((g) => g.id)"
            class="group-tree"
            @node-click="onNodeClick"
          >
            <template #default="{ node, data }">
              <span class="tree-node-content">
                <el-icon class="tree-icon">
                  <component :is="(data.children?.length ? FolderOpened : Folder)" />
                </el-icon>
                <span class="tree-text">{{ data.name }} <span class="tree-code">({{ data.code }})</span></span>
                <span class="tree-actions">
                  <el-tooltip content="新建子分组" placement="top">
                    <el-button
                      v-permission="'gb28181:share-group:add'"
                      type="primary"
                      link
                      :icon="Plus"
                      size="small"
                      @click.stop="handleAddChildGroup(data)"
                    />
                  </el-tooltip>
                  <el-tooltip content="编辑" placement="top">
                    <el-button
                      v-permission="'gb28181:share-group:edit'"
                      type="primary"
                      link
                      :icon="Edit"
                      size="small"
                      @click.stop="handleEditGroup(data)"
                    />
                  </el-tooltip>
                  <el-tooltip content="清除本组挂载" placement="top">
                    <el-button
                      v-permission="'gb28181:parent-platform:edit'"
                      type="warning"
                      link
                      :icon="Delete"
                      size="small"
                      @click.stop="handleClearGroup(data)"
                    />
                  </el-tooltip>
                  <el-tooltip content="删除分组" placement="top">
                    <el-button
                      v-permission="'gb28181:share-group:delete'"
                      type="danger"
                      link
                      :icon="Delete"
                      size="small"
                      @click.stop="handleDeleteGroup(data)"
                    />
                  </el-tooltip>
                </span>
              </span>
            </template>
          </el-tree>
        </div>

        <!-- 右侧：当前选中组的已挂载通道列表 -->
        <div class="right-pane">
          <div class="pane-header">
            <div class="pane-actions">
              <el-input
                v-model="rightKeyword"
                size="small"
                placeholder="搜索名称 / 国标编码"
                :prefix-icon="Search"
                clearable
                style="width: 280px"
                :disabled="selectedGroupId == null"
                @input="onSearchInput"
              />
              <el-button
                v-permission="'gb28181:parent-platform:edit'"
                type="warning"
                size="small"
                :icon="Plus"
                plain
                :loading="loading && pendingAction === 'addAll'"
                :disabled="selectedGroupId == null"
                @click="handleAddAll"
              >
                一键添加
              </el-button>
              <el-button
                v-permission="'gb28181:parent-platform:edit'"
                type="danger"
                size="small"
                :icon="Delete"
                plain
                :loading="loading && pendingAction === 'clearAll'"
                @click="handleClearAll"
              >
                一键清除
              </el-button>
              <el-button
                v-permission="'gb28181:parent-platform:edit'"
                type="primary"
                size="small"
                :icon="Plus"
                :disabled="selectedGroupId == null"
                @click="openPicker"
              >
                添加
              </el-button>
            </div>
          </div>

          <div class="channel-list" v-loading="loading">
            <div v-if="selectedGroupId == null" class="empty-tip">
              请先在左侧选择一个推送分组
            </div>
            <div v-else-if="rightList.length === 0" class="empty-tip">
              暂无已挂载项<br />
              <span class="empty-sub">点击右上角"添加"从 catalog 选择</span>
            </div>
            <el-table v-else :data="rightList" border stripe size="small" max-height="calc(80vh - 280px)">
              <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
              <el-table-column label="国标编码" min-width="200">
                <template #default="{ row }">
                  <span>{{ row.channelId }}</span>
                </template>
              </el-table-column>
              <el-table-column label="推送国标码" min-width="200">
                <template #header>
                  <span>推送国标码</span>
                  <el-tooltip
                    placement="top"
                    content="实际推送给上级平台的国标码。普通通道为空时表示透传设备原始 GB 码；多个通道国标码相同时会被系统重码成唯一码"
                  >
                    <el-icon class="header-tip"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
                <template #default="{ row }">
                  <span class="expose-code">{{ row.exposedCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center" fixed="right">
                <template #default="{ row }">
                  <el-tooltip content="卸载" placement="top">
                    <el-button
                      v-permission="'gb28181:parent-platform:edit'"
                      type="danger"
                      :icon="Delete"
                      link
                      size="small"
                      @click="handleUnmount(row)"
                    />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-pagination
            v-if="rightTotal > 0"
            class="channel-pager"
            :current-page="rightPage"
            :page-size="rightPageSize"
            :total="rightTotal"
            :page-sizes="[10, 20, 50, 100]"
            small
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleClose">关 闭</el-button>
    </template>
  </el-dialog>

  <!-- 新建 / 编辑分组（嵌套弹窗） -->
  <el-dialog
    v-model="editDialogVisible"
    :title="editDialogTitle"
    width="520px"
    :close-on-click-modal="false"
    append-to-body
    align-center
  >
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px" label-position="right">
      <el-form-item label="分组名称" prop="name">
        <el-input v-model="editForm.name" placeholder="请输入分组名称（如：大门、出入口）" maxlength="64" show-word-limit clearable />
      </el-form-item>
      <el-form-item label="分组编码" prop="code">
        <el-input
          v-model="editForm.code"
          placeholder="20 位国标编码"
          maxlength="20"
          clearable
        />
        <div class="form-tip">20 位数字，第 11-13 位必须为 215 或 216（如 11000000002150000001）</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="editSubmitting" @click="handleConfirmEdit">确 定</el-button>
    </template>
  </el-dialog>

  <!-- 选择未挂载通道（嵌套弹窗） -->
  <el-dialog
    v-model="pickerVisible"
    title="选择要挂载的通道"
    width="800px"
    :close-on-click-modal="false"
    append-to-body
    @closed="pickerSelected = []"
  >
    <div class="picker-toolbar">
      <el-input
        v-model="pickerKeyword"
        size="small"
        placeholder="搜索名称 / 国标编码"
        :prefix-icon="Search"
        clearable
        style="width: 220px"
        @input="onPickerSearchInput"
      />
      <div class="picker-actions">
        <el-button
          size="small"
          :disabled="pickerList.length === 0"
          @click="selectAllOnPage"
        >
          全选
        </el-button>
        <el-button
          size="small"
          :disabled="pickerSelected.length === 0"
          @click="clearAllSelected"
        >
          全不选
        </el-button>
      </div>
    </div>
    <div class="picker-list" v-loading="pickerLoading">
      <div v-if="pickerList.length === 0" class="empty-tip">暂无未挂载通道</div>
      <el-checkbox-group v-else v-model="pickerSelected" class="channel-check-group">
        <div v-for="ch in pickerList" :key="ch.id" class="channel-item">
          <el-checkbox :value="ch.id">
            {{ ch.name || '(无名称)' }} ({{ ch.channelId }})
          </el-checkbox>
        </div>
      </el-checkbox-group>
    </div>
    <el-pagination
      v-if="pickerTotal > 0"
      class="picker-pager"
      :current-page="pickerPage"
      :page-size="pickerPageSize"
      :total="pickerTotal"
      :page-sizes="[10, 20, 50, 100]"
      small
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="onPickerPageChange"
      @size-change="onPickerSizeChange"
    />
    <template #footer>
      <el-button @click="pickerVisible = false">取 消</el-button>
      <el-button type="primary" :disabled="pickerSelected.length === 0" @click="handlePickerConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-content {
  max-height: calc(80vh - 120px);
  overflow-y: auto;
  padding: 0 4px;
}

/* 左右分栏：左侧推送树固定 360px（名字+国标编码展示够用），右侧已挂载表占剩余宽度 */
.split-layout {
  display: flex;
  gap: 16px;
  min-height: 420px;
}
.left-pane {
  flex: 0 0 360px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.right-pane {
  flex: 1;
  min-width: 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 面板头 */
.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  gap: 8px;
}
.pane-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.pane-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

/* 树 */
.group-tree {
  flex: 1;
  padding: 8px 4px;
  overflow-y: auto;
  background: transparent;
}
.group-tree :deep(.el-tree-node__content) {
  height: 32px;
}
.tree-node-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  width: 100%;
  padding-right: 8px;
  cursor: pointer;
}
.tree-icon {
  color: #e6a23c;
}
.tree-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-code {
  color: #909399;
  font-size: 12px;
}
/* 节点行尾操作按钮：默认透明，hover 行时显示 */
.tree-actions {
  display: inline-flex;
  gap: 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.tree-node-content:hover .tree-actions {
  opacity: 1;
}
.tree-actions .el-button {
  padding: 0 4px;
}

/* 右侧已挂载表 */
.channel-list {
  flex: 1;
  padding: 8px 12px;
  overflow-y: auto;
}
.channel-pager {
  padding: 4px 8px 8px;
  justify-content: flex-end;
  background: #fafafa;
  border-top: 1px solid #e4e7ed;
}
.expose-code {
  font-family: 'Menlo', 'Consolas', monospace;
  color: #409eff;
}
.header-tip {
  margin-left: 4px;
  color: #909399;
  cursor: help;
  vertical-align: -2px;
}
.muted {
  color: #c0c4cc;
}

.empty-tip {
  padding: 60px 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.empty-sub {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: #c0c4cc;
}

/* 选择未挂载通道弹窗 */
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
  gap: 12px;
}
.picker-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.picker-list {
  max-height: 50vh;
  overflow-y: auto;
  padding: 4px 0;
}
.picker-pager {
  margin-top: 8px;
  justify-content: flex-end;
}
.channel-check-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.channel-item {
  padding: 3px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.channel-item:hover {
  background: #f0f2f5;
}

/* 新建/编辑分组弹窗内字段说明 */
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
