<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteTask, getProjectTasks, getTask } from '../api/task'
import { formatDate } from '../utils/project'
import StatusBadge from './StatusBadge.vue'
import TaskDialog from './TaskDialog.vue'
import ExperimentRuns from './ExperimentRuns.vue'
import BusinessCodeBadge from './BusinessCodeBadge.vue'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const tasks = ref([])
const loading = ref(true)
const error = ref('')
const dialogOpen = ref(false)
const selected = ref(null)
const busyId = ref(null)
let loadSequence = 0
let active = true

async function loadTasks() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const result = await getProjectTasks(props.projectId)
    if (active && sequence === loadSequence) tasks.value = result
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.userMessage || '无法加载实验任务。'
  } finally {
    if (active && sequence === loadSequence) loading.value = false
  }
}

function openCreate() {
  selected.value = null
  dialogOpen.value = true
}

async function openEdit(task) {
  if (busyId.value !== null) return
  busyId.value = task.id
  try {
    const latest = await getTask(task.id)
    if (!active) return
    selected.value = latest
    dialogOpen.value = true
  } catch { /* 请求层显示后端错误。 */ }
  finally { busyId.value = null }
}

async function remove(task) {
  if (busyId.value !== null) return
  busyId.value = task.id
  try {
    await ElMessageBox.confirm(`确定删除实验任务“${task.name}”吗？此操作无法撤销。`, '删除实验任务', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
      confirmButtonClass: 'danger-confirm',
    })
    if (!active) return
    await deleteTask(task.id)
    ElMessage.success('实验任务已删除')
    if (active) await loadTasks()
  } catch { /* 取消不发请求；请求失败由请求层显示。 */ }
  finally { busyId.value = null }
}

watch(() => props.projectId, () => {
  tasks.value = []
  dialogOpen.value = false
  loadTasks()
}, { immediate: true })
onUnmounted(() => { active = false; loadSequence++ })
</script>

<template>
  <section class="tasks-section" aria-labelledby="tasks-heading">
    <div class="collection-toolbar task-toolbar">
      <div><p class="eyebrow">03 / 实验管理</p><h2 id="tasks-heading">实验任务</h2><span class="muted">管理当前研究项目下的实验任务。</span></div>
      <div class="toolbar-actions">
        <ElButton :loading="loading" :disabled="busyId !== null" @click="loadTasks">刷新</ElButton>
        <ElButton type="primary" :disabled="busyId !== null" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span>新建实验任务</ElButton>
      </div>
    </div>
    <div v-if="loading" class="task-grid" aria-live="polite" aria-label="正在加载实验任务">
      <div v-for="i in 2" :key="i" class="task-card"><ElSkeleton :rows="4" animated /></div>
    </div>
    <div v-else-if="error" class="state-panel" role="alert">
      <span class="state-symbol" aria-hidden="true">!</span><h2>无法加载实验任务</h2><p>{{ error }}</p><ElButton type="primary" @click="loadTasks">重试</ElButton>
    </div>
    <div v-else-if="!tasks.length" class="state-panel">
      <span class="state-symbol" aria-hidden="true">+</span><h2>暂无实验任务</h2><p>创建第一个实验任务以开始记录研究工作。</p><ElButton type="primary" @click="openCreate">新建实验任务</ElButton>
    </div>
    <div v-else class="task-grid">
      <article v-for="task in tasks" :key="task.id" class="task-card">
        <div class="task-card-heading"><h3><BusinessCodeBadge :code="task.taskCode" />{{ task.name }}</h3><StatusBadge :status="task.status" /></div>
        <p class="task-description">{{ task.description || '暂无任务描述' }}</p>
        <dl class="card-dates"><div><dt>创建时间</dt><dd>{{ formatDate(task.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatDate(task.updatedAt) }}</dd></div></dl>
        <div class="card-footer task-card-footer"><span class="muted">操作</span><div>
          <ElButton text :disabled="busyId !== null" @click="openEdit(task)">编辑</ElButton>
          <ElButton text class="delete-button" :disabled="busyId !== null" @click="remove(task)">删除</ElButton>
        </div></div>
        <ExperimentRuns :task-id="task.id" />
      </article>
    </div>
    <p v-if="!loading && !error && tasks.length" class="collection-count">共 {{ tasks.length }} 项实验任务</p>
    <TaskDialog v-model="dialogOpen" :project-id="projectId" :task="selected" @saved="loadTasks" />
  </section>
</template>
