<script setup lang="ts">
/**
 * 单个 KPI 卡片：标题 / 大数字 / sparkline 微图 / 较 1h 前差值
 *
 * <p>sparkline 用 ECharts mini 模式：隐藏所有坐标轴/图例/网格，只保留一条折线 + 区域渐变。
 * 卡片整体遵循"一个焦点"原则：进首页第一眼看到的就是大数字，其他都是辅助。</p>
 *
 * <p>{@code <VChart>} 已在 {@code main.ts} 全局注册，无需在组件内 import。</p>
 */
import { computed, shallowRef, watch } from 'vue'

interface Props {
  title: string
  /** 大数字：支持 number（如 102）或 string（如 "2/3"） */
  value: number | string
  /** sparkline 数据（顺序：旧→新）；为空时不渲染图 */
  trend: number[]
  /** 较 1h 前差值；null=无法比较 */
  diff: number | null
  /** sparkline 主色（hex，例如 "#409EFF"），区域渐变由色值自动派生 */
  color: string
  /** 数字单位（"%" / "路" / ""） */
  unit?: string
}

const props = withDefaults(defineProps<Props>(), {
  unit: '',
})

const diffText = computed(() => {
  if (props.diff === null || props.diff === undefined) return '—'
  if (props.diff > 0) return `+${props.diff}`
  if (props.diff < 0) return `${props.diff}`
  return '±0'
})

const diffType = computed<'up' | 'down' | 'flat'>(() => {
  if (props.diff === null || props.diff === undefined) return 'flat'
  if (props.diff > 0) return 'up'
  if (props.diff < 0) return 'down'
  return 'flat'
})

// 暴露的 sparkline option（响应 trend 变化）
const option = shallowRef(buildOption(props.trend, props.color))
watch(
  () => [props.trend, props.color],
  () => {
    option.value = buildOption(props.trend, props.color)
  },
)

/**
 * hex → rgba(...,0.22)，ECharts areaStyle 直接吃 rgba 字符串
 */
function withAlpha(hex: string, alpha: number): string {
  const s = hex.replace('#', '')
  const r = parseInt(s.substring(0, 2), 16)
  const g = parseInt(s.substring(2, 4), 16)
  const b = parseInt(s.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function buildOption(data: number[], color: string): Record<string, unknown> {
  return {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: {
      type: 'category',
      show: false,
      data: data.map((_, i) => i),
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      show: false,
      scale: true,
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 2 },
        areaStyle: { color: withAlpha(color, 0.22) },
        data,
        animationDuration: 600,
      },
    ],
    tooltip: { show: false },
  }
}
</script>

<template>
  <div class="kpi-card" :style="{ '--kpi-color': color }">
    <div class="kpi-head">
      <span class="kpi-title">{{ title }}</span>
    </div>
    <div class="kpi-body">
      <div class="kpi-value-block">
        <span class="kpi-value">{{ value }}</span>
        <span v-if="unit" class="kpi-unit">{{ unit }}</span>
      </div>
      <div v-if="trend && trend.length" class="kpi-sparkline">
        <VChart :option="option" autoresize />
      </div>
    </div>
    <div class="kpi-foot">
      <span class="kpi-diff" :class="`diff-${diffType}`">
        <span v-if="diffType === 'up'" class="diff-arrow">▲</span>
        <span v-else-if="diffType === 'down'" class="diff-arrow">▼</span>
        <span v-else class="diff-arrow">─</span>
        {{ diffText }}
      </span>
      <span class="kpi-foot-label">较 1h 前</span>
    </div>
  </div>
</template>

<style scoped>
.kpi-card {
  background: var(--el-bg-color);
  border-radius: var(--ds-card-radius);
  box-shadow: var(--ds-card-shadow);
  padding: var(--ds-card-padding);
  display: flex;
  flex-direction: column;
  height: 148px;
  position: relative;
  transition: box-shadow 0.2s ease;
}

.kpi-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06), 0 4px 16px rgba(0, 0, 0, 0.06);
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.kpi-title {
  font-weight: 500;
}

.kpi-body {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 8px;
  flex: 1;
  min-height: 0;
}

.kpi-value-block {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--el-text-color-primary);
  line-height: 1;
}

.kpi-value {
  font-size: 36px;
  font-weight: 600;
  letter-spacing: -0.5px;
}

.kpi-unit {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.kpi-sparkline {
  width: 96px;
  height: 48px;
  flex-shrink: 0;
}

.kpi-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.kpi-diff {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 500;
}

.diff-arrow {
  font-size: 10px;
  line-height: 1;
}

.diff-up {
  color: var(--ds-success);
}
.diff-down {
  color: var(--ds-serious);
}
.diff-flat {
  color: var(--el-text-color-placeholder);
}

.kpi-foot-label {
  color: var(--el-text-color-placeholder);
}
</style>
