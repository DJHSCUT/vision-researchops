export const logLevels = ['INFO', 'WARN', 'ERROR']

const levelLabels = { INFO: '信息', WARN: '警告', ERROR: '错误' }
const levelClasses = { INFO: 'log-info', WARN: 'log-warn', ERROR: 'log-error' }

export function getLogLevelLabel(level) {
  return levelLabels[level] || '未知级别'
}

export function getLogLevelClass(level) {
  return levelClasses[level] || ''
}

export function formatLogTime(value) {
  if (!value) return '—'
  // 后端返回 LocalDateTime，直接显示原始时分秒，不赋予时区。
  return value.match(/T(\d{2}:\d{2}:\d{2})/)?.[1] || value
}
