<script setup>
import { onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteArtifact, getArtifact, getArtifacts } from '../api/artifact'
import { artifactTypes, formatFileSize, getArtifactTypeLabel } from '../utils/artifact'
import { formatDate } from '../utils/project'
import ArtifactDialog from './ArtifactDialog.vue'

const props = defineProps({ runId: { type: [String, Number], required: true } })
const expanded = ref(false)
const artifacts = ref([])
const totalCount = ref(null)
const type = ref('')
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
const selected = ref(null)
const busyId = ref(null)
let loadSequence = 0
let active = true

async function loadArtifacts() {
  const sequence = ++loadSequence
  const requestedType = type.value
  loading.value = true
  error.value = ''
  try {
    const result = await getArtifacts(props.runId, requestedType)
    if (!active || sequence !== loadSequence) return
    artifacts.value = result
    if (!requestedType) totalCount.value = result.length
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.userMessage || '无法加载实验产物。'
  } finally {
    if (active && sequence === loadSequence) loading.value = false
  }
}

function toggleArtifacts() {
  expanded.value = !expanded.value
  if (expanded.value) loadArtifacts()
}

function selectType(value) {
  if (type.value === value) return
  type.value = value
  loadArtifacts()
}

function openCreate() {
  selected.value = null
  dialogOpen.value = true
}

async function openEdit(artifact) {
  if (busyId.value !== null) return
  busyId.value = artifact.id
  try {
    const latest = await getArtifact(artifact.id)
    if (!active) return
    selected.value = latest
    dialogOpen.value = true
  } catch { /* 请求层显示后端 message。 */ }
  finally { busyId.value = null }
}

function onSaved() {
  // 新建或更换类型后显示全部，确保新记录可见，并刷新总数。
  type.value = ''
  loadArtifacts()
}

async function remove(artifact) {
  if (busyId.value !== null) return
  busyId.value = artifact.id
  try {
    await ElMessageBox.confirm('确定删除该实验产物吗？此操作只删除系统中的产物记录，不会删除实际文件。', '删除实验产物', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
      confirmButtonClass: 'danger-confirm',
    })
    if (!active) return
    await deleteArtifact(artifact.id)
    if (!active) return
    ElMessage.success('实验产物删除成功')
    // 删除后查询全部，以更新折叠标题总数。
    type.value = ''
    await loadArtifacts()
  } catch { /* 取消不发请求；失败时由请求层显示后端消息。 */ }
  finally { busyId.value = null }
}

onUnmounted(() => { active = false; loadSequence++ })
</script>

<template>
  <div class="run-artifacts">
    <ElButton text class="artifacts-toggle" :aria-expanded="expanded" :aria-controls="`run-artifacts-${runId}`"
      :disabled="dialogOpen || busyId !== null" @click="toggleArtifacts">
      实验产物 · {{ expanded ? '收起' : '展开' }}<span v-if="totalCount !== null">（{{ totalCount }}）</span>
      <span class="runs-chevron" aria-hidden="true">{{ expanded ? '▴' : '▾' }}</span>
    </ElButton>
    <section v-show="expanded" :id="`run-artifacts-${runId}`" class="artifacts-panel" :aria-labelledby="`artifacts-heading-${runId}`">
      <div class="artifacts-toolbar">
        <h6 :id="`artifacts-heading-${runId}`">实验产物</h6>
        <div class="artifacts-toolbar-actions">
          <ElButton text :loading="loading" :disabled="busyId !== null || dialogOpen" @click="loadArtifacts">刷新</ElButton>
          <ElButton text class="artifacts-add" :disabled="busyId !== null || dialogOpen" @click="openCreate"><span class="button-plus" aria-hidden="true">+</span>添加产物</ElButton>
        </div>
      </div>
      <div class="artifact-filters" role="group" aria-label="产物类型筛选">
        <ElButton text :class="{ 'is-selected': type === '' }" :aria-pressed="type === ''" :disabled="busyId !== null || dialogOpen" @click="selectType('')">全部</ElButton>
        <ElButton v-for="option in artifactTypes" :key="option.value" text :class="{ 'is-selected': type === option.value }"
          :aria-pressed="type === option.value" :disabled="busyId !== null || dialogOpen" @click="selectType(option.value)">{{ option.label }}</ElButton>
      </div>
      <div v-if="loading" class="artifacts-loading" aria-live="polite" aria-label="正在加载实验产物"><ElSkeleton :rows="3" animated /></div>
      <div v-else-if="error" class="artifacts-state" role="alert">
        <strong>实验产物加载失败</strong><p>{{ error }}</p><ElButton @click="loadArtifacts">重试</ElButton>
      </div>
      <div v-else-if="!artifacts.length" class="artifacts-state">
        <strong>{{ type ? '暂无该类型的实验产物' : '暂无实验产物' }}</strong>
        <p>可记录模型、点云、图片、检查点和报告等实验结果。</p><ElButton @click="openCreate">添加产物</ElButton>
      </div>
      <div v-else class="artifact-list">
        <article v-for="artifact in artifacts" :key="artifact.id" class="artifact-card">
          <div class="artifact-heading"><h6>{{ artifact.artifactName }}</h6><span class="artifact-type">{{ getArtifactTypeLabel(artifact.artifactType) }}</span></div>
          <p class="artifact-path" :title="artifact.storagePath"><span>存储路径</span>{{ artifact.storagePath }}</p>
          <p class="artifact-description">{{ artifact.description || '暂无描述' }}</p>
          <div class="artifact-metadata"><span>文件大小：{{ formatFileSize(artifact.fileSizeBytes) }}</span><span>创建：{{ formatDate(artifact.createdAt) }}</span><span>更新：{{ formatDate(artifact.updatedAt) }}</span></div>
          <div class="artifact-actions">
            <ElButton text :disabled="busyId !== null || dialogOpen" @click="openEdit(artifact)">编辑</ElButton>
            <ElButton text class="delete-button" :disabled="busyId !== null || dialogOpen" @click="remove(artifact)">删除</ElButton>
          </div>
        </article>
      </div>
    </section>
    <ArtifactDialog v-model="dialogOpen" :run-id="runId" :artifact="selected" @saved="onSaved" />
  </div>
</template>

<style scoped>
.run-artifacts { border-top: 1px solid #f0f1f4; margin-top: 6px; padding-top: 4px; min-width: 0; }
.artifacts-toggle.el-button { color: #2861af; padding-left: 0; font-size: 11px; }
.artifacts-panel { padding: 10px 0 8px; min-width: 0; }
.artifacts-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
.artifacts-toolbar h6 { margin: 0; font-size: 12px; font-weight: 600; }
.artifacts-toolbar-actions { display: flex; gap: 4px; }
.artifacts-toolbar-actions .el-button { padding: 6px; height: 30px; font-size: 11px; }
.artifacts-toolbar-actions .artifacts-add { color: #2861af; }
.artifacts-toolbar-actions .el-button + .el-button { margin-left: 0; }
.artifacts-toolbar-actions .button-plus { font-size: 16px; margin-right: 4px; }
.artifact-filters { display: flex; flex-wrap: wrap; gap: 4px; margin: 8px 0 12px; }
.artifact-filters .el-button { height: 28px; padding: 5px 8px; font-size: 11px; color: #858b95; margin: 0; }
.artifact-filters .el-button.is-selected { color: #2861af; background: #edf3fc; }
.artifact-list { display: grid; gap: 10px; }
.artifact-card { padding: 12px; border: 1px solid #eceef2; border-radius: 8px; background: #f7f8fa; min-width: 0; }
.artifact-heading { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 8px; }
.artifact-heading h6 { margin: 0; color: #3c536e; font-size: 12px; font-weight: 550; line-height: 1.8; overflow-wrap: anywhere; }
.artifact-type { color: #2861af; font-size: 10px; background: #edf3fc; padding: 3px 7px; border-radius: 5px; }
.artifact-path { margin-top: 10px; font-family: ui-monospace, Consolas, monospace; color: #626b78; font-size: 11px; line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.artifact-path span { display: block; color: #9197a0; font-family: inherit; font-size: 10px; margin-bottom: 3px; }
.artifact-description { margin-top: 8px; color: #858b95; font-size: 11px; line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.artifact-metadata { display: flex; flex-wrap: wrap; gap: 6px 12px; margin-top: 10px; color: #9197a0; font-size: 10px; line-height: 1.8; overflow-wrap: anywhere; }
.artifact-actions { display: flex; justify-content: flex-end; gap: 2px; margin-top: 6px; }
.artifact-actions .el-button { padding: 4px; height: 26px; font-size: 11px; margin: 0; }
.artifacts-loading { padding: 12px 4px; }
.artifacts-state { padding: 20px 8px; text-align: center; }
.artifacts-state strong { font-size: 12px; font-weight: 550; }
.artifacts-state p { margin: 10px 0 14px; font-size: 11px; line-height: 1.8; color: #858b95; overflow-wrap: anywhere; }
</style>
