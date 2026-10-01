<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, updateProject } from '../api/project'
import { buildProjectChanges, getStatusOptions } from '../utils/project'

const props = defineProps({
  modelValue: Boolean,
  project: { type: Object, default: null },
  statuses: { type: Array, default: () => ['ACTIVE'] },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const form = reactive({ name: '', description: '', status: 'ACTIVE' })
const editing = computed(() => !!props.project)
const statusOptions = computed(() => [...new Set([...props.statuses, ...getStatusOptions(props.project ? [props.project] : [])])])
const rules = {
  name: [
    { validator: (_rule, value, callback) => callback(value?.trim() ? undefined : new Error('项目名称不能为空')), trigger: 'blur' },
    { max: 100, message: '项目名称不能超过100个字符', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '项目描述不能超过500个字符', trigger: 'blur' }],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    name: props.project?.name ?? '',
    description: props.project?.description ?? '',
    status: props.project?.status ?? 'ACTIVE',
  })
  await nextTick()
  formRef.value?.clearValidate()
})

function close() {
  if (!saving.value) emit('update:modelValue', false)
}

async function save() {
  if (saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const fields = editing.value
    ? buildProjectChanges(props.project, form)
    : { name: form.name, description: form.description }
  if (!Object.keys(fields).length) {
    ElMessage.info('No changes to save')
    return
  }
  saving.value = true
  try {
    if (editing.value) await updateProject(props.project.id, fields)
    else await createProject(fields)
    ElMessage.success(editing.value ? '项目更新成功' : '项目创建成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    // The shared request layer already displays the backend message. Keep the form for retry.
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog :model-value="modelValue" :title="editing ? 'Edit project' : 'New project'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? 'Keep your research context up to date.' : 'Give your next research idea a place to grow.' }}</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="Project name" prop="name">
        <ElInput v-model="form.name" maxlength="100" placeholder="Name your research project" autofocus />
      </ElFormItem>
      <ElFormItem label="Description" prop="description">
        <ElInput v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="What are you working towards?" />
      </ElFormItem>
      <ElFormItem v-if="editing" label="Status" prop="status">
        <ElSelect v-model="form.status" placeholder="Select status"><ElOption v-for="status in statusOptions" :key="status" :label="status" :value="status" /></ElSelect>
        <p class="field-hint">Options reflect states defined or already used by the backend.</p>
      </ElFormItem>
    </ElForm>
    <template #footer><ElButton :disabled="saving" @click="close">Cancel</ElButton><ElButton type="primary" :loading="saving" @click="save">{{ editing ? 'Save changes' : 'Create project' }}</ElButton></template>
  </ElDialog>
</template>
