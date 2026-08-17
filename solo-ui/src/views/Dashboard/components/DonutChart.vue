<script setup lang="ts">
/**
 * 通用环形图（donut）
 *
 * <p>用于设备状态分布、厂商分布两个场景；颜色与 legend 由 props 传入。
 * 设计原则：</p>
 * <ul>
 *   <li>状态色（绿/灰/橙）只用于"状态分布"场景，带图标 + 文字，不用颜色单独编码</li>
 *   <li>分类色（5 种固定 hue）用于"厂商分布"等 ≤ 6 类的场景</li>
 *   <li>center 居中显示主数值（hero number）</li>
 *   <li>hover 触发扇区高亮 + tooltip</li>
 * </ul>
 *
 * <p>{@code <VChart>} 已在 {@code main.ts} 全局注册。</p>
 */
import { computed, shallowRef, watch } from 'vue'
import type { DashboardDistribution } from '@/types/api'

interface Props {
  data: DashboardDistribution[]
  /** 中心主数值（如总设备数 128） */
  centerValue?: number | string
  /** 中心主数值标签（如 "总设备"） */
  centerLabel?: string
  /** 数据为空时显示的占位文案 */
  emptyText?: string
  /** 颜色板（按 data 顺序取色）；不传则用 ds-cat-* 分类色 */
  colors?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  centerValue: '',
  centerLabel: '',
  emptyText: '暂无数据',
  colors: () => [],
})

const hasData = computed(() => props.data.length > 0)

const option = shallowRef(buildOption(props.data, props.colors))
watch(
  () => [props.data, props.colors],
  () => {
    option.value = buildOption(props.data, props.colors)
  },
  { deep: true },
)

function buildOption(data: DashboardDistribution[], colors: string[]): Record<string, unknown> {
  // 默认使用项目分类色板（ds-cat-1 ~ ds-cat-6）
  const defaultColors = ['#409EFF', '#17A2B8', '#8E44AD', '#16A085', '#F39C12', '#34495E']
  const palette = colors.length ? colors : defaultColors

  return {
    color: palette,
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(50, 50, 50, 0.92)',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: (params: { name: string; value: number; percent: number }) => {
        return `${params.name}<br/>${params.value} (${params.percent}%)`
      },
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#606266', fontSize: 12 },
      formatter: (name: string) => {
        const hit = data.find((d) => d.name === name)
        return hit ? `${name}  ${hit.value}` : name
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 500,
            color: '#303133',
          },
        },
        data: data.map((d) => ({ name: d.name, value: d.value })),
      },
    ],
  }
}
</script>

<template>
  <div class="donut-chart">
    <div v-if="hasData" class="chart-host">
      <VChart :option="option" autoresize />
      <div v-if="centerValue !== ''" class="center-block">
        <div class="center-value">{{ centerValue }}</div>
        <div class="center-label">{{ centerLabel }}</div>
      </div>
    </div>
    <div v-else class="chart-empty">{{ emptyText }}</div>
  </div>
</template>

<style scoped>
.donut-chart {
  position: relative;
  width: 100%;
  height: 240px;
}

.chart-host {
  width: 100%;
  height: 100%;
  position: relative;
}

.center-block {
  position: absolute;
  left: 38%;
  top: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.center-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.center-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}
</style>
