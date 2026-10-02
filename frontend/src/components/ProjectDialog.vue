<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createProject, updateProject } from '../api/project'
import { buildProjectChanges, getStatusOptions } from '../utils/project'
import { getStatusLabel } from '../utils/status'

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
    ElMessage.info('没有需要保存的修改')
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
  <ElDialog :model-value="modelValue" :title="editing ? '编辑项目' : '新建项目'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? '更新项目信息，记录研究进展。' : '创建项目，开始记录新的研究工作。' }}</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="项目名称" prop="name">
        <ElInput v-model="form.name" maxlength="100" placeholder="请输入研究项目名称" autofocus />
      </ElFormItem>
      <ElFormItem label="项目描述" prop="description">
        <ElInput v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="简要描述研究目标或内容（选填）" />
      </ElFormItem>
      <ElFormItem v-if="editing" label="状态" prop="status">
        <ElSelect v-model="form.status" placeholder="请选择状态"><ElOption v-for="status in statusOptions" :key="status" :label="getStatusLabel(status)" :value="status" /></ElSelect>
        <p class="field-hint">状态选项来自当前项目已使用的状态。</p>
      </ElFormItem>
    </ElForm>
    <template #footer><ElButton :disabled="saving" @click="close">取消</ElButton><ElButton type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '创建项目' }}</ElButton></template>
  </ElDialog>
</template>
