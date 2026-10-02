import request from '../utils/request'

export const createLog = (runId, data) => request.post(`/runs/${encodeURIComponent(runId)}/logs`, data)
export const getRunLogs = (runId, level) => request.get(`/runs/${encodeURIComponent(runId)}/logs`,
  level ? { params: { level } } : undefined,
)
