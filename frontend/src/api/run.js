import request from '../utils/request'

export const createRun = (taskId, data) => request.post(`/tasks/${encodeURIComponent(taskId)}/runs`, data)
export const getTaskRuns = (taskId) => request.get(`/tasks/${encodeURIComponent(taskId)}/runs`)
export const getRun = (id) => request.get(`/runs/${encodeURIComponent(id)}`)
export const updateRun = (id, data) => request.patch(`/runs/${encodeURIComponent(id)}`, data)
export const deleteRun = (id) => request.delete(`/runs/${encodeURIComponent(id)}`)
