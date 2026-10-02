<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createRun, updateRun } from '../api/run'
import { buildRunChanges, getRunStatusLabel, runStatuses } from '../utils/run'

const props = defineProps({
  modelValue: Boolean,
  taskId: { type: [String, Number], required: true },
  run: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const form = reactive({ runName: '', status: 'PENDING', errorMessage: '' })
const editing = computed(() => !!props.run)
let active = true
const rules = {
  runName: [
    { validator: (_rule, value, callback) => callback(value?.trim() ? undefined : new Error('实验运行名称不能为空')), trigger: 'blur' },
    { max: 150, message: '实验运行名称不能超过150个字符', trigger: 'blur' },
  ],
  errorMessage: [{ max: 1000, message: '实验运行错误信息不能超过1000个字符', trigger: 'blur' }],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    runName: props.run?.runName ?? '',
    status: props.run?.status ?? 'PENDING',
    errorMessage: props.run?.errorMessage ?? '',
  })
  await nextTick()
  formRef.value?.clearValidate()
})
onUnmounted(() => { active = false })

function close() {
  if (!saving.value) emit('update:modelValue', false)
}

async function save() {
  if (saving.value) return
  saving.value = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid || !active) return
    const fields = editing.value
      ? buildRunChanges(props.run, form)
      : { runName: form.runName }
    if (!Object.keys(fields).length) {
      ElMessage.info('没有需要保存的修改')
      return
    }
    if (editing.value) await updateRun(props.run.id, fields)
    else await createRun(props.taskId, fields)
    if (!active) return
    ElMessage.success(editing.value ? '实验运行更新成功' : '实验运行创建成功')
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
  <ElDialog :model-value="modelValue" :title="editing ? '编辑运行' : '新建运行'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? '更新运行信息，记录实验执行结果。' : '为当前实验任务添加一次运行记录。' }}</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="运行名称" prop="runName">
        <ElInput v-model="form.runName" maxlength="150" placeholder="请输入实验运行名称" autofocus />
      </ElFormItem>
      <template v-if="editing">
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="form.status" placeholder="请选择状态">
            <ElOption v-for="status in runStatuses" :key="status" :label="getRunStatusLabel(status)" :value="status" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="form.status === 'FAILED'" label="失败原因" prop="errorMessage">
          <ElInput v-model="form.errorMessage" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="填写运行失败原因（选填）" />
          <p class="field-hint">失败原因选填；改为其他状态后，后端会自动清空失败原因。</p>
        </ElFormItem>
      </template>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '创建运行' }}</ElButton>
    </template>
  </ElDialog>
</template>
