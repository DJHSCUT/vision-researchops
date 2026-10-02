// 中文仅用于显示，接口和表单仍使用原始状态值。
const labels = {
  ACTIVE: '进行中',
  INACTIVE: '未启用',
  PAUSED: '已暂停',
  ARCHIVED: '已归档',
  CANCELLED: '已取消',
  CANCELED: '已取消',
  PENDING: '待处理',
  TODO: '待开始',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

export function getStatusLabel(status) {
  return labels[status] || (status ? '其他状态' : '未知状态')
}
