<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createTask, updateTask } from '../api/task'
import { buildTaskChanges, taskStatuses } from '../utils/task'
import { getStatusLabel } from '../utils/status'

const props = defineProps({
  modelValue: Boolean,
  projectId: { type: [String, Number], required: true },
  task: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const form = reactive({ name: '', description: '', status: 'TODO' })
const editing = computed(() => !!props.task)
const statusOptions = computed(() => [...new Set([
  ...taskStatuses, ...(props.task?.status ? [props.task.status] : []),
])])
const rules = {
  name: [
    { validator: (_rule, value, callback) => callback(value?.trim() ? undefined : new Error('实验任务名称不能为空')), trigger: 'blur' },
    { max: 150, message: '实验任务名称不能超过150个字符', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '实验任务描述不能超过500个字符', trigger: 'blur' }],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    name: props.task?.name ?? '',
    description: props.task?.description ?? '',
    status: props.task?.status ?? 'TODO',
  })
  await nextTick()
  formRef.value?.clearValidate()
})

function close() {
  if (!saving.value) emit('update:modelValue', false)
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    const fields = editing.value
      ? buildTaskChanges(props.task, form)
      : { name: form.name, description: form.description }
    if (!Object.keys(fields).length) {
      ElMessage.info('没有需要保存的修改')
      return
    }
    if (editing.value) await updateTask(props.task.id, fields)
    else await createTask(props.projectId, fields)
    ElMessage.success(editing.value ? '实验任务更新成功' : '实验任务创建成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    // 请求层显示后端 message，保留输入以便重试。
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog :model-value="modelValue" :title="editing ? '编辑实验任务' : '新建实验任务'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? '更新任务信息，记录研究进展。' : '为当前研究项目添加一项实验任务。' }}</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="任务名称" prop="name">
        <ElInput v-model="form.name" maxlength="150" placeholder="请输入实验任务名称" autofocus />
      </ElFormItem>
      <ElFormItem label="任务描述" prop="description">
        <ElInput v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="简要描述实验目标或内容（选填）" />
      </ElFormItem>
      <ElFormItem v-if="editing" label="状态" prop="status">
        <ElSelect v-model="form.status" placeholder="请选择状态">
          <ElOption v-for="status in statusOptions" :key="status" :label="getStatusLabel(status)" :value="status" />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '创建实验任务' }}</ElButton>
    </template>
  </ElDialog>
</template>
