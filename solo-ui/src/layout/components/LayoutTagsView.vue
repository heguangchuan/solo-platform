<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsStore, type TagItem } from '@/stores/tags'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()
const appStore = useAppStore()

const scrollRef = ref<HTMLElement>()
const tagRefs = ref<Record<string, HTMLElement>>({})

/* ============ 右键菜单 ============ */
const ctxVisible = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)
const ctxTag = ref<TagItem | null>(null)

/** 右键菜单尺寸（与样式保持一致），用于视口边缘防溢出 */
const CTX_MENU_WIDTH = 140
const CTX_MENU_HEIGHT = 220

function openContext(e: MouseEvent, tag: TagItem) {
  e.preventDefault()
  ctxTag.value = tag
  // 靠右/靠下点击时把菜单收回视口内，避免被裁掉
  ctxX.value = Math.min(e.clientX, window.innerWidth - CTX_MENU_WIDTH - 8)
  ctxY.value = Math.min(e.clientY, window.innerHeight - CTX_MENU_HEIGHT - 8)
  ctxVisible.value = true
}

function closeContext() {
  ctxVisible.value = false
  ctxTag.value = null
}

onMounted(() => {
  // 任意点击 / 滚动 / 切路由都关闭右键菜单
  document.addEventListener('click', closeContext)
  document.addEventListener('scroll', closeContext, true)
})

/* ============ 路由变化时同步 ============ */
watch(
  () => route.fullPath,
  () => {
    // route.matched 里最后一项才是真正命中的 view
    tagsStore.open(route)
    nextTick(scrollToActive)
  },
  { immediate: true },
)

function scrollToActive() {
  const el = tagRefs.value[tagsStore.activeTag]
  const wrap = scrollRef.value
  if (!el || !wrap) return
  const { offsetLeft, clientWidth } = el
  const wrapWidth = wrap.clientWidth
  const target = offsetLeft - wrapWidth / 2 + clientWidth / 2
  wrap.scrollTo({ left: Math.max(target, 0), behavior: 'smooth' })
}

/* ============ 鼠标滚轮横向滚动 ============ */
function handleWheel(e: WheelEvent) {
  if (!scrollRef.value) return
  // 横向滚轮（笔记本触摸板）已经是 deltaX；鼠标只有 deltaY，转横向
  if (e.deltaY !== 0) {
    e.preventDefault()
    scrollRef.value.scrollLeft += e.deltaY
  }
}

/* ============ 操作 ============ */
function clickTag(tag: TagItem) {
  if (route.fullPath === tag.fullPath) return
  router.push(tag.fullPath)
}

function closeTag(tag: TagItem, e?: MouseEvent) {
  e?.stopPropagation()
  const result = tagsStore.close(tag.fullPath)
  if (result.closedActive && result.next) {
    router.push(result.next.fullPath)
  }
}

function refresh() {
  if (!ctxTag.value) return
  // 通过 fullPath 触发 router 重新进入；keep-alive 缓存的页面也会重新渲染
  const target = ctxTag.value.fullPath
  closeContext()
  // 先跳到一个占位页，再跳回，强制 unmount 当前组件
  router.replace({ path: '/redirect', query: { to: target } })
}

function closeOthers() {
  if (!ctxTag.value) return
  tagsStore.closeOthers(ctxTag.value.fullPath)
  if (route.fullPath !== ctxTag.value.fullPath) {
    router.push(ctxTag.value.fullPath)
  }
  closeContext()
}

function closeLeft() {
  if (!ctxTag.value) return
  const removed = tagsStore.closeLeft(ctxTag.value.fullPath)
  if (removed.some((t) => t.fullPath === route.fullPath)) {
    router.push(ctxTag.value.fullPath)
  }
  closeContext()
}

function closeRight() {
  if (!ctxTag.value) return
  const removed = tagsStore.closeRight(ctxTag.value.fullPath)
  if (removed.some((t) => t.fullPath === route.fullPath)) {
    router.push(ctxTag.value.fullPath)
  }
  closeContext()
}

function closeAll() {
  tagsStore.closeAll()
  if (route.fullPath !== '/dashboard') router.push('/dashboard')
  closeContext()
}

/* ============ 上下文菜单可用性 ============ */
const ctxIndex = computed(() =>
  ctxTag.value ? tagsStore.tags.findIndex((t) => t.fullPath === ctxTag.value!.fullPath) : -1,
)
const canCloseLeft = computed(() => {
  if (ctxIndex.value < 0) return false
  return tagsStore.tags.slice(0, ctxIndex.value).some((t) => !t.affix)
})
const canCloseRight = computed(() => {
  if (ctxIndex.value < 0) return false
  return tagsStore.tags.slice(ctxIndex.value + 1).some((t) => !t.affix)
})

function setTagRef(el: HTMLElement | null, key: string) {
  if (el) tagRefs.value[key] = el
}
</script>

<template>
  <div class="tags-view" :class="{ 'is-dark': appStore.isDark }">
    <div ref="scrollRef" class="tags-scroll" @wheel="handleWheel">
      <div
        v-for="tag in tagsStore.tags"
        :key="tag.fullPath"
        :ref="(el) => setTagRef(el as HTMLElement, tag.fullPath)"
        class="tag-item"
        :class="{ active: tagsStore.activeTag === tag.fullPath }"
        @click="clickTag(tag)"
        @contextmenu="openContext($event, tag)"
        @click.middle="!tag.affix && closeTag(tag)"
      >
        <span v-if="tagsStore.activeTag === tag.fullPath" class="tag-dot"></span>
        <span class="tag-title">{{ tag.title }}</span>
        <el-icon v-if="!tag.affix" class="tag-close" @click="closeTag(tag, $event)">
          <Close />
        </el-icon>
      </div>
    </div>

    <!-- 右键菜单 -->
    <ul
      v-show="ctxVisible"
      class="ctx-menu"
      :style="{ left: ctxX + 'px', top: ctxY + 'px' }"
      @click.stop
    >
      <li @click="refresh">
        <el-icon><Refresh /></el-icon><span>刷新当前</span>
      </li>
      <li
        :class="{ disabled: !ctxTag || ctxTag.affix }"
        @click="!ctxTag?.affix && ctxTag && closeTag(ctxTag) && closeContext()"
      >
        <el-icon><Close /></el-icon><span>关闭当前</span>
      </li>
      <li :class="{ disabled: !canCloseLeft }" @click="canCloseLeft && closeLeft()">
        <el-icon><Back /></el-icon><span>关闭左侧</span>
      </li>
      <li :class="{ disabled: !canCloseRight }" @click="canCloseRight && closeRight()">
        <el-icon><Right /></el-icon><span>关闭右侧</span>
      </li>
      <li @click="closeOthers">
        <el-icon><Switch /></el-icon><span>关闭其他</span>
      </li>
      <li @click="closeAll">
        <el-icon><CircleClose /></el-icon><span>关闭全部</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.tags-view {
  position: relative;
  height: 38px;
  display: flex;
  align-items: center;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  /* Header 与 TagsView 同底色；用细微的内阴影制造一条分隔线，避免"连成一片" */
  box-shadow: inset 0 1px 0 0 var(--el-border-color-lighter);
  padding: 0 8px;
  user-select: none;
}

.tags-view.is-dark {
  border-bottom-color: rgba(255, 255, 255, 0.04);
  box-shadow:
    inset 0 1px 0 0 rgba(255, 255, 255, 0.06),
    0 1px 0 0 rgba(0, 0, 0, 0.4);
}

.tags-scroll {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 4px 0;
  /* 隐藏滚动条但保留滚动 */
  scrollbar-width: none;
}

.tags-scroll::-webkit-scrollbar {
  display: none;
}

.tag-item {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  line-height: 1;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.tag-item:hover {
  color: var(--el-color-primary);
}

.tag-item.active {
  color: #fff;
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  margin-right: 6px;
}

.tag-title {
  white-space: nowrap;
}

.tag-close {
  margin-left: 6px;
  font-size: 12px;
  border-radius: 50%;
  padding: 1px;
  transition: background 0.2s;
}

.tag-item.active .tag-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

.tag-item:not(.active) .tag-close:hover {
  background: var(--el-fill-color-darker);
  color: var(--el-color-danger);
}

/* 右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  list-style: none;
  margin: 0;
  padding: 4px 0;
  min-width: 140px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
  font-size: 13px;
}

.ctx-menu li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.ctx-menu li:hover:not(.disabled) {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.ctx-menu li.disabled {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
}

.ctx-menu li .el-icon {
  font-size: 14px;
}
</style>
