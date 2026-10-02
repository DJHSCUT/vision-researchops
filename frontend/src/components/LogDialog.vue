<script setup>
import { nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createLog } from '../api/log'
import { getLogLevelLabel, logLevels } from '../utils/log'

const props = defineProps({
  modelValue: Boolean,
  runId: { type: [String, Number], required: true },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const form = reactive({ level: 'INFO', content: '' })
let active = true
const rules = {
  content: [
    { validator: (_rule, value, callback) => callback(value?.trim() ? undefined : new Error('实验日志内容不能为空')), trigger: 'blur' },
    { max: 5000, message: '实验日志内容不能超过5000个字符', trigger: 'blur' },
  ],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, { level: 'INFO', content: '' })
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
    await createLog(props.runId, { level: form.level, content: form.content })
    if (!active) return
    ElMessage.success('实验日志添加成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    // 请求层显示后端 message；保留输入供重试。
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog :model-value="modelValue" title="添加日志" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">记录本次运行的关键信息。日志添加后不可编辑或删除。</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="日志级别" prop="level">
        <ElSelect v-model="form.level" placeholder="请选择日志级别">
          <ElOption v-for="level in logLevels" :key="level" :label="getLogLevelLabel(level)" :value="level" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="日志内容" prop="content">
        <ElInput v-model="form.content" type="textarea" :rows="6" maxlength="5000" show-word-limit placeholder="请输入实验日志内容" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">添加</ElButton>
    </template>
  </ElDialog>
</template>
