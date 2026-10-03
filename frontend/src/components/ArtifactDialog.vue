<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createArtifact, updateArtifact } from '../api/artifact'
import { artifactTypes, buildArtifactChanges, buildArtifactData, getFileSizeError } from '../utils/artifact'

const props = defineProps({
  modelValue: Boolean,
  runId: { type: [String, Number], required: true },
  artifact: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const editing = computed(() => !!props.artifact)
const form = reactive({ artifactName: '', artifactType: '', storagePath: '', description: '', fileSizeBytes: '' })
let active = true
const rules = {
  artifactName: [
    { required: true, whitespace: true, message: '请输入产物名称', trigger: 'blur' },
    { max: 200, message: '产物名称不能超过200个字符', trigger: 'blur' },
  ],
  artifactType: [{ required: true, message: '请选择产物类型', trigger: 'change' }],
  storagePath: [
    { required: true, whitespace: true, message: '请输入存储路径', trigger: 'blur' },
    { max: 1000, message: '存储路径不能超过1000个字符', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '产物描述不能超过500个字符', trigger: 'blur' }],
  fileSizeBytes: [{ validator: (_rule, value, callback) => {
    const error = getFileSizeError(value, props.artifact?.fileSizeBytes)
    callback(error ? new Error(error) : undefined)
  }, trigger: 'blur' }],
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    artifactName: props.artifact?.artifactName ?? '',
    artifactType: props.artifact?.artifactType ?? '',
    storagePath: props.artifact?.storagePath ?? '',
    description: props.artifact?.description ?? '',
    fileSizeBytes: props.artifact?.fileSizeBytes == null ? '' : String(props.artifact.fileSizeBytes),
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
    const fields = editing.value ? buildArtifactChanges(props.artifact, form) : buildArtifactData(form)
    if (!Object.keys(fields).length) {
      ElMessage.info('没有需要保存的修改')
      return
    }
    if (editing.value) await updateArtifact(props.artifact.id, fields)
    else await createArtifact(props.runId, fields)
    if (!active) return
    ElMessage.success(editing.value ? '实验产物更新成功' : '实验产物添加成功')
    emit('update:modelValue', false)
    emit('saved')
  } catch {
    // 请求层优先展示后端 message，保留输入以便重试。
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ElDialog :model-value="modelValue" :title="editing ? '编辑实验产物' : '添加实验产物'" width="540px"
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close" @update:model-value="close">
    <p class="dialog-intro">{{ editing ? '更新产物的信息和路径。' : '记录本次运行的结果文件信息和存储路径。' }}这里不会上传或修改实际文件。</p>
    <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <ElFormItem label="产物名称" prop="artifactName">
        <ElInput v-model="form.artifactName" maxlength="200" placeholder="例如：最终点云" />
      </ElFormItem>
      <ElFormItem label="产物类型" prop="artifactType">
        <ElSelect v-model="form.artifactType" placeholder="请选择产物类型">
          <ElOption v-for="option in artifactTypes" :key="option.value" :label="option.label" :value="option.value" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="存储路径" prop="storagePath">
        <ElInput v-model="form.storagePath" maxlength="1000" placeholder="例如：/results/run1/point_cloud.ply" />
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <ElInput v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="简要描述实验结果，可选" />
      </ElFormItem>
      <ElFormItem label="文件大小（字节）" prop="fileSizeBytes">
        <ElInput v-model="form.fileSizeBytes" inputmode="numeric" placeholder="例如：104857600，可选" />
        <p v-if="editing && artifact.fileSizeBytes != null" class="field-hint">当前不支持清空已记录的文件大小；可修改为其他非负整数。</p>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="saving" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">{{ editing ? '保存修改' : '添加' }}</ElButton>
    </template>
  </ElDialog>
</template>
