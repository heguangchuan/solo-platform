<script setup lang="ts">
/**
 * 推送目录管理弹窗（per-platform 推送树 CRUD + 通道挂载）
 *
 * <p>左右双栏布局：
 * <ul>
 *   <li>左侧：推送目录树（gb_platform_group） + CRUD（新建/编辑/删除）</li>
 *   <li>右侧：未挂载通道列表 + 挂载到选中组</li>
 * </ul>
 *
 * <p>当前版本：分组 CRUD 已对接后端。挂载 / 卸载接口待后续迭代"推送保存"功能时落地。</p>
 */
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import {
  Plus,
  Edit,
  Delete,
  Folder,
  FolderOpened,
  VideoCamera,
  Connection,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { GbDeviceChannel, GbPlatformGroup, GbPlatformGroupSaveRequest } from '@/types/api'
import {
  getParentPlatformShareGroupTreeApi,
  getParentPlatformUnmountedChannelsApi,
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

// ---------------- 数据（真实后端） ----------------
const loading = ref(false)
const groupTree = ref<GbPlatformGroup[]>([])
const unmountedChannelList = ref<GbDeviceChannel[]>([])

const selectedGroupId = ref<number | null>(null)
const selectedUnmountedIds = ref<string[]>([])

// 分页 / 搜索
const unmountedKeyword = ref('')
const unmountedPage = ref(1)
const unmountedPageSize = ref(20)
const unmountedTotal = ref(0)
let searchTimer: number | null = null

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

async function loadUnmountedChannels() {
  if (!props.platformId) return
  loading.value = true
  try {
    const res = await getParentPlatformUnmountedChannelsApi(props.platformId, {
      page: unmountedPage.value,
      pageSize: unmountedPageSize.value,
      keyword: unmountedKeyword.value.trim() || undefined,
    })
    unmountedChannelList.value = res.data?.rows || []
    unmountedTotal.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error('加载未挂载通道失败：' + (e?.message || '未知错误'))
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
    await loadUnmountedChannels()
    selectedGroupId.value = null
    selectedUnmountedIds.value = []
    unmountedPage.value = 1
    unmountedKeyword.value = ''
  } catch (e: any) {
    ElMessage.error('加载推送目录失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

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
    unmountedPage.value = 1
    // 关键字变了：之前跨页选中的可能搜不到了，清空避免误导
    selectedUnmountedIds.value = []
    loadUnmountedChannels()
  }, 250)
}

function onPageChange(p: number) {
  unmountedPage.value = p
  loadUnmountedChannels()
}

function onSizeChange(s: number) {
  unmountedPageSize.value = s
  unmountedPage.value = 1
  loadUnmountedChannels()
}

// ---------------- 树交互 ----------------
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

/**
 * 顶部"新建"按钮：强制顶级（parentId = 0）
 */
function handleCreateGroup() {
  if (!props.platformId) {
    ElMessage.warning('缺少上级平台 id')
    return
  }
  openEditDialog('新建顶级分组', null, ROOT_ID)
}

/**
 * 节点"+"按钮：在选中节点下新建子分组
 */
function handleAddChildGroup(parent: GbPlatformGroup) {
  if (!props.platformId) {
    ElMessage.warning('缺少上级平台 id')
    return
  }
  openEditDialog(`新建子分组（父：${parent.name}）`, null, parent.id)
}

/**
 * 节点"编辑"按钮
 */
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
    await loadData()
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
      `确定删除分组「${g.name}」？该分组下的挂载关系将一并解除。`,
      '提示',
      { type: 'warning' }
    )
    await deletePlatformGroupApi(props.platformId, g.id)
    ElMessage.success('删除成功')
    if (selectedGroupId.value === g.id) selectedGroupId.value = null
    await loadData()
  } catch {
    // cancelled
  }
}

// ---------------- 挂载 / 卸载（待后端实现） ----------------
function notImplemented(action: string) {
  ElMessage.warning(`「${action}」待推送保存功能实现后开放`)
}
function handleMount() { notImplemented('挂载通道') }
function handleUnmount() { notImplemented('卸载通道') }

// ---------------- 选中组 → "添加挂载"嵌套弹窗 ----------------
const pickerVisible = ref(false)
const pickerSelected = ref<string[]>([])

const pickerKeyword = ref('')
const pickerPage = ref(1)
const pickerPageSize = ref(20)
const pickerTotal = ref(0)
const pickerList = ref<GbDeviceChannel[]>([])
const pickerLoading = ref(false)
let pickerSearchTimer: number | null = null

async function loadPickerChannels() {
  if (!props.platformId) return
  pickerLoading.value = true
  try {
    const res = await getParentPlatformUnmountedChannelsApi(props.platformId, {
      page: pickerPage.value,
      pageSize: pickerPageSize.value,
      keyword: pickerKeyword.value.trim() || undefined,
    })
    pickerList.value = res.data?.rows || []
    pickerTotal.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error('加载未挂载通道失败：' + (e?.message || '未知错误'))
  } finally {
    pickerLoading.value = false
  }
}

function openPicker() {
  pickerSelected.value = []
  pickerKeyword.value = ''
  pickerPage.value = 1
  pickerVisible.value = true
  loadPickerChannels()
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

function handlePickerConfirm() {
  if (pickerSelected.value.length === 0) {
    ElMessage.warning('请先勾选要挂载的通道')
    return
  }
  const groupName = selectedGroup.value?.name ?? '未选组'
  // 挂载接口未实现：弹占位提示
  notImplemented(`批量挂载到「${groupName}」（${pickerSelected.value.length} 条）`)
  pickerVisible.value = false
}

function handleClose() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`推送目录 - ${platformName || '上级平台'}`"
    width="1000px"
    top="3vh"
    :close-on-click-modal="false"
    class="share-directory-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="dialog-content">
      <div class="split-layout">
        <!-- 左侧：推送目录树 -->
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
                  <el-tooltip content="删除" placement="top">
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

        <!-- 右侧：未挂载 / 已挂载切换 -->
        <div class="right-pane">
          <div class="pane-header">
            <span class="pane-title">
              {{ selectedGroup ? `已挂载通道（${selectedGroup.name}）` : '未挂载通道' }}
            </span>
            <div class="pane-actions">
              <!-- 未选组：顶部放搜索 -->
              <el-input
                v-if="!selectedGroup"
                v-model="unmountedKeyword"
                size="small"
                placeholder="搜索通道"
                :prefix-icon="Search"
                clearable
                style="width: 180px"
                @input="onSearchInput"
              />
              <!-- 选中组：顶部放"添加挂载" -->
              <el-button
                v-else
                v-permission="'gb28181:share-group:edit'"
                type="primary"
                size="small"
                :icon="Plus"
                @click="openPicker"
              >
                添加挂载
              </el-button>
            </div>
          </div>

          <!-- 列表区 -->
          <div class="channel-list" v-loading="loading">
            <!-- 未选组：未挂载通道分页列表 -->
            <template v-if="!selectedGroup">
              <div v-if="unmountedChannelList.length === 0" class="empty-tip">
                暂无未挂载通道
              </div>
              <el-checkbox-group v-else v-model="selectedUnmountedIds" class="channel-check-group">
                <div v-for="ch in unmountedChannelList" :key="ch.id" class="channel-item">
                  <el-checkbox :value="ch.id">
                    <span class="channel-name">
                      <el-icon class="channel-icon"><VideoCamera /></el-icon>
                      {{ ch.name || '(无名称)' }}
                    </span>
                    <span class="channel-code">({{ ch.channelId }})</span>
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </template>

            <!-- 选中组：已挂载占位（接口未实现时为空） -->
            <template v-else>
              <div class="empty-tip">
                暂无已挂载通道<br />
                <span class="empty-sub">挂载关系接口待推送保存功能落地后开放</span>
              </div>
            </template>
          </div>

          <!-- 分页器：仅未选组时显示 -->
          <el-pagination
            v-if="!selectedGroup && unmountedTotal > 0"
            class="channel-pager"
            :current-page="unmountedPage"
            :page-size="unmountedPageSize"
            :total="unmountedTotal"
            :page-sizes="[10, 20, 50, 100]"
            small
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />

          <div class="pane-footer" v-if="!selectedGroup">
            <el-button
              v-permission="'gb28181:share-group:edit'"
              type="primary"
              size="small"
              :icon="Connection"
              :disabled="!selectedGroup || selectedUnmountedIds.length === 0"
              @click="handleMount"
            >
              挂载到「{{ selectedGroup?.name || '未选组' }}」
            </el-button>
          </div>
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
    width="360px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
      <el-form-item label="分组名称" prop="name">
        <el-input v-model="editForm.name" placeholder="分组名称" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="分组编码" prop="code">
        <el-input v-model="editForm.code" placeholder="20 位数字，第 11-13 位为 215 或 216" maxlength="20" />
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
    :title="`选择要挂载到「${selectedGroup?.name || ''}」的通道`"
    width="640px"
    :close-on-click-modal="false"
    append-to-body
    @closed="pickerSelected = []"
  >
    <div class="picker-toolbar">
      <el-input
        v-model="pickerKeyword"
        size="small"
        placeholder="搜索通道"
        :prefix-icon="Search"
        clearable
        style="width: 220px"
        @input="onPickerSearchInput"
      />
      <span class="picker-hint">已选 {{ pickerSelected.length }} 条</span>
    </div>
    <div class="picker-list" v-loading="pickerLoading">
      <div v-if="pickerList.length === 0" class="empty-tip">暂无未挂载通道</div>
      <el-checkbox-group v-else v-model="pickerSelected" class="channel-check-group">
        <div v-for="ch in pickerList" :key="ch.id" class="channel-item">
          <el-checkbox :value="ch.id">
            <span class="channel-name">
              <el-icon class="channel-icon"><VideoCamera /></el-icon>
              {{ ch.name || '(无名称)' }}
            </span>
            <span class="channel-code">({{ ch.channelId }})</span>
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
      <el-button type="primary" @click="handlePickerConfirm">挂 载</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-content {
  max-height: calc(80vh - 120px);
  overflow-y: auto;
  padding: 0 4px;
}

/* 左右分栏 */
.split-layout {
  display: flex;
  gap: 16px;
  min-height: 420px;
}
.left-pane,
.right-pane {
  flex: 1;
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
}
.pane-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.pane-actions {
  display: flex;
  gap: 4px;
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

/* 通道列表 */
.channel-list {
  flex: 1;
  padding: 8px 12px;
  overflow-y: auto;
}
.channel-check-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.channel-item {
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.channel-item:hover {
  background: #f0f2f5;
}
.channel-name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
}
.channel-icon {
  color: #409eff;
  font-size: 14px;
}
.channel-code {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.pane-footer {
  padding: 8px 12px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  display: flex;
  justify-content: flex-end;
}

.channel-pager {
  padding: 4px 8px 8px;
  justify-content: flex-end;
  background: #fafafa;
  border-top: 1px solid #e4e7ed;
}

/* 已挂载占位次级文案 */
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
}
.picker-hint {
  font-size: 12px;
  color: #909399;
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

/* 已挂载区 */
.mounted-section {
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
}
.mounted-list {
  padding: 10px 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.mounted-tag {
  margin: 0;
}
.empty-tip {
  padding: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* 父分组输入提示 */
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
