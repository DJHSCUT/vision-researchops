<script setup>
import { onUnmounted, ref } from 'vue'
import { getRunLogs } from '../api/log'
import { formatDate } from '../utils/project'
import { formatLogTime, getLogLevelClass, getLogLevelLabel, logLevels } from '../utils/log'
import LogDialog from './LogDialog.vue'

const props = defineProps({ runId: { type: [String, Number], required: true } })
const expanded = ref(false)
const logs = ref([])
const totalCount = ref(null)
const level = ref('')
const loading = ref(false)
const error = ref('')
const dialogOpen = ref(false)
let loadSequence = 0
let active = true

async function loadLogs() {
  const sequence = ++loadSequence
  const requestedLevel = level.value
  loading.value = true
  error.value = ''
  try {
    const result = await getRunLogs(props.runId, requestedLevel)
    if (!active || sequence !== loadSequence) return
    logs.value = result
    // 筛选结果数量不覆盖全部日志数量。
    if (!requestedLevel) totalCount.value = result.length
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.userMessage || '无法加载实验日志。'
  } finally {
    if (active && sequence === loadSequence) loading.value = false
  }
}

function toggleLogs() {
  expanded.value = !expanded.value
  if (expanded.value) loadLogs()
}

function selectLevel(value) {
  if (level.value === value) return
  level.value = value
  loadLogs()
}

function onSaved() {
  // 添加后显示全部日志，使新记录可见，并更新总数。
  level.value = ''
  loadLogs()
}

onUnmounted(() => { active = false; loadSequence++ })
</script>

<template>
  <div class="run-logs">
    <ElButton text class="logs-toggle" :aria-expanded="expanded" :aria-controls="`run-logs-${runId}`"
      :disabled="dialogOpen" @click="toggleLogs">
      {{ expanded ? '收起日志' : '查看日志' }}<span v-if="totalCount !== null">（{{ totalCount }}）</span>
      <span class="runs-chevron" aria-hidden="true">{{ expanded ? '▴' : '▾' }}</span>
    </ElButton>
    <section v-show="expanded" :id="`run-logs-${runId}`" class="logs-panel" :aria-labelledby="`logs-heading-${runId}`">
      <div class="logs-toolbar">
        <h6 :id="`logs-heading-${runId}`">运行日志</h6>
        <div class="logs-toolbar-actions">
          <ElButton text :loading="loading" @click="loadLogs">刷新</ElButton>
          <ElButton text class="logs-add" @click="dialogOpen = true"><span class="button-plus" aria-hidden="true">+</span>添加日志</ElButton>
        </div>
      </div>
      <div class="log-filters" role="group" aria-label="日志级别筛选">
        <ElButton text :class="{ 'is-selected': level === '' }" :aria-pressed="level === ''" @click="selectLevel('')">全部</ElButton>
        <ElButton v-for="option in logLevels" :key="option" text :class="{ 'is-selected': level === option }"
          :aria-pressed="level === option" @click="selectLevel(option)">{{ getLogLevelLabel(option) }}</ElButton>
      </div>
      <div v-if="loading" class="logs-loading" aria-live="polite" aria-label="正在加载实验日志"><ElSkeleton :rows="2" animated /></div>
      <div v-else-if="error" class="logs-state" role="alert">
        <strong>实验日志加载失败</strong><p>{{ error }}</p><ElButton @click="loadLogs">重试</ElButton>
      </div>
      <div v-else-if="!logs.length" class="logs-state">
        <strong>{{ level ? '暂无该级别的实验日志' : '暂无实验日志' }}</strong>
        <p>记录运行过程中的关键信息、警告和错误，便于后续分析。</p>
        <ElButton @click="dialogOpen = true">{{ level ? '添加日志' : '添加第一条日志' }}</ElButton>
      </div>
      <ul v-else class="log-lines" aria-label="实验日志列表">
        <li v-for="log in logs" :key="log.id" class="log-line">
          <time class="log-time" :datetime="log.createdAt" :title="formatDate(log.createdAt)">{{ formatLogTime(log.createdAt) }}</time>
          <span class="log-level" :class="getLogLevelClass(log.level)">{{ getLogLevelLabel(log.level) }}</span>
          <p class="log-content">{{ log.content }}</p>
        </li>
      </ul>
    </section>
    <LogDialog v-model="dialogOpen" :run-id="runId" @saved="onSaved" />
  </div>
</template>
