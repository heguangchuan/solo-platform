<script setup lang="ts">
/**
 * 首页左侧设备 / 通道树
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>抽屉式：可隐藏，靠右上角 × 或外层 toggle 按钮控制</li>
 *   <li>el-tree 懒加载：root 展开 → 拉设备列表；设备展开 → 拉前 50 个通道</li>
 *   <li>极端情况：单设备 4万 通道，用"加载更多"节点按 50/批 增量拉取
 *       （类似京东首页的滚动加载，但承载在树节点里）</li>
 *   <li>点通道：emit 'channel-click'，父组件调 MapPanel.locateToChannel
 *       → map.flyTo + openPopup</li>
 * </ul>
 *
 * <h3>为什么"加载更多"用节点而不是按钮</h3>
 * <ul>
 *   <li>el-tree 没法在某个父节点的 children 末尾插一个自定义按钮</li>
 *   <li>用 isLeaf: true 的伪节点 + @click.stop 触发，updateKeyChildren 替换子节点
 *       保持节点形态统一（不需要切自定义组件）</li>
 * </ul>
 */
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { Monitor, VideoCamera, Expand, Fold } from '@element-plus/icons-vue'
import { getGbDevicePageApi, getGbDeviceChannelPageApi } from '@/api/gb28181'
import type { GbDevice, GbDeviceChannelVO } from '@/types/api/gb28181'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'channel-click', channel: GbDeviceChannelVO): void
}>()

// el-tree 实例：用来调 updateKeyChildren 替换设备子节点（"加载更多"用）
const treeRef = ref()
// 树容器 ref：IntersectionObserver 的 root 节点（用 panel-tree 这个 scroll 容器，触发更精准）
const treeContainerRef = ref<HTMLElement | null>(null)

// deviceId (string 后端主键) → { 已加载通道数组, 当前页, 总数 }
// 用普通 Map 不放 reactive——树组件自身负责渲染，这里只管数据源
const deviceStates = new Map<string, { channels: GbDeviceChannelVO[]; page: number; total: number }>()

// "加载更多"自动加载：每个 loadMore 节点挂一个 IntersectionObserver，
// 节点进入视口就自动调 loadMoreChannels（无需用户点击，类似京东首页无限滚动）
// 用 WeakMap 让 observer 跟节点的生命周期绑定（节点被 replace/移除时自动 GC）
const autoLoadObservers = new WeakMap<HTMLElement, IntersectionObserver>()
// 防并发锁：deviceId 维度，避免快速滚动触发多次重复请求
const loadingSet = new Set<string>()

// 节点 id 用 `${type}-${pk}` 形式，确保唯一
interface TreeNode {
  id: string
  label: string
  type: 'device' | 'channel' | 'loadMore'
  isLeaf: boolean
  data?: any
  deviceId?: string | number
  loaded?: number
  total?: number
}

const treeProps = {
  label: 'label',
  isLeaf: 'isLeaf',
  children: 'children',
}

/**
 * el-tree 懒加载回调
 * level 0: root → 拉设备列表
 * device 节点: → 拉前 50 个通道 + "加载更多"节点
 * loadMore 节点: 不会到这里（isLeaf: true），由 span 上的 @click.stop 直接调 loadMoreChannels</li>
 */
async function loadNode(node: any, resolve: (data: TreeNode[]) => void) {
  try {
    if (node.level === 0) {
      const { data } = await getGbDevicePageApi({ page: 1, pageSize: 50 })
      // 后端 PageResult 字段是 rows（不是 records），见 types/api/common.ts:13
      const nodes: TreeNode[] = data.rows.map((d: GbDevice) => ({
        id: `device-${d.deviceId ?? d.id}`,
        label: d.name ?? d.deviceId,
        type: 'device',
        isLeaf: false,
        data: d,
      }))
      resolve(nodes)
    } else if (node.data?.type === 'device') {
      const deviceId = node.data.data.deviceId ?? node.data.data.id
      const { data } = await getGbDeviceChannelPageApi({ deviceId, page: 1, pageSize: 50 })
      const channels = data.rows
      deviceStates.set(String(deviceId), { channels, page: 1, total: data.total })
      const children = buildDeviceChildren(deviceId, channels, data.total)
      resolve(children)
      // 设备首次展开后，"加载更多"节点已经在 DOM 里了 → 挂 observer
      // （后续 updateKeyChildren 替换时 loadMoreChannels 内部也会再调一次）
      await nextTick()
      setupAutoLoad()
    } else {
      resolve([])
    }
  } catch (e: any) {
    // eslint-disable-next-line no-console
    console.error('[设备树] 加载失败', e)
    // 后端通常把业务错误放在 response.data.msg（被 request.ts 拦截器塞进 error.message）
    ElMessage.error(`设备/通道加载失败：${e?.message || '未知错误'}`)
    resolve([])
  }
}

/**
 * 把"已加载通道数组"组装成 el-tree 节点列表
 * 末尾如果还有更多，就塞一个"加载更多"伪节点
 */
function buildDeviceChildren(deviceId: string | number, channels: GbDeviceChannelVO[], total: number): TreeNode[] {
  const nodes: TreeNode[] = channels.map(c => ({
    id: `channel-${c.id}`,
    label: c.name ?? c.channelId,
    type: 'channel',
    isLeaf: true,
    data: c,
  }))
  if (channels.length < total) {
    nodes.push({
      id: `loadMore-${deviceId}-${channels.length}`,
      label: `加载更多 (${channels.length}/${total})`,
      type: 'loadMore',
      isLeaf: true,  // 不让 el-tree 展开它（避免嵌套成它的子节点）
      deviceId,
      loaded: channels.length,
      total,
    })
  }
  return nodes
}

/**
 * 通道点击：手动调 tree.setCurrentKey 加 .is-current class
 * 原因：span 上的 @click.stop 阻断了 el-tree 内部 click handler，
 *  el-tree 不会自动给点击节点加 .is-current class，需要手动设
 * 配套的 CSS：.el-tree-node.is-current > .el-tree-node__content .node-row { color: #00E5FF }
 */
function handleChannelClick(channel: GbDeviceChannelVO, nodeKey: string) {
  const tree = treeRef.value
  if (tree) {
    tree.setCurrentKey(nodeKey)
  }
  emit('channel-click', channel)
}

async function loadMoreChannels(deviceId: string | number, _oldLoadMoreNode: any) {
  const key = String(deviceId)
  // 防并发：滚动可能瞬间触发多次（rootMargin 50px 预加载 + 快速滚动），避免重复请求
  if (loadingSet.has(key)) return
  loadingSet.add(key)
  try {
    const state = deviceStates.get(key)
    if (!state) return
    const nextPage = state.page + 1
    const { data } = await getGbDeviceChannelPageApi({ deviceId, page: nextPage, pageSize: 50 })
    state.channels.push(...data.rows)
    state.page = nextPage
    // 关键：替换设备节点的 children（不是替换"加载更多"节点的 children——那会嵌套）
    const tree = treeRef.value
    if (tree) {
      const deviceKey = `device-${deviceId}`
      const newChildren = buildDeviceChildren(deviceId, state.channels, state.total)
      tree.updateKeyChildren(deviceKey, newChildren)
      // 替换后新的"加载更多"节点要重新挂 IntersectionObserver（旧 observer 随旧节点 GC）
      await nextTick()
      setupAutoLoad()
    }
  } catch (e: any) {
    // eslint-disable-next-line no-console
    console.error('[设备树] 加载更多失败', e)
    ElMessage.error(`加载更多通道失败：${e?.message || '未知错误'}`)
  } finally {
    loadingSet.delete(key)
  }
}

/**
 * 给当前所有"加载更多"节点挂 IntersectionObserver
 * 节点进入视口 → 自动调 loadMoreChannels（无需点击）
 * 重复调用是幂等的：已经挂过 observer 的节点（用 WeakMap 标记）会跳过
 */
function setupAutoLoad() {
  const container = treeContainerRef.value
  if (!container) return
  const loadMoreNodes = container.querySelectorAll<HTMLElement>('.node-loadmore')
  loadMoreNodes.forEach(node => {
    if (autoLoadObservers.has(node)) return  // 已挂过，跳过
    const deviceId = node.getAttribute('data-device-id')
    if (!deviceId) return
    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting) {
        loadMoreChannels(deviceId, null)
      }
    }, {
      root: container,         // 监听 panel-tree 这个 scroll 容器，不是整个 window
      rootMargin: '50px',      // 离底 50px 就触发，提前预加载
      threshold: 0.1,
    })
    observer.observe(node)
    autoLoadObservers.set(node, observer)
  })
}

/**
 * 监听树容器 DOM 变化（el-tree 展开/折叠/替换节点时触发），
 * 一旦发现新出现的 .node-loadmore 节点就挂 observer
 * 节点被替换时旧 observer 由 WeakMap 自动 GC
 */
let treeMutationObserver: MutationObserver | null = null
function startTreeMutationWatcher() {
  const container = treeContainerRef.value
  if (!container || treeMutationObserver) return
  treeMutationObserver = new MutationObserver(() => {
    setupAutoLoad()
  })
  treeMutationObserver.observe(container, {
    childList: true,
    subtree: true,
  })
}

onMounted(() => {
  // 树容器 mount 后启动 DOM 变化监听
  startTreeMutationWatcher()
})

onBeforeUnmount(() => {
  // 清理：observer / 容器引用 / 加载锁
  treeMutationObserver?.disconnect()
  treeMutationObserver = null
  // autoLoadObservers 是 WeakMap，元素 GC 时 observer 自动断开，不用手动清理
  loadingSet.clear()
})

function close() {
  emit('update:visible', false)
}

// visible 关闭时清掉树状态：下次打开重新拉根（避免用户长时间挂着页面、设备列表已过期）
watch(() => props.visible, (v) => {
  if (!v) {
    deviceStates.clear()
  }
})
</script>

<template>
  <!-- 抽屉式：从左侧滑入，固定宽 320px，浮在地图之上 -->
  <Transition name="slide-left">
    <div v-if="visible" class="device-tree-panel">
      <div class="panel-header">
        <span class="title">设备和通道</span>
        <button class="close-btn" title="收起" @click="close">
          <el-icon><Fold /></el-icon>
        </button>
      </div>
      <div class="panel-tip">提示：单设备通道过多时，展开后按 50 条/批加载</div>
      <div ref="treeContainerRef" class="panel-tree">
        <el-tree
          ref="treeRef"
          :load="loadNode"
          :props="treeProps"
          lazy
          node-key="id"
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <span v-if="data.type === 'device'" class="node-row">
              <el-icon class="node-icon"><Monitor /></el-icon>
              <span class="node-label">{{ data.label }}</span>
            </span>
            <!-- 通道：@click.stop 直接 emit，绕开 el-tree 的 @node-click（对 isLeaf:true 不可靠，
                 跟之前"加载更多"同样的问题）；文字和图标按 status 着色（绿=在线/中红=离线/亮红=未注册） -->
            <span
              v-else-if="data.type === 'channel'"
              class="node-row"
              :class="`node-channel-${(data.data?.status || 'OFF').toLowerCase()}`"
              @click.stop="handleChannelClick(data.data, data.id)"
            >
              <el-icon class="node-icon"><VideoCamera /></el-icon>
              <span class="node-label">{{ data.label }}</span>
            </span>
            <span
              v-else-if="data.type === 'loadMore'"
              class="node-row node-loadmore"
              :data-device-id="data.deviceId"
              :data-total="data.total"
              @click.stop="loadMoreChannels(data.deviceId, data)"
            >
              <el-icon class="node-icon"><Expand /></el-icon>
              <span class="node-label">加载更多 ({{ data.loaded }}/{{ data.total }})</span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* 抽屉：固定宽度 320px，从左侧 0 开始 */
.device-tree-panel {
  position: absolute;
  top: 0;
  left: 0;
  width: 320px;
  height: 100%;
  background: rgba(10, 25, 41, 0.96);
  border-right: 1px solid rgba(0, 188, 212, 0.3);
  box-shadow: 4px 0 16px rgba(0, 0, 0, 0.5), 0 0 12px rgba(0, 188, 212, 0.08);
  z-index: 1000;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 188, 212, 0.2);
}
.panel-header .title {
  color: #E0F7FA;
  font-size: 14px;
  font-weight: 600;
}
.close-btn {
  background: transparent;
  border: 1px solid rgba(0, 188, 212, 0.3);
  border-radius: 4px;
  color: #80DEEA;
  cursor: pointer;
  padding: 4px 8px;
  display: inline-flex;
  align-items: center;
  transition: all 0.2s;
}
.close-btn:hover {
  background: rgba(0, 188, 212, 0.15);
  color: #00E5FF;
}

.panel-tip {
  padding: 6px 16px;
  font-size: 11px;
  color: #80DEEA;
  background: rgba(0, 188, 212, 0.08);
  border-bottom: 1px solid rgba(0, 188, 212, 0.15);
}

.panel-tree {
  flex: 1;
  overflow: auto;
  padding: 8px 4px;
  background: transparent;
}

/* 自定义 el-tree 暗色样式（必须 unscoped，因为 el-tree 内部是全局类）
   节点保持透明：hover / current 都不加背景，完全用左侧面板底色做层级 */
:deep(.el-tree) {
  background: transparent;
  color: #E0F7FA;
}
:deep(.el-tree-node__content) {
  background: transparent !important;
}
:deep(.el-tree-node__content):hover {
  background: transparent !important;
}
/* 选中态：仅文字色变电光蓝，不加背景——最不突兀的反馈
   整个 .node-row（含图标和文字）都跟着变，避免点击后图标还是"状态色"看着分裂 */
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: transparent !important;
}
:deep(.el-tree-node.is-current > .el-tree-node__content) .node-row,
:deep(.el-tree-node.is-current > .el-tree-node__content) .node-label {
  color: #00E5FF !important;
}
:deep(.el-tree-node__expand-icon) {
  color: #80DEEA;
}

.node-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #E0F7FA;
}
.node-icon {
  font-size: 14px;
  color: #80DEEA;
}
/* 通道状态色：只染图标（VideoCamera 那个），文字保持冷白 #E0F7FA 不动
   理由：几百条通道时全染字太"花"，眼睛扫不动；只图标着色 → 形成天然的"状态列"
   跟资源管理器那种"文件类型图标"一个套路 */
.node-channel-on .node-icon {
  color: #00E676;        /* 在线：翠绿 */
}
.node-channel-off .node-icon {
  color: #FF5252;        /* 离线：亮红（连不上，是通讯故障） */
}
.node-channel-unregistered .node-icon {
  color: #FFC107;        /* 未注册：琥珀（状态缺失，不是故障） */
}
.node-loadmore {
  color: #00E5FF;
  cursor: pointer;
}
.node-loadmore:hover {
  color: #80DEEA;
}
.node-loadmore .node-icon {
  color: #00E5FF;
}

/* 抽屉滑入动画 */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.3s ease;
}
.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(-100%);
}
</style>
