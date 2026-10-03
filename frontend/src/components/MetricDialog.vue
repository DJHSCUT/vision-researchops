<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createMetric, updateMetric } from '../api/metric'
import { buildMetricChanges, buildMetricData, getMetricStepError, getMetricValueError } from '../utils/metric'

const props = defineProps({
  modelValue: Boolean,
  runId: { type: [String, Number], required: true },
  metric: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const editing = computed(() => !!props.metric)
const form = reactive({ metricName: '', metricValue: '', unit: '', step: '' })
let active = true
const rules = {
  metricName: [
    { required: true, whitespace: true, message: '请输入指标名称', trigger: 'blur' },
    { max: 100, message: '指标名称不能超过100个字符', trigger: 'blur' },
  ],
  metricValue: [{ required: true, whitespace: true, message: '请输入指标值', trigger: 'blur' }, { validator: (_rule, value, callback) => {
    const error = getMetricValueError(value)
    callback(error ? new Error(error) : undefined)
  }, trigger: 'blur' }],
  unit: [{ max: 50, message: '单位不能超过50个字符', trigger: 'blur' }],
  step: [{ validator: (_rule, value, callback) => {
    const error = getMetricStepError(value)
    callback(error ? new Error(error) : undefined)
  }, trigger: 'blur' }],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    metricName: props.metric?.metricName ?? '',
    metricValue: props.metric ? String(props.metric.metricValue) : '',
    unit: props.metric?.unit ?? '',
    step: props.metric?.step == null ? '' : String(props.metric.step),
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
    const fields = editing.value ? buildMetricChanges(props.metric, form) : buildMetricData(form)
    if (!Object.keys(fields).length) {
      ElMessage.info('没有需要保存的修改')
      return
    }
    if (editing.value) await updateMetric(props.metric.id, fields)
    else await createMetric(props.runId, fields)
    if (!active) return
    ElMessage.success(editing.value ? '实验指标更新成功' : '实验指标添加成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    // 请求层优先展示后端 message；保留输入以便重试。
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog :model-value="modelValue" :title="editing ? '编辑指标' : '添加指标'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? '修正当前指标记录，保存后更新本次运行的数值结果。' : '记录本次运行的数值结果。新的训练步数请添加为新的记录。' }}</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="指标名称" prop="metricName">
        <ElInput v-model="form.metricName" maxlength="100" placeholder="例如：PSNR、SSIM、train_loss" />
      </ElFormItem>
      <ElFormItem label="指标值" prop="metricValue">
        <ElInput v-model="form.metricValue" inputmode="decimal" placeholder="例如：28.43" />
      </ElFormItem>
      <ElFormItem label="单位" prop="unit">
        <ElInput v-model="form.unit" maxlength="50" placeholder="例如：dB、ms、MB，可选" />
      </ElFormItem>
      <ElFormItem label="训练步数" prop="step">
        <ElInput v-model="form.step" inputmode="numeric" placeholder="例如：1000，可选" />
        <p class="field-hint">留空表示单值指标；填写非负整数表示某一步的序列指标。</p>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '添加' }}</ElButton>
    </template>
  </ElDialog>
</template>
