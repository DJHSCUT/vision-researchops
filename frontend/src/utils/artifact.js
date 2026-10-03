export const artifactTypes = [
  { value: 'IMAGE', label: '图片' },
  { value: 'MODEL', label: '模型' },
  { value: 'POINT_CLOUD', label: '点云' },
  { value: 'CHECKPOINT', label: '检查点' },
  { value: 'REPORT', label: '报告' },
  { value: 'OTHER', label: '其他' },
]

export function getArtifactTypeLabel(type) {
  return artifactTypes.find(option => option.value === type)?.label || '未知类型'
}

export function formatFileSize(bytes) {
  if (bytes == null) return '-'
  const size = Number(bytes)
  const index = size < 1024 ? 0 : size < 1024 ** 2 ? 1 : size < 1024 ** 3 ? 2 : 3
  const value = size / 1024 ** index
  return `${Number(value.toFixed(2))} ${['B', 'KB', 'MB', 'GB'][index]}`
}

export function getFileSizeError(value, originalSize = null) {
  const text = String(value ?? '').trim()
  if (!text) return originalSize == null ? '' : '当前不支持清空文件大小，请填写非负整数'
  if (!/^\d+$/.test(text) || !Number.isSafeInteger(Number(text))) return '文件大小必须为非负整数'
  return ''
}

export function buildArtifactData(form) {
  return {
    artifactName: form.artifactName,
    artifactType: form.artifactType,
    storagePath: form.storagePath,
    description: form.description || null,
    fileSizeBytes: form.fileSizeBytes.trim() ? Number(form.fileSizeBytes.trim()) : null,
  }
}

export function buildArtifactChanges(original, form) {
  const current = buildArtifactData(form)
  const changes = {}
  for (const field of ['artifactName', 'artifactType', 'storagePath']) {
    if (current[field] !== original[field]) changes[field] = current[field]
  }
  if ((current.description || '') !== (original.description || '')) changes.description = current.description || ''
  // 后端不支持清空文件大小，不发送 null 作为更新值。
  if (current.fileSizeBytes != null && current.fileSizeBytes !== original.fileSizeBytes) {
    changes.fileSizeBytes = current.fileSizeBytes
  }
  return changes
}
