const decimalPattern = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/

export function getMetricValueError(value) {
  const text = String(value ?? '').trim()
  if (!text) return '请输入指标值'
  if (!decimalPattern.test(text) || !Number.isFinite(Number(text))) return '请输入有效的指标值'
  return ''
}

export function getMetricStepError(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''
  if (!/^\d+$/.test(text) || !Number.isSafeInteger(Number(text))) return '训练步数必须为非负整数'
  return ''
}

export function buildMetricData(form) {
  return {
    metricName: form.metricName,
    metricValue: Number(form.metricValue.trim()),
    unit: form.unit || null,
    step: form.step.trim() ? Number(form.step.trim()) : null,
  }
}

export function formatMetricValue(metric) {
  // 直接显示响应数值，不使用 toFixed 或固定小数位格式化。
  const value = String(metric.metricValue)
  return metric.unit ? `${value} ${metric.unit}` : value
}

export function buildMetricChanges(original, form) {
  const current = buildMetricData(form)
  const changes = {}
  for (const field of ['metricName', 'metricValue', 'step']) {
    if (current[field] !== (original[field] ?? null)) changes[field] = current[field]
  }
  if ((current.unit || '') !== (original.unit || '')) changes.unit = current.unit || ''
  return changes
}

export function groupMetrics(metrics) {
  const singleMetrics = []
  const groups = new Map()
  for (const metric of metrics) {
    if (metric.step == null) {
      singleMetrics.push(metric)
    } else {
      if (!groups.has(metric.metricName)) groups.set(metric.metricName, [])
      groups.get(metric.metricName).push(metric)
    }
  }
  const seriesGroups = Array.from(groups, ([name, records]) => ({
    name,
    records: records.sort((a, b) => a.step - b.step || a.id - b.id),
  }))
  return { singleMetrics, seriesGroups }
}
