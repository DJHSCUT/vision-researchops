import request from '../utils/request'

export const updateMetric = (metricId, data) => request.patch(`/metrics/${encodeURIComponent(metricId)}`, data)
export const deleteMetric = (metricId) => request.delete(`/metrics/${encodeURIComponent(metricId)}`)

export const createMetric = (runId, data) => request.post(`/runs/${encodeURIComponent(runId)}/metrics`, data)
export const getRunMetrics = (runId, name) => request.get(`/runs/${encodeURIComponent(runId)}/metrics`,
  name == null ? undefined : { params: { name } },
)
