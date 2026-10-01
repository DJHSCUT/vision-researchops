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
  catch (failure) { error.value = failure.userMessage || 'Unable to load projects.' }
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
    await ElMessageBox.confirm(`Delete “${project.name}”? This cannot be undone.`, 'Delete project', {
      confirmButtonText: 'Delete project', cancelButtonText: 'Cancel', type: 'warning',
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
      <div><p class="eyebrow">YOUR RESEARCH, IN FOCUS</p><h1>Projects<span class="heading-dot">.</span></h1><p class="page-subtitle">Manage and explore your research projects.</p></div>
      <ElButton type="primary" size="large" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span> New Project</ElButton>
    </div>

    <div class="overview-strip" aria-label="Project overview">
      <div><span class="metric-label">Total projects</span><strong>{{ loading || error ? '—' : projects.length }}</strong></div>
      <div><span class="metric-label">Active research</span><strong>{{ loading || error ? '—' : activeCount }}</strong></div>
      <div class="last-activity"><span class="metric-label">Last activity</span><strong>{{ loading || error ? '—' : formatDate(latestUpdate) }}</strong></div>
    </div>

    <div class="collection-toolbar"><div><h2>Project collection</h2><span class="muted">{{ error ? 'Connection needs attention' : 'A clear view of your work.' }}</span></div><div class="toolbar-actions"><ElInput v-model="query" placeholder="Search projects" clearable aria-label="Search projects" class="search-input" /><ElButton :loading="loading" @click="loadProjects">Refresh</ElButton></div></div>

    <div v-if="loading" class="project-grid" aria-live="polite" aria-label="Loading projects"><div v-for="i in 3" :key="i" class="project-card"><ElSkeleton :rows="4" animated /></div></div>
    <div v-else-if="error" class="state-panel" role="alert"><span class="state-symbol" aria-hidden="true">!</span><h2>Projects are unavailable</h2><p>{{ error }}</p><ElButton type="primary" @click="loadProjects">Try again</ElButton></div>
    <div v-else-if="!projects.length" class="state-panel"><span class="state-symbol" aria-hidden="true">+</span><p class="eyebrow">A NEW BEGINNING</p><h2>Your next discovery starts here.</h2><p>Create your first project to bring your research into focus.</p><ElButton type="primary" @click="openCreate">New Project</ElButton></div>
    <div v-else-if="!filtered.length" class="state-panel"><h2>No matching projects</h2><p>Try another name or description.</p><ElButton @click="query = ''">Clear search</ElButton></div>
    <div v-else class="project-grid">
      <article v-for="project in filtered" :key="project.id" class="project-card">
        <div class="card-topline"><span class="project-monogram" aria-hidden="true">{{ project.name?.slice(0, 1).toUpperCase() }}</span><StatusBadge :status="project.status" /></div>
        <h3><RouterLink :to="`/projects/${project.id}`">{{ project.name }}</RouterLink></h3>
        <p class="project-description">{{ project.description || 'No description yet.' }}</p>
        <dl class="card-dates"><div><dt>Created</dt><dd>{{ formatDate(project.createdAt) }}</dd></div><div><dt>Updated</dt><dd>{{ formatDate(project.updatedAt) }}</dd></div></dl>
        <div class="card-footer"><RouterLink :to="`/projects/${project.id}`" class="view-link">View project <span aria-hidden="true">↗</span></RouterLink><div><ElButton text :disabled="busyId !== null" @click="openEdit(project)">Edit</ElButton><ElButton text class="delete-button" :disabled="busyId !== null" @click="remove(project)">Delete</ElButton></div></div>
      </article>
    </div>
    <p v-if="!loading && !error && projects.length" class="collection-count">{{ filtered.length }} of {{ projects.length }} projects</p>
    <ProjectDialog v-model="dialogOpen" :project="selected" :statuses="statuses" @saved="loadProjects" />
  </section>
</template>
