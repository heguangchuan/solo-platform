<template>
  <div ref="rootRef" class="gb-player relative h-full w-full overflow-hidden bg-black">
    <!-- 画面舞台（电子放大用） -->
    <div
      class="stage group relative h-full w-full overflow-hidden"
      :class="{ 'cursor-grab': isZoomed, 'cursor-grabbing': isDragging }"
      @wheel="onWheel"
      @mousedown="onMouseDown"
      @dblclick="onDoubleClick"
    >
      <div
        ref="containerRef"
        class="jessibuca-container absolute inset-0 origin-top-left"
        :style="{ transform: zoomTransform, transition: isDragging ? 'none' : 'transform 80ms ease-out' }"
      />

      <!-- 信息面板 -->
      <div v-if="showInfo" class="absolute right-3 top-3 z-10 rounded-md bg-black/65 px-3 py-2 text-xs text-white/90 backdrop-blur-sm">
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">分辨率</span>
          <span class="font-mono">{{ resolutionText }}</span>
        </div>
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">视频编码</span>
          <span class="font-mono">{{ videoCodecText }}</span>
        </div>
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">帧率</span>
          <span class="font-mono">{{ fpsText }}</span>
        </div>
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">视频码率</span>
          <span class="font-mono">{{ videoBitrateText }}</span>
        </div>
        <div v-if="audioInfo" class="flex items-center justify-between gap-6">
          <span class="text-white/50">音频编码</span>
          <span class="font-mono">{{ audioCodecText }}</span>
        </div>
        <div v-if="audioInfo" class="flex items-center justify-between gap-6">
          <span class="text-white/50">音频码率</span>
          <span class="font-mono">{{ audioBitrateText }}</span>
        </div>
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">缓冲</span>
          <span class="font-mono">{{ bufferText }}</span>
        </div>
        <div class="flex items-center justify-between gap-6">
          <span class="text-white/50">流畅度</span>
          <span class="font-mono">{{ performanceText }}</span>
        </div>
      </div>

      <!-- 录制指示器（红点 + 时长） -->
      <div
        v-if="isRecording"
        class="absolute left-3 top-3 z-10 flex items-center gap-1.5 rounded-md bg-black/65 px-2.5 py-1 text-xs text-white backdrop-blur-sm"
      >
        <span class="animate-pulse font-medium text-red-500">REC</span>
        <span class="font-mono tabular-nums text-white/90">{{ recordTimeText }}</span>
      </div>

      <!-- 放大倍数徽章 -->
      <div
        v-if="isZoomed"
        class="absolute left-3 z-10 rounded-full bg-black/65 px-2.5 py-1 text-xs text-white/80 backdrop-blur-sm"
        :class="isRecording ? 'top-10' : 'top-3'"
      >
        {{ zoomLabel }}
      </div>

      <!-- 工具栏（悬浮在画面底部，鼠标移入显示） -->
      <div
        v-if="showToolbar"
        class="toolbar absolute inset-x-0 bottom-0 z-10 flex h-9 items-center gap-1 bg-gradient-to-t from-black/80 to-transparent px-2 text-white/70 opacity-0 transition-opacity duration-200 group-hover:opacity-100"
      >
      <!-- 全屏 -->
      <template v-if="showFullscreenBtn">
        <button
          title="全屏"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="toggleFullscreen"
        >
          <Maximize class="h-4 w-4" />
        </button>
      </template>

      <!-- 播放控制 -->
      <template v-if="showPlayBtn">
        <button
          :title="isPlaying ? '暂停' : '播放'"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="togglePlay"
        >
          <Pause v-if="isPlaying" class="h-4 w-4" />
          <Play v-else class="h-4 w-4" />
        </button>
      </template>

      <template v-if="showRefreshBtn">
        <button
          title="刷新"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="onRefresh"
        >
          <RotateCw class="h-4 w-4" />
        </button>
      </template>

      <template v-if="showStopBtn">
        <button
          title="停止并恢复默认界面"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="onStop"
        >
          <Square class="h-3.5 w-3.5" />
        </button>
      </template>

      <div
        v-if="(showFullscreenBtn || showPlayBtn || showRefreshBtn || showStopBtn) && (hasStreams || showTransport)"
        class="mx-1 h-4 w-px bg-white/10"
      />

      <!-- 流控制 -->
      <template v-if="hasStreams">
        <div class="relative">
          <button
            title="码流切换"
            class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
            data-menu-trigger
            @click="streamMenuOpen = !streamMenuOpen"
          >
            <Layers class="h-4 w-4" />
          </button>
          <div v-if="streamMenuOpen" class="menu-popover absolute bottom-full left-0 mb-2 min-w-[150px] rounded-md border border-white/10 bg-neutral-900/95 py-1 text-xs shadow-xl backdrop-blur-sm">
            <div
              v-for="s in streams"
              :key="s.value"
              class="flex cursor-pointer items-center justify-between gap-4 whitespace-nowrap rounded px-2.5 py-1 text-white/70 hover:bg-white/10 hover:text-white"
              :class="{ 'bg-white/5 text-white': s.value === currentStream }"
              @click="onSwitchStream(s.value)"
            >
              <span>{{ s.label }}</span>
              <Check v-if="s.value === currentStream" class="h-3.5 w-3.5 text-white" />
            </div>
          </div>
        </div>
      </template>

      <template v-if="showTransport">
        <div class="relative">
          <button
            title="收流模式"
            class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
            data-menu-trigger
            @click="transportMenuOpen = !transportMenuOpen"
          >
            <Wifi class="h-4 w-4" />
          </button>
          <div v-if="transportMenuOpen" class="menu-popover absolute bottom-full left-0 mb-2 min-w-[110px] rounded-md border border-white/10 bg-neutral-900/95 py-1 text-xs shadow-xl backdrop-blur-sm">
            <div
              v-for="t in TRANSPORTS"
              :key="t.value"
              class="flex cursor-pointer items-center justify-between gap-4 whitespace-nowrap rounded px-2.5 py-1 text-white/70 hover:bg-white/10 hover:text-white"
              :class="{ 'bg-white/5 text-white': t.value === transport }"
              @click="onSwitchTransport(t.value)"
            >
              <span>{{ t.label }}</span>
              <Check v-if="t.value === transport" class="h-3.5 w-3.5 text-white" />
            </div>
          </div>
        </div>
      </template>

      <div v-if="(hasStreams || showTransport) && (showZoom || showPtz || showScreenshotBtn || showRecordBtn || showMuteBtn || showInfoBtn)" class="mx-1 h-4 w-px bg-white/10" />

      <!-- 电子放大 -->
      <template v-if="showZoom">
        <button
          title="放大（滚轮）"
          :disabled="zoomScale >= MAX_ZOOM"
          :class="[
            'grid h-7 w-7 place-items-center rounded transition-colors',
            zoomScale >= MAX_ZOOM
              ? 'cursor-not-allowed text-white/25'
              : 'text-white/70 hover:bg-white/10 hover:text-white',
          ]"
          data-menu-trigger
          @click="zoomIn"
        >
          <ZoomIn class="h-4 w-4" />
        </button>
        <button
          title="缩小"
          :disabled="zoomScale <= MIN_ZOOM"
          :class="[
            'grid h-7 w-7 place-items-center rounded transition-colors',
            zoomScale <= MIN_ZOOM
              ? 'cursor-not-allowed text-white/25'
              : 'text-white/70 hover:bg-white/10 hover:text-white',
          ]"
          data-menu-trigger
          @click="zoomOut"
        >
          <ZoomOut class="h-4 w-4" />
        </button>
        <button
          title="复位（双击）"
          :disabled="!isZoomed"
          :class="[
            'grid h-7 w-7 place-items-center rounded transition-colors',
            !isZoomed
              ? 'cursor-not-allowed text-white/25'
              : 'text-white/70 hover:bg-white/10 hover:text-white',
          ]"
          data-menu-trigger
          @click="resetZoom"
        >
          <Focus class="h-4 w-4" />
        </button>
      </template>

      <div v-if="showZoom && showPtz" class="mx-1 h-4 w-px bg-white/10" />

      <!-- 云台 / 预置位：两面板并排展开、相邻不重叠，可同时打开 -->
      <template v-if="showPtz">
        <div class="relative flex items-center">
        <!-- 云台控制 -->
        <button
          title="云台控制"
          class="grid h-7 w-7 shrink-0 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="togglePtzMenu"
        >
          <Move class="h-4 w-4" />
        </button>
          <div v-if="ptzMenuOpen" class="menu-popover absolute bottom-full left-0 mb-2 w-[260px] rounded-md border border-white/10 bg-neutral-900/95 p-3 text-sm shadow-xl backdrop-blur-sm">
            <!-- Tabs -->
            <div class="mb-2 flex gap-1 border-b border-white/10 pb-2">
              <div
                v-for="tab in PTZ_TABS"
                :key="tab.value"
                class="flex-1 cursor-pointer rounded px-2 py-1 text-center text-sm"
                :class="ptzActiveTab === tab.value ? 'bg-white/20 text-white' : 'text-white/50 hover:text-white/80'"
                @click="ptzActiveTab = tab.value"
              >
                {{ tab.label }}
              </div>
            </div>

            <!-- 方向 -->
            <div v-show="ptzActiveTab === 'direction'" class="flex flex-col items-center gap-2">
              <div class="grid grid-cols-3 gap-1">
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('up-left')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↖</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('up')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↑</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('up-right')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↗</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('left')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >←</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/15 text-sm text-red-400 transition-colors hover:bg-red-500/20"
                  @click="ptzStop"
                >●</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('right')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >→</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('down-left')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↙</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('down')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↓</button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('down-right')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >↘</button>
              </div>
              <div class="flex gap-1">
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('zoom-in')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >
                  <ZoomIn class="h-4 w-4" />
                </button>
                <button
                  class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                  @mousedown.prevent="ptzStart('zoom-out')"
                  @mouseup="ptzStop"
                  @mouseleave="ptzStop"
                >
                  <ZoomOut class="h-4 w-4" />
                </button>
              </div>
            </div>

            <!-- 镜头 -->
            <div v-show="ptzActiveTab === 'lens'" class="space-y-2 pt-0.5">
              <div class="flex items-center justify-between">
                <span class="w-16 text-xs text-white/50">聚焦</span>
                <div class="flex gap-1">
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                    @mousedown.prevent="focusStart('near')"
                    @mouseup="focusStop"
                    @mouseleave="focusStop"
                  >近</button>
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/15 text-sm text-red-400 transition-colors hover:bg-red-500/20"
                    @click="focusStop"
                  >停</button>
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                    @mousedown.prevent="focusStart('far')"
                    @mouseup="focusStop"
                    @mouseleave="focusStop"
                  >远</button>
                </div>
              </div>
              <div class="flex items-center justify-between">
                <span class="w-16 text-xs text-white/50">光圈</span>
                <div class="flex gap-1">
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                    @mousedown.prevent="irisStart('open')"
                    @mouseup="irisStop"
                    @mouseleave="irisStop"
                  >开</button>
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/15 text-sm text-red-400 transition-colors hover:bg-red-500/20"
                    @click="irisStop"
                  >停</button>
                  <button
                    class="grid h-8 w-8 select-none place-items-center rounded bg-white/10 text-sm text-white/80 transition-colors hover:bg-white/20 hover:text-white active:bg-white/30"
                    @mousedown.prevent="irisStart('close')"
                    @mouseup="irisStop"
                    @mouseleave="irisStop"
                  >关</button>
                </div>
              </div>
              <div class="flex items-center justify-between">
                <span class="w-16 text-xs text-white/50">雨刷</span>
                <button
                  class="relative h-4 w-8 rounded-full transition-colors"
                  :class="wiperOn ? 'bg-white/40' : 'bg-white/20'"
                  @click="toggleWiper"
                >
                  <span
                    class="absolute top-0.5 h-3 w-3 rounded-full bg-white transition-transform"
                    :class="wiperOn ? 'translate-x-4' : 'translate-x-0.5'"
                  />
                </button>
              </div>
            </div>

            <!-- 速度 -->
            <div v-show="ptzActiveTab === 'speed'" class="space-y-2 pt-0.5">
              <div v-for="row in speedRows" :key="row.key" class="flex items-center gap-2">
                <span class="w-16 shrink-0 text-xs text-white/50">{{ row.label }}</span>
                <input
                  type="range"
                  :min="row.min"
                  :max="row.max"
                  :value="(ptzSpeeds as Record<string, number>)[row.key]"
                  class="h-1.5 flex-1 cursor-pointer appearance-none rounded-full bg-white/15 [accent-color:rgba(255,255,255,0.7)]"
                  @input="onSpeedInput(row.key, ($event.target as HTMLInputElement).value)"
                />
                <span class="w-8 text-right font-mono text-xs text-white/70">{{ (ptzSpeeds as Record<string, number>)[row.key] }}</span>
              </div>
            </div>

          </div>

        <!-- 预置位（独立按钮，与云台同组） -->
        <button
          v-if="showPresets"
          title="预置位"
          class="grid h-7 w-7 shrink-0 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="togglePresetMenu"
        >
          <Bookmark class="h-4 w-4" />
        </button>

        <!-- 巡航（独立按钮，与云台/预置位同组） -->
        <button
          v-if="showCruises"
          title="巡航"
          class="grid h-7 w-7 shrink-0 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="toggleCruiseMenu"
        >
          <Route class="h-4 w-4" />
        </button>

          <div
            v-if="presetMenuOpen && showPresets"
            class="menu-popover absolute bottom-full mb-2 w-[260px] rounded-md border border-white/10 bg-neutral-900/95 p-3 text-sm shadow-xl backdrop-blur-sm"
            :class="ptzMenuOpen ? 'left-[260px]' : 'left-0'"
          >
            <ul class="flex max-h-60 flex-col gap-0.5 overflow-y-auto pr-0.5">
              <li
                v-for="slot in presetSlots"
                :key="slot.presetId"
                class="group flex items-center gap-2 rounded px-1.5 py-1 transition-colors hover:bg-white/10"
              >
                <span
                  class="grid h-5 w-5 shrink-0 place-items-center rounded font-mono text-[10px]"
                  :class="slot.preset ? 'bg-white/10 text-white/70' : 'bg-white/5 text-white/30'"
                >
                  {{ slot.presetId }}
                </span>

                <span
                  class="min-w-0 flex-1 truncate text-xs"
                  :class="slot.preset ? 'text-white/80' : 'text-white/30'"
                >
                  {{ slot.preset ? slot.preset.name : `预置点${slot.presetId}` }}
                </span>

                <div class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    title="设置"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-white/10 hover:text-white"
                    :disabled="presetBusyId === `s${slot.presetId}`"
                    @click="onSetPreset(slot.presetId)"
                  >
                    <BookmarkPlus class="h-3.5 w-3.5" />
                  </button>
                  <button
                    title="调用"
                    :disabled="!slot.preset || presetBusyId === `c${slot.presetId}`"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-white/10 hover:text-white disabled:pointer-events-none disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-white/60"
                    @click="slot.preset && onCallPreset(slot.preset.id, slot.presetId)"
                  >
                    <Crosshair class="h-3.5 w-3.5" />
                  </button>
                  <button
                    title="删除"
                    :disabled="!slot.preset || presetBusyId === `d${slot.presetId}`"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-red-500/20 hover:text-red-400 disabled:pointer-events-none disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-white/60"
                    @click="slot.preset && onDeletePreset(slot.preset.id, slot.presetId)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </li>
            </ul>
          </div>

          <!-- 巡航面板 -->
          <div
            v-if="cruiseMenuOpen && showCruises"
            class="menu-popover absolute bottom-full mb-2 max-h-[72vh] w-[260px] overflow-y-auto rounded-md border border-white/10 bg-neutral-900/95 p-3 text-sm shadow-xl backdrop-blur-sm"
            :style="{ left: `${cruisePanelOffset}px` }"
          >
            <!-- 头部 -->
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-medium text-white/80">巡航</span>
              <button
                title="新建路线"
                class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-white/10 hover:text-white"
                @click="startNewCruise"
              >
                <Plus class="h-3.5 w-3.5" />
              </button>
            </div>

            <!-- 新建路线表单 -->
            <div v-if="newCruise" class="mb-2 space-y-2 rounded border border-white/10 bg-white/5 p-2">
              <input
                v-model="newCruise.name"
                placeholder="路线名称"
                class="h-7 w-full rounded border border-white/10 bg-black/40 px-2 text-xs text-white/90 outline-none placeholder:text-white/30 focus:border-white/30"
                @keydown.enter="confirmNewCruise"
                @keydown.esc="newCruise = null"
              />
              <div class="flex items-center gap-2">
                <label class="flex flex-1 items-center gap-1 text-[10px] text-white/40">
                  速度
                  <input
                    v-model.number="newCruise.speed"
                    type="number"
                    min="1"
                    max="255"
                    class="h-6 w-full rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none focus:border-white/30"
                  />
                </label>
                <label class="flex flex-1 items-center gap-1 text-[10px] text-white/40">
                  停留
                  <input
                    v-model.number="newCruise.staySeconds"
                    type="number"
                    min="1"
                    max="255"
                    class="h-6 w-full rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none focus:border-white/30"
                  />
                </label>
              </div>

              <!-- 勾选预置位（按勾选顺序巡航） -->
              <div class="space-y-1">
                <span class="text-[10px] text-white/40">选择预置位（至少 2 个）</span>
                <ul class="flex max-h-28 flex-col gap-0.5 overflow-y-auto rounded border border-white/10 bg-black/30 p-1">
                  <li
                    v-for="preset in presets"
                    :key="preset.id"
                    class="flex cursor-pointer items-center gap-1.5 rounded px-1 py-0.5 text-[11px] text-white/70 hover:bg-white/10"
                    @click="toggleNewCruisePreset(preset.presetId)"
                  >
                    <span
                      class="grid h-3.5 w-3.5 place-items-center rounded-[3px] border border-white/25"
                      :class="{ 'border-white/60 bg-white/25 text-white': newCruise.points.includes(preset.presetId) }"
                    >
                      <Check v-if="newCruise.points.includes(preset.presetId)" class="h-2.5 w-2.5" />
                    </span>
                    <span class="font-mono text-[10px] text-white/40">{{ preset.presetId }}</span>
                    <span class="min-w-0 flex-1 truncate">{{ preset.name }}</span>
                  </li>
                  <li v-if="!presets.length" class="px-1 py-2 text-center text-[10px] text-white/30">
                    请先设置预置位
                  </li>
                </ul>
              </div>

              <div class="flex justify-end gap-1">
                <button
                  class="rounded px-2 py-0.5 text-[11px] text-white/50 hover:bg-white/10 hover:text-white"
                  @click="newCruise = null"
                >
                  取消
                </button>
                <button
                  class="rounded bg-white/15 px-2 py-0.5 text-[11px] text-white hover:bg-white/25 disabled:pointer-events-none disabled:opacity-30"
                  :disabled="newCruise.points.length < 2"
                  @click="confirmNewCruise"
                >
                  创建
                </button>
              </div>
            </div>

            <!-- 路线列表 -->
            <ul class="flex max-h-32 flex-col gap-0.5 overflow-y-auto pr-0.5">
              <li
                v-for="c in cruises"
                :key="c.id"
                class="group flex cursor-pointer items-center gap-2 rounded px-1.5 py-1 transition-colors hover:bg-white/10"
                :class="{ 'bg-white/10': selectedCruiseId === c.id }"
                @click="selectCruise(c.id)"
              >
                <span class="grid h-5 w-5 shrink-0 place-items-center rounded bg-white/10 font-mono text-[10px] text-white/70">
                  {{ c.cruiseId }}
                </span>
                <span
                  class="min-w-0 flex-1 truncate text-xs"
                  :class="runningCruiseId === c.id ? 'animate-pulse text-emerald-400' : 'text-white/80'"
                >
                  {{ c.name }}
                </span>
                <div class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                  <button
                    v-if="runningCruiseId === c.id"
                    title="停止"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-red-500/20 hover:text-red-400"
                    :disabled="cruiseBusy"
                    @click.stop="onStopCruise(c.id)"
                  >
                    <Square class="h-3.5 w-3.5" />
                  </button>
                  <button
                    v-else
                    title="开始"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-emerald-500/20 hover:text-emerald-400"
                    :disabled="cruiseBusy"
                    @click.stop="onStartCruise(c.id)"
                  >
                    <Play class="h-3.5 w-3.5" />
                  </button>
                  <button
                    title="删除"
                    class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-red-500/20 hover:text-red-400"
                    :disabled="cruiseBusy"
                    @click.stop="onDeleteCruise(c.id)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </li>
              <li v-if="!cruises.length" class="px-1.5 py-3 text-center text-[11px] text-white/30">
                暂无巡航路线
              </li>
            </ul>

            <!-- 选中路线详情 -->
            <template v-if="selectedCruise">
              <div class="my-2 h-px bg-white/10" />
              <div class="mb-2 flex items-center gap-2">
                <label class="flex flex-1 items-center gap-1 text-[10px] text-white/40">
                  速度
                  <input
                    :value="selectedCruise.speed"
                    type="number"
                    min="1"
                    max="255"
                    class="h-6 w-full rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none focus:border-white/30"
                    @change="onCruiseFieldChange('speed', ($event.target as HTMLInputElement).value)"
                  />
                </label>
                <label class="flex flex-1 items-center gap-1 text-[10px] text-white/40">
                  停留
                  <input
                    :value="selectedCruise.staySeconds"
                    type="number"
                    min="1"
                    max="255"
                    class="h-6 w-full rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none focus:border-white/30"
                    @change="onCruiseFieldChange('staySeconds', ($event.target as HTMLInputElement).value)"
                  />
                </label>
              </div>

              <!-- 巡航点 -->
              <div class="mb-1.5 flex items-center justify-between">
                <span class="text-[11px] text-white/50">巡航点（{{ cruisePoints.length }}）</span>
                <button
                  v-if="addPointPresetId === ''"
                  title="添加巡航点"
                  class="grid h-6 w-6 place-items-center rounded text-white/60 hover:bg-white/10 hover:text-white"
                  :disabled="!availablePresets.length"
                  @click="addPointPresetId = availablePresets[0] ? String(availablePresets[0].presetId) : ''"
                >
                  <Plus class="h-3.5 w-3.5" />
                </button>
              </div>

              <ul class="flex max-h-32 flex-col gap-0.5 overflow-y-auto pr-0.5">
                <li
                  v-for="p in cruisePoints"
                  :key="p.id"
                  class="group flex items-center gap-2 rounded px-1.5 py-1 transition-colors hover:bg-white/10"
                >
                  <span class="w-4 shrink-0 text-right font-mono text-[10px] text-white/40">{{ p.sortOrder + 1 }}</span>
                  <span class="grid h-5 w-5 shrink-0 place-items-center rounded bg-white/10 font-mono text-[10px] text-white/70">
                    {{ p.presetId }}
                  </span>
                  <span class="min-w-0 flex-1 truncate text-xs text-white/80">{{ p.presetName || `预置点${p.presetId}` }}</span>
                  <span
                    class="shrink-0 rounded bg-white/5 px-1 py-px text-[9px] text-white/40"
                    :title="p.staySeconds ? '单点停留' : '默认停留'"
                  >
                    {{ p.staySeconds ?? selectedCruise.staySeconds }}s
                  </span>
                  <button
                    title="移除"
                    class="grid h-6 w-6 shrink-0 place-items-center rounded text-white/60 opacity-0 hover:bg-red-500/20 hover:text-red-400 group-hover:opacity-100"
                    :disabled="cruiseBusy"
                    @click="onRemovePoint(p.presetId)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </li>
                <li v-if="!cruisePoints.length" class="px-1.5 py-2 text-center text-[11px] text-white/30">
                  暂无巡航点
                </li>
              </ul>

              <!-- 添加巡航点行内选择 -->
              <div v-if="addPointPresetId !== ''" class="mt-1.5 space-y-1">
                <div class="flex items-center gap-1">
                  <select
                    v-model="addPointPresetId"
                    class="h-7 flex-1 rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none focus:border-white/30"
                  >
                    <option
                      v-for="preset in availablePresets"
                      :key="preset.presetId"
                      :value="String(preset.presetId)"
                      class="bg-neutral-900"
                    >
                      {{ preset.name }}
                    </option>
                  </select>
                  <input
                    v-model.number="addPointStay"
                    type="number"
                    min="1"
                    max="15"
                    placeholder="停留s"
                    title="单点停留秒数（1~15），留空用路线默认"
                    class="h-7 w-14 rounded border border-white/10 bg-black/40 px-1.5 text-xs text-white/90 outline-none placeholder:text-white/25 focus:border-white/30"
                  />
                </div>
                <div class="flex justify-end gap-1">
                  <button
                    class="rounded bg-white/15 px-2 py-0.5 text-[11px] text-white hover:bg-white/25"
                    @click="onAddPoint"
                  >
                    添加
                  </button>
                  <button
                    class="rounded px-1.5 py-1 text-[11px] text-white/50 hover:bg-white/10 hover:text-white"
                    @click="addPointPresetId = ''"
                  >
                    取消
                  </button>
                </div>
              </div>
            </template>
          </div>
        </div>
        <div v-if="showScreenshotBtn || showRecordBtn || showMuteBtn || showInfoBtn" class="mx-1 h-4 w-px bg-white/10" />
      </template>

      <!-- 媒体输出 -->
      <template v-if="showScreenshotBtn">
        <button
          title="截图"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="takeScreenshot"
        >
          <Camera class="h-4 w-4" />
        </button>
      </template>

      <template v-if="showRecordBtn">
        <button
          :title="isRecording ? '停止录制' : '开始录制'"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="toggleRecord"
        >
          <CircleStop v-if="isRecording" class="h-4 w-4" />
          <Video v-else class="h-4 w-4" />
        </button>
      </template>

      <template v-if="showMuteBtn">
        <button
          :title="isMuted ? '取消静音' : '静音'"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="toggleMute"
        >
          <VolumeX v-if="isMuted" class="h-4 w-4" />
          <Volume2 v-else class="h-4 w-4" />
        </button>
      </template>

      <template v-if="showInfoBtn">
        <button
          title="码流信息"
          class="grid h-7 w-7 place-items-center rounded text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          data-menu-trigger
          @click="showInfo = !showInfo"
        >
          <Info class="h-4 w-4" />
        </button>
      </template>

      <!-- 业务扩展按钮（由父组件通过 toolbar-extra 插槽注入） -->
      <template v-if="$slots['toolbar-extra']">
        <div class="mx-1 h-4 w-px bg-white/10" />
        <slot name="toolbar-extra" />
      </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 国标视频播放器（Jessibuca 封装 + 工具栏）。
 *
 * 工具栏可通过 props 精细控制每个按钮的显隐：
 * - showToolbar: 总开关
 * - showPlayBtn / showMuteBtn / showScreenshotBtn / showFullscreenBtn / showInfoBtn / showRefreshBtn
 * - showZoom: 电子放大（滚轮/拖拽/双击复位）
 * - streams: 码流档位数组，>=2 档时显示码流切换
 * - showTransport: 收流模式切换（UDP/TCP 被动/TCP 主动）
 * - showPtz: 云台控制面板（方向/镜头/速度三 tab），showPresets 追加独立预置位按钮
 *
 * 业务相关操作（码流切换、收流切换、刷新、PTZ）通过 emit 抛出，由父组件处理。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  ZoomIn,
  ZoomOut,
  Focus,
  Camera,
  Check,
  Video,
  CircleStop,
  Maximize,
  RotateCw,
  Info,
  Layers,
  Wifi,
  Move,
  Bookmark,
  BookmarkPlus,
  Crosshair,
  Trash2,
  Route,
  Plus,
  Square,
} from 'lucide-vue-next'
import type {
  GbAudioInfo,
  GbPlayerStats,
  GbPtzCruise,
  GbPtzCruisePoint,
  GbPtzPreset,
  GbVideoInfo,
} from '@/types/api/gb28181'

/** Jessibuca 实例的最小结构（运行时来自 /static 脚本） */
interface JessibucaInstance {
  play: (url?: string) => Promise<unknown>
  pause: () => Promise<void>
  hasLoaded: () => boolean
  clearView: () => void
  destroy: () => Promise<unknown> | void
  screenshot: (filename?: string, format?: string, quality?: number, type?: string) => void
  setFullscreen: (flag: boolean) => void
  cancelMute: () => void
  mute: () => void
  startRecord: (fileName?: string, type?: string, quality?: number) => void
  stopRecordAndSave: () => void
  on: (event: string, cb: (...args: unknown[]) => void) => void
}
interface JessibucaConstructor {
  new (config: Record<string, unknown>): JessibucaInstance
}

declare global {
  interface Window {
    Jessibuca?: JessibucaConstructor
  }
}

/* ==================== Props & Emits ==================== */

const props = withDefaults(
  defineProps<{
    /** 播放地址，为空不播 */
    src?: string
    /** 低延迟缓冲秒数 */
    videoBuffer?: number
    /** 是否解析音频 */
    hasAudio?: boolean

    /** 工具栏总开关 */
    showToolbar?: boolean
    showPlayBtn?: boolean
    showMuteBtn?: boolean
    showScreenshotBtn?: boolean
    showRecordBtn?: boolean
    showFullscreenBtn?: boolean
    showInfoBtn?: boolean
    showRefreshBtn?: boolean
    showStopBtn?: boolean

    /** 电子放大 */
    showZoom?: boolean

    /** 码流档位，>=2 档时显示切换按钮 */
    streams?: Array<{ label: string; value: string }>
    /** 当前码流档位 value */
    currentStream?: string

    /** 收流模式切换 */
    showTransport?: boolean
    /** 当前收流模式 */
    transport?: 'UDP' | 'TCP-passive' | 'TCP-active'

    /** 云台控制面板 */
    showPtz?: boolean

    /** 预置位列表（由父组件拉取后传入） */
    presets?: GbPtzPreset[]
    /** 是否显示预置位按钮（与云台同组，仅 showPtz 时有意义） */
    showPresets?: boolean

    /** 巡航路线列表（由父组件拉取后传入） */
    cruises?: GbPtzCruise[]
    /** 选中路线的巡航点（由父组件拉取后传入） */
    cruisePoints?: GbPtzCruisePoint[]
    /** 是否显示巡航按钮（与云台/预置位同组） */
    showCruises?: boolean
  }>(),
  {
    videoBuffer: 0.2,
    hasAudio: true,
    showToolbar: true,
    showPlayBtn: true,
    showMuteBtn: true,
    showScreenshotBtn: true,
    showRecordBtn: false,
    showFullscreenBtn: true,
    showInfoBtn: true,
    showRefreshBtn: false,
    showStopBtn: false,
    showZoom: true,
    streams: () => [],
    currentStream: '',
    showTransport: false,
    transport: 'UDP',
    showPtz: false,
    presets: () => [],
    showPresets: true,
    cruises: () => [],
    cruisePoints: () => [],
    showCruises: true,
  },
)

const emit = defineEmits<{
  playing: []
  pause: []
  error: [message: string]
  streamEnd: []
  timeout: []
  videoInfo: [info: GbVideoInfo]
  audioInfo: [info: GbAudioInfo]
  stats: [stats: GbPlayerStats]
  performance: [level: number]
  fullscreen: [flag: boolean]
  mute: [flag: boolean]
  'switch-stream': [value: string]
  'switch-transport': [t: 'UDP' | 'TCP-passive' | 'TCP-active']
  refresh: []
  stop: []
  ptz: [direction: string, pressing: boolean]
  focus: [direction: 'near' | 'far' | 'stop', speed: number]
  iris: [direction: 'open' | 'close' | 'stop', speed: number]
  wiper: [action: 'on' | 'off']
  'speed-change': [speeds: { move: number; zoom: number; focus: number; iris: number }]
  /** 将当前视角设置到指定编号槽位（名称由后端生成） */
  'preset-save': [presetId: number]
  /** 删除指定编号槽位的预置位 */
  'preset-delete': [id: string, presetId: number]
  /** 调用预置位（云台转到该视角） */
  'preset-call': [id: string]
  /** 新建巡航路线（含初始巡航点） */
  'cruise-save': [
    data: {
      name: string
      speed: number
      staySeconds: number
      points: Array<{ presetId: number; staySeconds?: number | null }>
    },
  ]
  /** 更新巡航路线基本信息 */
  'cruise-update': [id: string, data: Partial<GbPtzCruise>]
  /** 删除巡航路线 */
  'cruise-delete': [id: string]
  /** 添加巡航点 */
  'cruise-add-point': [cruiseId: string, presetId: number, staySeconds?: number | null]
  /** 移除巡航点 */
  'cruise-remove-point': [cruiseId: string, presetId: number]
  /** 开始巡航 */
  'cruise-start': [id: string]
  /** 停止巡航 */
  'cruise-stop': [id: string]
  /** 选中路线变化（父组件据此拉取巡航点） */
  'cruise-select': [id: string]
  'record-start': []
  'record-stop': []
}>()

/* ==================== 常量 ==================== */

const DECODER_JS = '/static/js/jessibuca/decoder.js'
const MIN_ZOOM = 1
const MAX_ZOOM = 8
const ZOOM_STEP = 0.25

const TRANSPORTS: Array<{ label: string; value: 'UDP' | 'TCP-passive' | 'TCP-active' }> = [
  { label: 'UDP', value: 'UDP' },
  { label: 'TCP 被动', value: 'TCP-passive' },
  { label: 'TCP 主动', value: 'TCP-active' },
]

const PTZ_TABS = [
  { label: '方向', value: 'direction' as const },
  { label: '镜头', value: 'lens' as const },
  { label: '速度', value: 'speed' as const },
]

const speedRows = [
  { key: 'move', label: '移动速度', min: 1, max: 255 },
  { key: 'zoom', label: '变倍速度', min: 0, max: 15 },
  { key: 'focus', label: '聚焦速度', min: 0, max: 15 },
  { key: 'iris', label: '光圈速度', min: 0, max: 15 },
]

/* ==================== 播放器核心 ==================== */

const rootRef = ref<HTMLDivElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)
let player: JessibucaInstance | null = null
let pendingUrl: string | null = null

const isPlaying = ref(false)
const isMuted = ref(true)
const isFullscreen = ref(false)
const showInfo = ref(false)

const videoInfo = ref<GbVideoInfo | null>(null)
const audioInfo = ref<GbAudioInfo | null>(null)
const playFps = ref(0)
const videoBps = ref(0)
const audioBps = ref(0)
const bufferMs = ref(0)
const perfLevel = ref<number | null>(null)
const isRecording = ref(false)
const recordSeconds = ref(0)
const recordTimeText = computed(() => {
  const s = Math.max(0, Math.floor(recordSeconds.value))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(sec).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
})

const destroyPlayer = async () => {
  if (player) {
    try {
      if (isRecording.value) {
        try { player.stopRecordAndSave() } catch { /* ignore */ }
      }
      await player.destroy()
    } catch {
      /* 忽略销毁异常 */
    }
    player = null
  }
  pendingUrl = null
  isPlaying.value = false
  isRecording.value = false
  recordSeconds.value = 0
}

const startPlay = async (url: string) => {
  resetMetrics()
  await destroyPlayer()
  pendingUrl = url
  if (!containerRef.value) return
  if (typeof window.Jessibuca !== 'function') {
    emit('error', 'Jessibuca 未加载：请确认 index.html 已引入 /static/js/jessibuca/jessibuca.js')
    return
  }
  try {
    player = new window.Jessibuca({
      container: containerRef.value,
      videoBuffer: props.videoBuffer,
      isResize: true,
      text: '',
      loadingText: '正在拉流…',
      useMSE: false,
      useWCS: false,
      decoder: DECODER_JS,
      forceNoOffscreen: true,
      hasAudio: props.hasAudio,
      isFlv: true,
      timeout: 10,
      heartTimeout: 5,
      heartTimeoutReplay: true,
      heartTimeoutReplayTimes: 3,
      loadingTimeout: 10,
      loadingTimeoutReplay: true,
      loadingTimeoutReplayTimes: 3,
      wasmDecodeErrorReplay: true,
      operateBtns: {
        fullscreen: false,
        screenshot: false,
        play: false,
        audio: false,
        recorder: false,
      },
      showBandwidth: false,
    })

    player.on('play', () => {
      isPlaying.value = true
      emit('playing')
    })
    player.on('pause', () => {
      isPlaying.value = false
      emit('pause')
    })
    player.on('error', (e: unknown) => emit('error', '播放器错误: ' + String(e)))
    player.on('streamEnd', () => {
      isPlaying.value = false
      emit('streamEnd')
    })
    player.on('timeout', () => emit('timeout'))
    player.on('loadingTimeout', () => emit('timeout'))
    player.on('videoInfo', (info: unknown) => {
      videoInfo.value = info as GbVideoInfo
      emit('videoInfo', info as GbVideoInfo)
    })
    player.on('audioInfo', (info: unknown) => {
      audioInfo.value = info as GbAudioInfo
      emit('audioInfo', info as GbAudioInfo)
    })
    player.on('stats', (s: unknown) => {
      const stat = s as GbPlayerStats
      if (stat) {
        if (typeof stat.fps === 'number') playFps.value = stat.fps
        if (typeof stat.vbps === 'number') videoBps.value = stat.vbps
        if (typeof stat.abps === 'number') audioBps.value = stat.abps
        if (typeof stat.buf === 'number') bufferMs.value = stat.buf
      }
      emit('stats', stat)
    })
    player.on('performance', (level: unknown) => {
      const l = Number(level)
      perfLevel.value = Number.isNaN(l) ? null : l
      emit('performance', l)
    })
    player.on('fullscreen', (flag: unknown) => {
      isFullscreen.value = Boolean(flag)
      emit('fullscreen', Boolean(flag))
    })
    player.on('mute', (flag: unknown) => {
      isMuted.value = Boolean(flag)
      emit('mute', Boolean(flag))
    })
    player.on('recording', (flag: unknown) => {
      if (flag) {
        isRecording.value = true
      } else {
        isRecording.value = false
        recordSeconds.value = 0
        emit('record-stop')
      }
    })
    player.on('recordingTimestamp', (timestamp: unknown) => {
      const t = Number(timestamp)
      if (!Number.isNaN(t)) recordSeconds.value = t
    })

    let started = false
    const doPlay = () => {
      if (started || !player || !pendingUrl) return
      started = true
      player.play(pendingUrl).catch((e: unknown) => emit('error', String(e)))
    }
    if (player.hasLoaded()) doPlay()
    else player.on('load', doPlay)
    setTimeout(doPlay, 1500)
  } catch (e) {
    emit('error', '播放器初始化失败: ' + (e instanceof Error ? e.message : String(e)))
  }
}

const resetMetrics = () => {
  videoInfo.value = null
  audioInfo.value = null
  playFps.value = 0
  videoBps.value = 0
  audioBps.value = 0
  bufferMs.value = 0
  perfLevel.value = null
}

/* ==================== 计算属性 ==================== */

const hasStreams = computed(() => Array.isArray(props.streams) && props.streams.length >= 2)

const resolutionText = computed(() =>
  videoInfo.value ? `${videoInfo.value.width}×${videoInfo.value.height}` : '—',
)
const videoCodecText = computed(() => {
  const v = videoInfo.value
  if (!v) return '—'
  if (v.encType) return v.encType.toUpperCase()
  if (v.encTypeCode === 10) return 'H264'
  if (v.encTypeCode === 12) return 'H265'
  return '—'
})
const audioCodecText = computed(() => {
  const a = audioInfo.value
  if (!a) return '—'
  if (a.encType) return a.encType.toUpperCase()
  if (a.encTypeCode === 10) return 'AAC'
  if (a.encTypeCode === 7) return 'G711A'
  if (a.encTypeCode === 8) return 'G711U'
  return '—'
})
const fpsText = computed(() => (playFps.value > 0 ? `${playFps.value} fps` : '—'))
const videoBitrateText = computed(() =>
  videoBps.value > 0 ? `${Math.round((videoBps.value * 8) / 1000)} kbps` : '—',
)
const audioBitrateText = computed(() =>
  audioBps.value > 0 ? `${Math.round((audioBps.value * 8) / 1000)} kbps` : '—',
)
const bufferText = computed(() => (bufferMs.value > 0 ? `${bufferMs.value} ms` : '—'))
const performanceText = computed(() => {
  switch (perfLevel.value) {
    case 0: return '卡顿'
    case 1: return '流畅'
    case 2: return '非常流畅'
    default: return '—'
  }
})

/* ==================== 电子放大 ==================== */

const zoomScale = ref(1)
const zoomX = ref(0)
const zoomY = ref(0)
const isDragging = ref(false)
const isZoomed = computed(() => zoomScale.value > 1)
const zoomTransform = computed(() => `translate(${zoomX.value}px, ${zoomY.value}px) scale(${zoomScale.value})`)
const zoomLabel = computed(() => `${Math.round(zoomScale.value * 100)}%`)

const zoomIn = () => {
  applyZoom(Math.min(MAX_ZOOM, zoomScale.value + ZOOM_STEP))
}
const zoomOut = () => {
  applyZoom(Math.max(MIN_ZOOM, zoomScale.value - ZOOM_STEP))
}
const resetZoom = () => {
  zoomScale.value = 1
  zoomX.value = 0
  zoomY.value = 0
}
const applyZoom = (nextScale: number, anchorX = 0.5, anchorY = 0.5) => {
  if (nextScale <= 1) {
    resetZoom()
    return
  }
  const el = containerRef.value
  if (!el) {
    zoomScale.value = nextScale
    return
  }
  const w = el.clientWidth
  const h = el.clientHeight
  const ax = anchorX * w
  const ay = anchorY * h
  const oldScale = zoomScale.value
  const imgX = (ax - zoomX.value) / oldScale
  const imgY = (ay - zoomY.value) / oldScale
  zoomScale.value = nextScale
  zoomX.value = ax - imgX * nextScale
  zoomY.value = ay - imgY * nextScale
  clampPan()
}
const clampPan = () => {
  const el = containerRef.value
  if (!el || zoomScale.value <= 1) {
    zoomX.value = 0
    zoomY.value = 0
    return
  }
  const w = el.clientWidth
  const h = el.clientHeight
  const minX = -(w * (zoomScale.value - 1))
  const minY = -(h * (zoomScale.value - 1))
  if (zoomX.value > 0) zoomX.value = 0
  if (zoomX.value < minX) zoomX.value = minX
  if (zoomY.value > 0) zoomY.value = 0
  if (zoomY.value < minY) zoomY.value = minY
}
const onWheel = (e: WheelEvent) => {
  if (!props.showZoom) return
  if (!containerRef.value) return
  e.preventDefault()
  const rect = containerRef.value.getBoundingClientRect()
  const anchorX = (e.clientX - rect.left) / rect.width
  const anchorY = (e.clientY - rect.top) / rect.height
  const delta = e.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoomScale.value + delta))
  applyZoom(next, anchorX, anchorY)
}
let dragStartX = 0
let dragStartY = 0
let panStartX = 0
let panStartY = 0
const onMouseDown = (e: MouseEvent) => {
  if (!props.showZoom || !isZoomed.value) return
  isDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  panStartX = zoomX.value
  panStartY = zoomY.value
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}
const onMouseMove = (e: MouseEvent) => {
  if (!isDragging.value) return
  zoomX.value = panStartX + (e.clientX - dragStartX)
  zoomY.value = panStartY + (e.clientY - dragStartY)
  clampPan()
}
const onMouseUp = () => {
  isDragging.value = false
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}
const onDoubleClick = () => {
  if (props.showZoom && isZoomed.value) resetZoom()
}

/* ==================== 工具栏操作 ==================== */

const togglePlay = () => {
  if (!player) return
  if (isPlaying.value) {
    player.pause()
  } else if (pendingUrl) {
    player.play(pendingUrl)
  }
}
const toggleMute = () => {
  if (!player) return
  if (isMuted.value) {
    player.cancelMute()
    isMuted.value = false
  } else {
    player.mute()
    isMuted.value = true
  }
}
const takeScreenshot = () => {
  player?.screenshot(`snap_${Date.now()}`, 'png', 1)
}
const toggleRecord = () => {
  if (!player) return
  if (isRecording.value) {
    player.stopRecordAndSave()
    // 点击停止立即隐藏 REC 指示器，不等异步事件
    isRecording.value = false
    recordSeconds.value = 0
    emit('record-stop')
    return
  }
  try {
    player.startRecord(`record_${Date.now()}`, 'mp4', 1)
    isRecording.value = true
    recordSeconds.value = 0
    emit('record-start')
  } catch (e) {
    emit('error', '录制启动失败: ' + (e instanceof Error ? e.message : String(e)))
  }
}
const toggleFullscreen = () => {
  const el = rootRef.value
  if (!el) return
  if (document.fullscreenElement) void document.exitFullscreen()
  else void el.requestFullscreen()
}

const onFullscreenChange = () => {
  isFullscreen.value = document.fullscreenElement === rootRef.value
  emit('fullscreen', isFullscreen.value)
}
const onRefresh = () => {
  emit('refresh')
}

const onStop = () => {
  emit('stop')
}

/* ==================== 码流 / 收流 ==================== */

const streamMenuOpen = ref(false)
const transportMenuOpen = ref(false)

const onSwitchStream = (value: string) => {
  streamMenuOpen.value = false
  if (value !== props.currentStream) emit('switch-stream', value)
}
const onSwitchTransport = (value: 'UDP' | 'TCP-passive' | 'TCP-active') => {
  transportMenuOpen.value = false
  if (value !== props.transport) emit('switch-transport', value)
}

/* ==================== PTZ ==================== */

const ptzMenuOpen = ref(false)
const ptzActiveTab = ref<'direction' | 'lens' | 'speed'>('direction')
const presetMenuOpen = ref(false)
const cruiseMenuOpen = ref(false)

// 面板并排展开，可同时打开，互不关闭
const togglePtzMenu = () => {
  ptzMenuOpen.value = !ptzMenuOpen.value
}
const togglePresetMenu = () => {
  presetMenuOpen.value = !presetMenuOpen.value
}
const toggleCruiseMenu = () => {
  cruiseMenuOpen.value = !cruiseMenuOpen.value
}

const ptzSpeeds = ref({
  move: 100,
  zoom: 5,
  focus: 5,
  iris: 5,
})

let ptzActive = ''
const ptzStart = (direction: string) => {
  ptzActive = direction
  emit('ptz', direction, true)
  window.addEventListener('mouseup', ptzGlobalUp, { once: true })
}
const ptzGlobalUp = () => {
  if (!ptzActive) return
  emit('ptz', ptzActive, false)
  ptzActive = ''
}
const ptzStop = () => {
  if (ptzActive) {
    emit('ptz', ptzActive, false)
    ptzActive = ''
  }
}

let focusActive = ''
const focusStart = (direction: 'near' | 'far') => {
  focusActive = direction
  emit('focus', direction, ptzSpeeds.value.focus)
  window.addEventListener('mouseup', focusGlobalUp, { once: true })
}
const focusGlobalUp = () => {
  if (!focusActive) return
  emit('focus', 'stop', 0)
  focusActive = ''
}
const focusStop = () => {
  if (focusActive) {
    emit('focus', 'stop', 0)
    focusActive = ''
  }
}

let irisActive = ''
const irisStart = (direction: 'open' | 'close') => {
  irisActive = direction
  emit('iris', direction, ptzSpeeds.value.iris)
  window.addEventListener('mouseup', irisGlobalUp, { once: true })
}
const irisGlobalUp = () => {
  if (!irisActive) return
  emit('iris', 'stop', 0)
  irisActive = ''
}
const irisStop = () => {
  if (irisActive) {
    emit('iris', 'stop', 0)
    irisActive = ''
  }
}

const wiperOn = ref(false)
const toggleWiper = () => {
  wiperOn.value = !wiperOn.value
  emit('wiper', wiperOn.value ? 'on' : 'off')
}

const onSpeedInput = (key: string, val: string) => {
  ;(ptzSpeeds.value as Record<string, number>)[key] = Number(val)
  emit('speed-change', { ...ptzSpeeds.value })
}

/* ---------------- 预置位 ---------------- */

/** 固定 10 个槽位，与后端已有预置位按 presetId 合并 */
const presetSlots = computed(() => {
  return Array.from({ length: 10 }, (_, i) => {
    const presetId = i + 1
    return {
      presetId,
      preset: props.presets?.find((p) => p.presetId === presetId) ?? null,
    }
  })
})

/** 进行中的预置位动作（sN=设置中、dN=删除中），避免重复点击 */
const presetBusyId = ref('')

const onSetPreset = (presetId: number) => {
  if (presetBusyId.value) return
  presetBusyId.value = `s${presetId}`
  emit('preset-save', presetId)
  setTimeout(() => {
    presetBusyId.value = ''
  }, 600)
}

const onCallPreset = (id: string, presetId: number) => {
  if (presetBusyId.value) return
  presetBusyId.value = `c${presetId}`
  emit('preset-call', id)
  setTimeout(() => {
    presetBusyId.value = ''
  }, 600)
}

const onDeletePreset = (id: string, presetId: number) => {
  if (presetBusyId.value) return
  presetBusyId.value = `d${presetId}`
  emit('preset-delete', id, presetId)
  setTimeout(() => {
    presetBusyId.value = ''
  }, 600)
}

/* ---------------- 巡航 ---------------- */

const selectedCruiseId = ref('')
const runningCruiseId = ref('')
const cruiseBusy = ref(false)
const newCruise = ref<{
  name: string
  speed: number
  staySeconds: number
  points: number[]
} | null>(null)
const addPointPresetId = ref('')
const addPointStay = ref<number | null>(null)

const selectedCruise = computed(
  () => props.cruises?.find((c) => c.id === selectedCruiseId.value) ?? null,
)

/** 可添加的预置位：已设置且不在当前路线中 */
const availablePresets = computed(() => {
  const used = new Set((props.cruisePoints ?? []).map((p) => p.presetId))
  return (props.presets ?? []).filter((p) => !used.has(p.presetId))
})

/** 面板横向偏移：云台/预置位各占 260px，依次右排 */
const cruisePanelOffset = computed(
  () => (ptzMenuOpen.value ? 260 : 0) + (presetMenuOpen.value ? 260 : 0),
)

const selectCruise = (id: string) => {
  if (selectedCruiseId.value === id) return
  selectedCruiseId.value = id
  addPointPresetId.value = ''
  addPointStay.value = null
  emit('cruise-select', id)
}

const startNewCruise = () => {
  newCruise.value = {
    name: `巡航${(props.cruises?.length ?? 0) + 1}`,
    speed: 50,
    staySeconds: 10,
    points: [],
  }
}

const toggleNewCruisePreset = (presetId: number) => {
  if (!newCruise.value) return
  const points = newCruise.value.points
  const idx = points.indexOf(presetId)
  if (idx >= 0) points.splice(idx, 1)
  else points.push(presetId)
}

const confirmNewCruise = () => {
  if (!newCruise.value || cruiseBusy.value) return
  const name = newCruise.value.name.trim()
  if (!name) return
  // 巡航至少需要 2 个点
  if (newCruise.value.points.length < 2) return
  const speed = Math.min(255, Math.max(1, newCruise.value.speed || 50))
  const staySeconds = Math.min(255, Math.max(1, newCruise.value.staySeconds || 10))
  cruiseBusy.value = true
  emit('cruise-save', {
    name,
    speed,
    staySeconds,
    points: newCruise.value.points.map((presetId) => ({ presetId })),
  })
  newCruise.value = null
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onCruiseFieldChange = (field: 'speed' | 'staySeconds', raw: string) => {
  if (!selectedCruise.value) return
  const num = Number(raw)
  if (Number.isNaN(num)) return
  const limits = field === 'speed' ? [1, 255] : [1, 255]
  const value = Math.min(limits[1], Math.max(limits[0], Math.round(num)))
  if (value === selectedCruise.value[field]) return
  cruiseBusy.value = true
  emit('cruise-update', selectedCruise.value.id, { [field]: value })
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onDeleteCruise = (id: string) => {
  if (cruiseBusy.value) return
  cruiseBusy.value = true
  if (selectedCruiseId.value === id) selectedCruiseId.value = ''
  if (runningCruiseId.value === id) runningCruiseId.value = ''
  emit('cruise-delete', id)
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onAddPoint = () => {
  const presetId = Number(addPointPresetId.value)
  if (!selectedCruiseId.value || !presetId || cruiseBusy.value) return
  let stay: number | null = null
  if (addPointStay.value !== null) {
    stay = Math.min(15, Math.max(1, Math.round(addPointStay.value)))
  }
  cruiseBusy.value = true
  emit('cruise-add-point', selectedCruiseId.value, presetId, stay)
  addPointPresetId.value = ''
  addPointStay.value = null
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onRemovePoint = (presetId: number) => {
  if (!selectedCruiseId.value || cruiseBusy.value) return
  cruiseBusy.value = true
  emit('cruise-remove-point', selectedCruiseId.value, presetId)
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onStartCruise = (id: string) => {
  if (cruiseBusy.value) return
  cruiseBusy.value = true
  runningCruiseId.value = id
  emit('cruise-start', id)
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

const onStopCruise = (id: string) => {
  if (cruiseBusy.value) return
  cruiseBusy.value = true
  runningCruiseId.value = ''
  emit('cruise-stop', id)
  setTimeout(() => {
    cruiseBusy.value = false
  }, 600)
}

/* ==================== 生命周期 ==================== */

watch(
  () => props.src,
  (url) => {
    if (url) startPlay(url)
    else destroyPlayer()
  },
  { flush: 'post' },
)

onMounted(() => {
  if (props.src) startPlay(props.src)
  // 点击外部关闭弹出菜单
  document.addEventListener('mousedown', onDocMouseDown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  destroyPlayer()
  document.removeEventListener('mousedown', onDocMouseDown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})

// 点击外部关闭所有弹出菜单
const onDocMouseDown = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  // 检查是否点击在菜单弹出层内
  if (!target.closest('.menu-popover') && !target.closest('[data-menu-trigger]')) {
    streamMenuOpen.value = false
    transportMenuOpen.value = false
    ptzMenuOpen.value = false
    presetMenuOpen.value = false
    cruiseMenuOpen.value = false
  }
}

// 路线列表刷新后，选中项已被删除时清空
watch(
  () => props.cruises,
  (list) => {
    if (selectedCruiseId.value && !list?.some((c) => c.id === selectedCruiseId.value)) {
      selectedCruiseId.value = ''
    }
  },
)

/* ==================== Expose ==================== */

defineExpose({
  play: (url: string) => startPlay(url),
  pause: () => player?.pause(),
  resume: () => player?.play(),
  stop: () => {
    if (!player) return
    try {
      player.pause()
      player.clearView()
    } catch {
      /* ignore */
    }
    resetMetrics()
  },
  destroy: destroyPlayer,
  screenshot: (filename?: string) => player?.screenshot(filename, 'png', 1),
  startRecord: (filename?: string) => {
    if (!player || isRecording.value) return
    player.startRecord(filename || `record_${Date.now()}`, 'mp4', 1)
    isRecording.value = true
    recordSeconds.value = 0
    emit('record-start')
  },
  stopRecord: () => {
    if (!player || !isRecording.value) return
    player.stopRecordAndSave()
    isRecording.value = false
    recordSeconds.value = 0
    emit('record-stop')
  },
  fullscreen: () => void rootRef.value?.requestFullscreen(),
  cancelMute: () => player?.cancelMute(),
  mute: () => player?.mute(),
})

</script>

<style>
/* jessibuca 内部 canvas 不拦截鼠标事件，让外层 stage 能收到 wheel/mousedown/dblclick */
.jessibuca-container canvas {
  pointer-events: none !important;
}
</style>
