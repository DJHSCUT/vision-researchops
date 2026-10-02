// LocalDateTime has no timezone: display the backend's wall-clock time without converting zones.
export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

// ACTIVE is the only state explicitly defined by the backend. Others must come from real records.
export function getStatusOptions(projects = []) {
  return [...new Set(['ACTIVE', ...projects.map((project) => project.status).filter(Boolean)])]
}

export function buildProjectChanges(original, form) {
  const changes = {}
  if (form.name !== original.name) changes.name = form.name
  if (form.description !== (original.description ?? '')) changes.description = form.description
  if (form.status !== original.status) changes.status = form.status
  return changes
}
