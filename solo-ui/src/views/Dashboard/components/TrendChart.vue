<script setup lang="ts">
/**
 * 24h 趋势折线（双系列直接标签）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>两系列：在线设备（蓝）+ 活跃流（青）</li>
 *   <li>≤ 4 系列，direct label 替代 legend box（dataviz 原则：identity never color-alone）</li>
 *   <li>hover 十字光标 + tooltip（dataviz 原则：默认带 hover 层）</li>
 *   <li>recessive grid（dataviz 原则：grid 要"退后"）</li>
 *   <li>空数据兜底：少于 2 个点降级为占位文案</li>
 * </ul>
 *
 * <p>{@code <VChart>} 已在 {@code main.ts} 全局注册。</p>
 */
import { computed, shallowRef, watch } from 'vue'
import type { DashboardTrendPoint } from '@/types/api'

interface Props {
  deviceOnline: DashboardTrendPoint[]
  activeStream: DashboardTrendPoint[]
}

const props = defineProps<Props>()

const hasData = computed(
  () => props.deviceOnline.length >= 1 || props.activeStream.length >= 1,
)

const option = shallowRef(buildOption(props.deviceOnline, props.activeStream))
watch(
  () => [props.deviceOnline, props.activeStream],
  () => {
    option.value = buildOption(props.deviceOnline, props.activeStream)
  },
  { deep: true },
)

function buildOption(
  deviceSeries: DashboardTrendPoint[],
  streamSeries: DashboardTrendPoint[],
): Record<string, unknown> {
  // X 轴：合并两个 series 的 hour（按时序去重排序）
  const hourSet = new Set<string>()
  for (const p of deviceSeries) hourSet.add(p.hour)
  for (const p of streamSeries) hourSet.add(p.hour)
  const hours = Array.from(hourSet).sort()

  const lookup = (arr: DashboardTrendPoint[]) => {
    const m = new Map<string, number>()
    for (const p of arr) m.set(p.hour, p.value)
    return hours.map((h) => m.get(h) ?? null)
  }

  return {
    grid: { left: 40, right: 24, top: 32, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(50, 50, 50, 0.92)',
      borderColor: 'transparent',
      textStyle: { color: '#fff', fontSize: 12 },
      axisPointer: {
        type: 'cross',
        lineStyle: { color: 'rgba(64,158,255,0.4)', width: 1, type: 'dashed' },
        crossStyle: { color: 'rgba(64,158,255,0.4)', type: 'dashed' },
        label: { backgroundColor: '#409EFF' },
      },
    },
    xAxis: {
      type: 'category',
      data: hours,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#E4E7ED' } },
      axisTick: { show: false },
      axisLabel: { color: '#909399', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#F2F3F5', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#909399', fontSize: 11 },
    },
    series: [
      {
        name: '在线设备',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64,158,255,0.22)' },
              { offset: 1, color: 'rgba(64,158,255,0)' },
            ],
          },
        },
        data: lookup(deviceSeries),
        // 末端 direct label
        endLabel: {
          show: true,
          color: '#409EFF',
          fontSize: 12,
          fontWeight: 500,
          formatter: '在线设备',
          distance: 8,
        },
        emphasis: { focus: 'series' },
      },
      {
        name: '活跃流',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: { color: '#17A2B8', width: 2 },
        itemStyle: { color: '#17A2B8' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(23,162,184,0.22)' },
              { offset: 1, color: 'rgba(23,162,184,0)' },
            ],
          },
        },
        data: lookup(streamSeries),
        endLabel: {
          show: true,
          color: '#17A2B8',
          fontSize: 12,
          fontWeight: 500,
          formatter: '活跃流',
          distance: 8,
        },
        emphasis: { focus: 'series' },
      },
    ],
    animationDuration: 800,
  }
}
</script>

<template>
  <div class="trend-chart">
    <div v-if="hasData" class="chart-host">
      <VChart :option="option" autoresize />
    </div>
    <div v-else class="chart-empty">
      <span>暂无 24h 趋势数据</span>
      <small>系统启动 1h 后开始采集</small>
    </div>
  </div>
</template>

<style scoped>
.trend-chart {
  width: 100%;
  height: 320px;
  position: relative;
}

.chart-host {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}

.chart-empty small {
  font-size: 12px;
  opacity: 0.7;
}
</style>
