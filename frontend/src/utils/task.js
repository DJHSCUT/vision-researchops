export const taskStatuses = ['TODO', 'RUNNING', 'COMPLETED', 'FAILED']

export function buildTaskChanges(original, form) {
  const changes = {}
  if (form.name !== original.name) changes.name = form.name
  if (form.description !== (original.description ?? '')) changes.description = form.description
  if (form.status !== original.status) changes.status = form.status
  return changes
}
