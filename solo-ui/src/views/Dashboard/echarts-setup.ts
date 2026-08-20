/**
 * ECharts 按需注册（tree-shaking 入口）
 *
 * <p>只引入本项目用到的 chart / component / renderer，规避全量引入的 ~1MB 体积。
 * 各 chart 组件（KpiCard 的 sparkline、DonutChart 的饼图）共用本注册结果。</p>
 */
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent,
])
