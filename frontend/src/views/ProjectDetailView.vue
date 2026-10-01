<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getProject } from '../api/project'
import { formatDate } from '../utils/project'
import StatusBadge from '../components/StatusBadge.vue'
import ProjectDialog from '../components/ProjectDialog.vue'

const route = useRoute()
const project = ref(null)
const loading = ref(true)
const error = ref('')
const status = ref(null)
const dialogOpen = ref(false)
let loadSequence = 0

async function loadProject() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  status.value = null
  project.value = null
  try {
    const result = await getProject(route.params.id)
    if (sequence === loadSequence) project.value = result
  } catch (failure) {
    if (sequence === loadSequence) {
      error.value = failure.userMessage || 'Unable to load project.'
      status.value = failure.response?.status
    }
  } finally { if (sequence === loadSequence) loading.value = false }
}

watch(() => route.params.id, () => { dialogOpen.value = false; loadProject() }, { immediate: true })
</script>

<template>
  <section>
    <RouterLink to="/projects" class="back-link">← All projects</RouterLink>
    <div v-if="loading" class="detail-panel loading-panel" aria-label="Loading project"><ElSkeleton :rows="7" animated /></div>
    <div v-else-if="error" class="state-panel" role="alert"><span class="state-symbol">{{ status === 404 ? '404' : '!' }}</span><h1 class="state-title">{{ status === 404 ? 'Project not found' : 'Project is unavailable' }}</h1><p>{{ error }}</p><ElButton v-if="status !== 404" type="primary" @click="loadProject">Try again</ElButton><RouterLink v-else to="/projects" class="view-link">Back to projects →</RouterLink></div>
    <template v-else-if="project">
      <div class="page-heading detail-heading"><div><p class="eyebrow">RESEARCH PROJECT</p><h1>{{ project.name }}</h1><StatusBadge :status="project.status" /></div><ElButton size="large" @click="dialogOpen = true">Edit project</ElButton></div>
      <div class="detail-grid">
        <section class="detail-panel"><p class="eyebrow">01 / CONTEXT</p><h2>Overview</h2><div class="detail-field"><span class="field-label">Project name</span><h3>{{ project.name }}</h3></div><div class="detail-field"><span class="field-label">Description</span><p class="full-description">{{ project.description || 'No description has been added to this project.' }}</p></div></section>
        <section class="detail-panel metadata-panel"><p class="eyebrow">02 / AT A GLANCE</p><h2>Metadata</h2><dl class="metadata"><div><dt>Status</dt><dd><StatusBadge :status="project.status" /></dd></div><div><dt>Created</dt><dd>{{ formatDate(project.createdAt) }}</dd></div><div><dt>Last updated</dt><dd>{{ formatDate(project.updatedAt) }}</dd></div><div><dt>Project ID</dt><dd class="project-id">{{ project.id }}</dd></div></dl></section>
      </div>
      <ProjectDialog v-model="dialogOpen" :project="project" @saved="loadProject" />
    </template>
  </section>
</template>
