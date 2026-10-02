export const runStatuses = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED']

const runStatusLabels = {
  PENDING: '等待运行',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

export function getRunStatusLabel(status) {
  return runStatusLabels[status] || '未知状态'
}

export function buildRunChanges(original, form) {
  const changes = {}
  if (form.runName !== original.runName) changes.runName = form.runName
  if (form.status !== original.status) changes.status = form.status
  // 离开 FAILED 只提交状态变化，失败原因由后端清理。
  if (form.status === 'FAILED' && form.errorMessage !== (original.errorMessage ?? '')) {
    changes.errorMessage = form.errorMessage
  }
  return changes
}
