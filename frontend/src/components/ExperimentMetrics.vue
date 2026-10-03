<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMetric, getRunMetrics } from '../api/metric'
import { formatMetricValue, groupMetrics } from '../utils/metric'
import MetricDialog from './MetricDialog.vue'

const props = defineProps({ runId: { type: [String, Number], required: true } })
const expanded = ref(false)
const metrics = ref([])
const count = ref(null)
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const selected = ref(null)
const busyId = ref(null)
const grouped = computed(() => groupMetrics(metrics.value))
let loadSequence = 0
let active = true

async function loadMetrics() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const result = await getRunMetrics(props.runId)
    if (!active || sequence !== loadSequence) return
    metrics.value = result
    count.value = result.length
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.userMessage || '无法加载实验指标。'
  } finally {
    if (active && sequence === loadSequence) loading.value = false
  }
}

function toggleMetrics() {
  expanded.value = !expanded.value
  if (expanded.value) loadMetrics()
}

function openCreate() {
  selected.value = null
  dialogOpen.value = true
}

function openEdit(metric) {
  selected.value = { ...metric }
  dialogOpen.value = true
}

async function remove(metric) {
  if (busyId.value !== null) return
  busyId.value = metric.id
  try {
    await ElMessageBox.confirm('确定删除该实验指标吗？此操作无法撤销。', '删除指标', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
      confirmButtonClass: 'danger-confirm',
    })
    if (!active) return
    await deleteMetric(metric.id)
    if (!active) return
    ElMessage.success('实验指标删除成功')
    await loadMetrics()
  } catch { /* 取消不发请求；失败时由请求层显示后端消息。 */ }
  finally { busyId.value = null }
}

onUnmounted(() => { active = false; loadSequence++ })
</script>

<template>
  <div class="run-metrics">
    <ElButton text class="metrics-toggle" :aria-expanded="expanded" :aria-controls="`run-metrics-${runId}`"
      :disabled="dialogOpen || busyId !== null" @click="toggleMetrics">
      实验指标 · {{ expanded ? '收起' : '展开' }}<span v-if="count !== null">（{{ count }}）</span>
      <span class="runs-chevron" aria-hidden="true">{{ expanded ? '▴' : '▾' }}</span>
    </ElButton>
    <section v-show="expanded" :id="`run-metrics-${runId}`" class="metrics-panel" :aria-labelledby="`metrics-heading-${runId}`">
      <div class="metrics-toolbar">
        <h6 :id="`metrics-heading-${runId}`">实验指标</h6>
        <div class="metrics-toolbar-actions">
          <ElButton text :loading="loading" :disabled="busyId !== null || dialogOpen" @click="loadMetrics">刷新</ElButton>
          <ElButton text class="metrics-add" :disabled="busyId !== null || dialogOpen" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span>添加指标</ElButton>
        </div>
      </div>
      <p class="metrics-intro">记录本次运行的结构化数值结果。</p>
      <div v-if="loading" class="metrics-loading" aria-live="polite" aria-label="正在加载实验指标"><ElSkeleton :rows="2" animated /></div>
      <div v-else-if="error" class="metrics-state" role="alert">
        <strong>实验指标加载失败</strong><p>{{ error }}</p><ElButton @click="loadMetrics">重试</ElButton>
      </div>
      <div v-else-if="!metrics.length" class="metrics-state">
        <strong>暂无实验指标</strong><p>添加单值结果或不同训练步数的指标，便于比较实验表现。</p>
        <ElButton :disabled="busyId !== null || dialogOpen" @click="openCreate">添加第一条指标</ElButton>
      </div>
      <div v-else class="metrics-content">
        <div v-if="grouped.singleMetrics.length" class="single-metrics">
          <h6 class="metrics-subheading">单值指标</h6>
          <dl class="metric-grid">
            <div v-for="metric in grouped.singleMetrics" :key="metric.id" class="metric-tile">
              <dt>{{ metric.metricName }}</dt><dd>{{ formatMetricValue(metric) }}</dd>
              <div class="metric-actions">
                <ElButton text :disabled="busyId !== null || dialogOpen" @click="openEdit(metric)">编辑</ElButton>
                <ElButton text class="delete-button" :disabled="busyId !== null || dialogOpen" @click="remove(metric)">删除</ElButton>
              </div>
            </div>
          </dl>
        </div>
        <div v-if="grouped.seriesGroups.length" class="series-metrics">
          <h6 class="metrics-subheading">序列指标</h6>
          <div v-for="group in grouped.seriesGroups" :key="group.name" class="metric-series">
            <table>
              <caption>{{ group.name }}</caption>
              <thead><tr><th scope="col">训练步数</th><th scope="col">指标值</th><th scope="col" class="metric-operation">操作</th></tr></thead>
              <tbody>
                <tr v-for="metric in group.records" :key="metric.id">
                  <td>{{ metric.step }}</td><td>{{ formatMetricValue(metric) }}</td>
                  <td class="metric-operation"><div class="metric-actions">
                    <ElButton text :disabled="busyId !== null || dialogOpen" @click="openEdit(metric)">编辑</ElButton>
                    <ElButton text class="delete-button" :disabled="busyId !== null || dialogOpen" @click="remove(metric)">删除</ElButton>
                  </div></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
    <MetricDialog v-model="dialogOpen" :run-id="runId" :metric="selected" @saved="loadMetrics" />
  </div>
</template>

<style scoped>
.run-metrics { border-top: 1px solid #f0f1f4; margin-top: 6px; padding-top: 4px; min-width: 0; }
.metrics-toggle.el-button { color: #2861af; padding-left: 0; font-size: 11px; }
.metrics-panel { padding: 10px 0 8px; min-width: 0; }
.metrics-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.metrics-toolbar h6 { margin: 0; font-size: 12px; font-weight: 600; }
.metrics-toolbar-actions { display: flex; gap: 4px; }
.metrics-toolbar-actions .el-button { padding: 6px; height: 30px; font-size: 11px; }
.metrics-toolbar-actions .metrics-add { color: #2861af; }
.metrics-toolbar-actions .el-button + .el-button { margin-left: 0; }
.metrics-toolbar-actions .button-plus { font-size: 16px; margin-right: 4px; }
.metrics-intro { margin: 6px 0 14px; font-size: 11px; color: #9197a0; line-height: 1.8; }
.metrics-content { display: grid; gap: 18px; min-width: 0; }
.metrics-subheading { margin: 0 0 10px; color: #858b95; font-size: 11px; font-weight: 550; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(130px, 100%), 1fr)); gap: 8px; margin: 0; }
.metric-tile { background: #f7f8fa; border: 1px solid #eceef2; border-radius: 8px; padding: 12px; min-width: 0; }
.metric-tile dt { color: #858b95; font-size: 11px; line-height: 1.6; overflow-wrap: anywhere; }
.metric-tile dd { margin-top: 7px; color: #3c536e; font-size: 16px; font-weight: 550; line-height: 1.6; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.metric-series { background: #f7f8fa; border: 1px solid #eceef2; border-radius: 8px; padding: 10px 12px; min-width: 0; }
.metric-series + .metric-series { margin-top: 10px; }
.metric-series table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 11px; text-align: left; }
.metric-series caption { text-align: left; color: #3c536e; font-weight: 550; padding: 2px 0 12px; overflow-wrap: anywhere; }
.metric-series th, .metric-series td { padding: 8px 4px; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.metric-series th { font-weight: 400; color: #9197a0; }
.metric-series td { border-top: 1px solid #eceef2; color: #626b78; }
.metric-series th:last-child, .metric-series td:last-child { text-align: right; }
.metric-operation { width: 80px; }
.metric-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 2px; margin-top: 6px; }
.metric-actions .el-button { padding: 4px; height: 26px; font-size: 11px; margin: 0; }
.metric-operation .metric-actions { margin-top: 0; }
.metrics-loading { padding: 12px 4px; }
.metrics-state { padding: 20px 8px; text-align: center; }
.metrics-state strong { font-size: 12px; font-weight: 550; }
.metrics-state p { margin: 10px 0 14px; font-size: 11px; line-height: 1.8; color: #858b95; overflow-wrap: anywhere; }
</style>

