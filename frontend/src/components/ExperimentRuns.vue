<script setup>
import { onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteRun, getRun, getTaskRuns } from '../api/run'
import { formatDate } from '../utils/project'
import { getRunStatusLabel } from '../utils/run'
import StatusBadge from './StatusBadge.vue'
import RunDialog from './RunDialog.vue'
import ExperimentLogs from './ExperimentLogs.vue'
import ExperimentMetrics from './ExperimentMetrics.vue'
import ExperimentArtifacts from './ExperimentArtifacts.vue'
import BusinessCodeBadge from './BusinessCodeBadge.vue'

const props = defineProps({ taskId: { type: [String, Number], required: true } })
const expanded = ref(false)
const runs = ref([])
const count = ref(null)
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const selected = ref(null)
const busyId = ref(null)
let loadSequence = 0
let active = true

async function loadRuns() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const result = await getTaskRuns(props.taskId)
    if (!active || sequence !== loadSequence) return
    runs.value = result
    count.value = result.length
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.userMessage || '无法加载运行记录。'
  } finally {
    if (active && sequence === loadSequence) loading.value = false
  }
}

function toggleRuns() {
  expanded.value = !expanded.value
  // 初次展开才查询；重新展开时刷新，避免显示其他客户端修改前的数据。
  if (expanded.value) loadRuns()
}

function openCreate() {
  selected.value = null
  dialogOpen.value = true
}

async function openEdit(run) {
  if (busyId.value !== null) return
  busyId.value = run.id
  try {
    const latest = await getRun(run.id)
    if (!active) return
    selected.value = latest
    dialogOpen.value = true
  } catch { /* 请求层显示后端错误。 */ }
  finally { busyId.value = null }
}

async function remove(run) {
  if (busyId.value !== null) return
  busyId.value = run.id
  try {
    await ElMessageBox.confirm(`确定删除实验运行“${run.runName}”吗？此操作无法撤销。`, '删除运行', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
      confirmButtonClass: 'danger-confirm',
    })
    if (!active) return
    await deleteRun(run.id)
    if (!active) return
    ElMessage.success('实验运行已删除')
    await loadRuns()
  } catch { /* 取消不发请求；请求失败由请求层显示。 */ }
  finally { busyId.value = null }
}

onUnmounted(() => { active = false; loadSequence++ })
</script>

<template>
  <div class="task-runs">
    <ElButton text class="runs-toggle" :aria-expanded="expanded" :aria-controls="`task-runs-${taskId}`"
      :disabled="busyId !== null || dialogOpen" @click="toggleRuns">
      {{ expanded ? '收起运行记录' : '查看运行记录' }}<span v-if="count !== null">（{{ count }}）</span>
      <span class="runs-chevron" aria-hidden="true">{{ expanded ? '▴' : '▾' }}</span>
    </ElButton>
    <section v-show="expanded" :id="`task-runs-${taskId}`" class="runs-panel" :aria-labelledby="`runs-heading-${taskId}`">
      <div class="runs-toolbar">
        <h4 :id="`runs-heading-${taskId}`">运行记录</h4>
        <div class="runs-toolbar-actions">
          <ElButton text :loading="loading" :disabled="busyId !== null" @click="loadRuns">刷新</ElButton>
          <ElButton type="primary" :disabled="busyId !== null" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span>新建运行</ElButton>
        </div>
      </div>
      <div v-if="loading" class="runs-loading" aria-live="polite" aria-label="正在加载运行记录"><ElSkeleton :rows="3" animated /></div>
      <div v-else-if="error" class="runs-state" role="alert">
        <h4>运行记录加载失败</h4><p>{{ error }}</p><ElButton @click="loadRuns">重试</ElButton>
      </div>
      <div v-else-if="!runs.length" class="runs-state">
        <h4>暂无运行记录</h4><p>创建第一次实验运行以开始记录实验执行情况。</p><ElButton type="primary" @click="openCreate">新建运行</ElButton>
      </div>
      <div v-else class="runs-list">
        <article v-for="run in runs" :key="run.id" class="run-card">
          <div class="run-card-heading"><h5><BusinessCodeBadge :code="run.runCode" />{{ run.runName }}</h5><StatusBadge :status="run.status" :label="getRunStatusLabel(run.status)" /></div>
          <dl class="run-dates">
            <div><dt>开始时间</dt><dd>{{ formatDate(run.startedAt) }}</dd></div>
            <div><dt>结束时间</dt><dd>{{ formatDate(run.finishedAt) }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatDate(run.createdAt) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatDate(run.updatedAt) }}</dd></div>
          </dl>
          <div v-if="run.status === 'FAILED' && run.errorMessage?.trim()" class="run-error"><span>失败原因</span><p>{{ run.errorMessage }}</p></div>
          <div class="run-card-footer">
            <ElButton text :disabled="busyId !== null" @click="openEdit(run)">编辑</ElButton>
            <ElButton text class="delete-button" :disabled="busyId !== null" @click="remove(run)">删除</ElButton>
          </div>
          <ExperimentMetrics :run-id="run.id" />
          <ExperimentArtifacts :run-id="run.id" />
          <ExperimentLogs :run-id="run.id" />
        </article>
      </div>
    </section>
    <RunDialog v-model="dialogOpen" :task-id="taskId" :run="selected" @saved="loadRuns" />
  </div>
</template>
