<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getProject } from '../api/project'
import { formatDate } from '../utils/project'
import StatusBadge from '../components/StatusBadge.vue'
import ProjectDialog from '../components/ProjectDialog.vue'
import ProjectTasks from '../components/ProjectTasks.vue'
import BusinessCodeBadge from '../components/BusinessCodeBadge.vue'

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
      error.value = failure.userMessage || '无法加载研究项目。'
      status.value = failure.response?.status
    }
  } finally { if (sequence === loadSequence) loading.value = false }
}

watch(() => route.params.id, () => { dialogOpen.value = false; loadProject() }, { immediate: true })
</script>

<template>
  <section>
    <RouterLink to="/projects" class="back-link">← 返回项目列表</RouterLink>
    <div v-if="loading" class="detail-panel loading-panel" aria-label="正在加载项目"><ElSkeleton :rows="7" animated /></div>
    <div v-else-if="error" class="state-panel" role="alert"><span class="state-symbol">{{ status === 404 ? '404' : '!' }}</span><h1 class="state-title">{{ status === 404 ? '研究项目不存在' : '无法加载项目' }}</h1><p>{{ error }}</p><ElButton v-if="status !== 404" type="primary" @click="loadProject">重试</ElButton><RouterLink v-else to="/projects" class="view-link">返回项目列表 →</RouterLink></div>
    <template v-else-if="project">
      <div class="page-heading detail-heading"><div><p class="eyebrow">研究项目</p><h1><BusinessCodeBadge :code="project.projectCode" />{{ project.name }}</h1><StatusBadge :status="project.status" /></div><ElButton size="large" @click="dialogOpen = true">编辑项目</ElButton></div>
      <div class="detail-grid">
        <section class="detail-panel"><p class="eyebrow">01 / 项目概览</p><h2>基本信息</h2><div class="detail-field"><span class="field-label">项目名称</span><h3>{{ project.name }}</h3></div><div class="detail-field"><span class="field-label">项目描述</span><p class="full-description">{{ project.description || '暂无项目描述' }}</p></div></section>
        <section class="detail-panel metadata-panel"><p class="eyebrow">02 / 项目信息</p><h2>项目记录</h2><dl class="metadata"><div><dt>状态</dt><dd><StatusBadge :status="project.status" /></dd></div><div><dt>创建时间</dt><dd>{{ formatDate(project.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatDate(project.updatedAt) }}</dd></div><div><dt>项目编号</dt><dd><BusinessCodeBadge :code="project.projectCode" /></dd></div></dl></section>
      </div>
      <ProjectTasks :key="route.params.id" :project-id="route.params.id" />
      <ProjectDialog v-model="dialogOpen" :project="project" @saved="loadProject" />
    </template>
  </section>
</template>
