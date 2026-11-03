<template>
  <div>
    <!-- ============ 核心指标卡片 ============ -->
    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.label" :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '16px' }">
          <div class="stat-content">
            <div>
              <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
            <el-icon :size="34" :style="{ color: card.color, opacity: 0.85 }">
              <component :is="card.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 待办提醒 ============ -->
    <el-alert
      v-if="summary.myTodoApproval > 0 || summary.unreadNotice > 0"
      type="warning"
      class="todo-alert"
      show-icon
      :closable="false"
    >
      <template #title>
        您有
        <strong>{{ summary.myTodoApproval }}</strong>
        条待审批、
        <strong>{{ summary.unreadNotice }}</strong>
        条未读通知
      </template>
    </el-alert>

    <!-- ============ 图表区 ============ -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>工单状态分布</template>
          <div ref="statusChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>工单类型分布</template>
          <div ref="typeChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="hover">
          <template #header>
            近 30 天工单趋势
          </template>
          <div ref="trendChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="hover">
          <template #header>处理人工作量 TOP8</template>
          <div ref="rankChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 审批状态 ============ -->
    <el-card shadow="hover" class="chart-row">
      <template #header>审批单状态分布</template>
      <div ref="approvalChartRef" class="chart-box"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
// 按需引入 ECharts，而不是 import * as echarts 全量导入。
// 全量引入会让打包体积超过 1MB，按需引入后能减少 70% 以上。
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard } from '@/api'

// 注册需要的模块
echarts.use([
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DatasetComponent,
  CanvasRenderer
])

const summary = ref({})
const statusCount = ref([])
const typeCount = ref([])
const dailyTrend = ref([])
const assigneeRank = ref([])
const approvalCount = ref([])

const statusChartRef = ref()
const typeChartRef = ref()
const trendChartRef = ref()
const rankChartRef = ref()
const approvalChartRef = ref()

let charts = []
let resizeHandler = null

/** 状态码 → 中文 + 颜色 */
const STATUS_MAP = {
  0: { name: '待受理', color: '#409eff' },
  1: { name: '处理中', color: '#e6a23c' },
  2: { name: '待确认', color: '#909399' },
  3: { name: '已完成', color: '#67c23a' },
  4: { name: '已关闭', color: '#c0c4cc' },
  5: { name: '已驳回', color: '#f56c6c' }
}

const statCards = computed(() => [
  { label: '工单总数', value: summary.value.total ?? 0, icon: 'Tickets', color: '#409eff' },
  { label: '进行中', value: summary.value.processing ?? 0, icon: 'Loading', color: '#e6a23c' },
  { label: '已完成', value: summary.value.finished ?? 0, icon: 'CircleCheck', color: '#67c23a' },
  { label: '完成率(%)', value: summary.value.finishRate ?? 0, icon: 'TrendCharts', color: '#909399' },
  { label: '超时工单', value: summary.value.timeout ?? 0, icon: 'Warning', color: '#f56c6c' },
  { label: '我的工单', value: summary.value.myTicketCount ?? 0, icon: 'User', color: '#6a5cff' },
  { label: '待我审批', value: summary.value.myTodoApproval ?? 0, icon: 'Stamp', color: '#12b886' },
  { label: '系统用户', value: summary.value.userCount ?? 0, icon: 'UserFilled', color: '#8b5cf6' }
])

/** 把后端返回的 { name: "0", value: 3 } 转成图表数据 */
function mapStatusData(list) {
  const data = list || []
  return {
    names: data.map((d) => STATUS_MAP[Number(d.name)]?.name || '未知'),
    values: data.map((d) => d.value),
    colors: data.map((d) => STATUS_MAP[Number(d.name)]?.color || '#c0c4cc')
  }
}

const TYPE_MAP = { BUG: '系统缺陷', FEATURE: '功能需求', COMPLAINT: '投诉建议', OTHER: '其他' }
const TYPE_COLORS = ['#f56c6c', '#409eff', '#e6a23c', '#909399']

function initCharts() {
  charts = []

  // ---- 1. 状态分布：环形图 ----
  const s = mapStatusData(statusCount.value)
  const c1 = echarts.init(statusChartRef.value)
  c1.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 10, textStyle: { fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '65%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{c} ({d}%)', fontSize: 11 },
        data: s.names.map((name, i) => ({
          name,
          value: s.values[i],
          itemStyle: { color: s.colors[i] }
        }))
      }
    ]
  })
  charts.push(c1)

  // ---- 2. 类型分布：饼图 ----
  const c2 = echarts.init(typeChartRef.value)
  const tData = (typeCount.value || []).map((d, i) => ({
    name: TYPE_MAP[d.name] || d.name,
    value: d.value,
    itemStyle: { color: TYPE_COLORS[i % TYPE_COLORS.length] }
  }))
  c2.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, itemWidth: 10, textStyle: { fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: '58%',
        center: ['50%', '45%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {d}%', fontSize: 11 },
        data: tData
      }
    ]
  })
  charts.push(c2)

  // ---- 3. 趋势：折线图 + 面积渐变 ----
  const c3 = echarts.init(trendChartRef.value)
  const trend = dailyTrend.value || []
  c3.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 35 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((d) => d.name),
      axisLabel: { fontSize: 11, rotate: trend.length > 15 ? 45 : 0 }
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    series: [
      {
        name: '新增工单',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: trend.map((d) => d.value),
        lineStyle: { width: 3, color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.35)' },
            { offset: 1, color: 'rgba(64,158,255,0.02)' }
          ])
        }
      }
    ]
  })
  charts.push(c3)

  // ---- 4. 处理人排行：横向柱状图 ----
  const c4 = echarts.init(rankChartRef.value)
  const rank = assigneeRank.value || []
  c4.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 70, right: 25, top: 20, bottom: 25 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: rank.map((d) => d.name).reverse(),
      axisLabel: { fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: rank.map((d) => d.value).reverse(),
        itemStyle: {
          borderRadius: [0, 7, 7, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#6a5cff' }
          ])
        },
        label: { show: true, position: 'right', fontSize: 11 }
      }
    ]
  })
  charts.push(c4)

  // ---- 5. 审批状态：柱状图 ----
  const c5 = echarts.init(approvalChartRef.value)
  const APP_MAP = { 0: '审批中', 1: '已通过', 2: '已驳回', 3: '已撤销' }
  const APP_COLORS = { 0: '#e6a23c', 1: '#67c23a', 2: '#f56c6c', 3: '#909399' }
  const aData = approvalCount.value || []
  c5.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 20, top: 25, bottom: 30 },
    xAxis: {
      type: 'category',
      data: aData.map((d) => APP_MAP[Number(d.name)] || '未知')
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barWidth: 45,
        data: aData.map((d) => ({
          value: d.value,
          itemStyle: { color: APP_COLORS[Number(d.name)] || '#409eff', borderRadius: [5, 5, 0, 0] }
        })),
        label: { show: true, position: 'top' }
      }
    ]
  })
  charts.push(c5)
}

async function loadData() {
  try {
    const res = await getDashboard()
    const d = res.data
    summary.value = d.summary || {}
    statusCount.value = d.statusCount || []
    typeCount.value = d.typeCount || []
    dailyTrend.value = d.dailyTrend || []
    assigneeRank.value = d.assigneeRank || []
    approvalCount.value = d.approvalCount || []

    await nextTick()
    initCharts()
  } catch (e) {
    console.error('加载看板数据失败', e)
  }
}

onMounted(() => {
  loadData()
  // 窗口尺寸变化时重新调整图表大小（ECharts 必须手动处理）
  resizeHandler = () => charts.forEach((c) => c.resize())
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  // 必须手动销毁，否则组件切换时内存泄漏
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.todo-alert {
  margin-top: 16px;
}

.chart-row {
  margin-top: 16px;
}
</style>