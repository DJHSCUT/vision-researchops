<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteProject, getProject, getProjects } from '../api/project'
import { formatDate, getStatusOptions } from '../utils/project'
import ProjectDialog from '../components/ProjectDialog.vue'
import StatusBadge from '../components/StatusBadge.vue'

const projects = ref([])
const loading = ref(true)
const error = ref('')
const query = ref('')
const dialogOpen = ref(false)
const selected = ref(null)
const busyId = ref(null)
const statuses = computed(() => getStatusOptions(projects.value))
const filtered = computed(() => projects.value.filter((project) =>
  `${project.name} ${project.description ?? ''}`.toLowerCase().includes(query.value.toLowerCase().trim()),
))
const activeCount = computed(() => projects.value.filter((project) => project.status === 'ACTIVE').length)
const latestUpdate = computed(() => projects.value.map((p) => p.updatedAt).filter(Boolean).sort().at(-1))

async function loadProjects() {
  loading.value = true
  error.value = ''
  try { projects.value = await getProjects() }
  catch (failure) { error.value = failure.userMessage || '无法加载研究项目。' }
  finally { loading.value = false }
}

function openCreate() { selected.value = null; dialogOpen.value = true }

async function openEdit(project) {
  busyId.value = project.id
  try {
    selected.value = await getProject(project.id)
    dialogOpen.value = true
  } catch { /* Shared request layer displays the error. */ }
  finally { busyId.value = null }
}

async function remove(project) {
  try {
    await ElMessageBox.confirm(`确定删除研究项目“${project.name}”吗？此操作无法撤销。`, '删除项目', {
      confirmButtonText: '删除项目', cancelButtonText: '取消', type: 'warning',
      confirmButtonClass: 'danger-confirm',
    })
  } catch { return }
  busyId.value = project.id
  try {
    await deleteProject(project.id)
    ElMessage.success('项目删除成功')
    await loadProjects()
  } catch { /* Shared request layer displays the error. */ }
  finally { busyId.value = null }
}

onMounted(loadProjects)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><p class="eyebrow">专注研究，有序推进</p><h1>研究项目<span class="heading-dot">.</span></h1><p class="page-subtitle">管理研究项目，记录每一步进展。</p></div>
      <ElButton type="primary" size="large" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span> 新建项目</ElButton>
    </div>

    <div class="overview-strip" aria-label="项目概览">
      <div><span class="metric-label">项目总数</span><strong>{{ loading || error ? '—' : projects.length }}</strong></div>
      <div><span class="metric-label">进行中的项目</span><strong>{{ loading || error ? '—' : activeCount }}</strong></div>
      <div class="last-activity"><span class="metric-label">最近更新</span><strong>{{ loading || error ? '—' : formatDate(latestUpdate) }}</strong></div>
    </div>

    <div class="collection-toolbar"><div><h2>项目列表</h2><span class="muted">{{ error ? '连接异常，请检查服务' : '集中查看与管理研究工作。' }}</span></div><div class="toolbar-actions"><ElInput v-model="query" placeholder="搜索项目" clearable aria-label="搜索项目" class="search-input" /><ElButton :loading="loading" @click="loadProjects">刷新</ElButton></div></div>

    <div v-if="loading" class="project-grid" aria-live="polite" aria-label="正在加载项目"><div v-for="i in 3" :key="i" class="project-card"><ElSkeleton :rows="4" animated /></div></div>
    <div v-else-if="error" class="state-panel" role="alert"><span class="state-symbol" aria-hidden="true">!</span><h2>无法加载项目</h2><p>{{ error }}</p><ElButton type="primary" @click="loadProjects">重试</ElButton></div>
    <div v-else-if="!projects.length" class="state-panel"><span class="state-symbol" aria-hidden="true">+</span><p class="eyebrow">开始新的研究</p><h2>暂无研究项目</h2><p>创建第一个项目，开始记录研究工作。</p><ElButton type="primary" @click="openCreate">新建项目</ElButton></div>
    <div v-else-if="!filtered.length" class="state-panel"><h2>未找到匹配的项目</h2><p>请尝试其他名称或描述。</p><ElButton @click="query = ''">清空搜索</ElButton></div>
    <div v-else class="project-grid">
      <article v-for="project in filtered" :key="project.id" class="project-card">
        <div class="card-topline"><span class="project-monogram" aria-hidden="true">{{ project.name?.slice(0, 1).toUpperCase() }}</span><StatusBadge :status="project.status" /></div>
        <h3><RouterLink :to="`/projects/${project.id}`">{{ project.name }}</RouterLink></h3>
        <p class="project-description">{{ project.description || '暂无项目描述' }}</p>
        <dl class="card-dates"><div><dt>创建时间</dt><dd>{{ formatDate(project.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatDate(project.updatedAt) }}</dd></div></dl>
        <div class="card-footer"><RouterLink :to="`/projects/${project.id}`" class="view-link">查看项目 <span aria-hidden="true">↗</span></RouterLink><div><ElButton text :disabled="busyId !== null" @click="openEdit(project)">编辑</ElButton><ElButton text class="delete-button" :disabled="busyId !== null" @click="remove(project)">删除</ElButton></div></div>
      </article>
    </div>
    <p v-if="!loading && !error && projects.length" class="collection-count">显示 {{ filtered.length }} / {{ projects.length }} 个项目</p>
    <ProjectDialog v-model="dialogOpen" :project="selected" :statuses="statuses" @saved="loadProjects" />
  </section>
</template>
