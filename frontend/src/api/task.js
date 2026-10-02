import request from '../utils/request'

export const createTask = (projectId, data) => request.post(`/projects/${encodeURIComponent(projectId)}/tasks`, data)
export const getProjectTasks = (projectId) => request.get(`/projects/${encodeURIComponent(projectId)}/tasks`)
export const getTask = (id) => request.get(`/tasks/${encodeURIComponent(id)}`)
export const updateTask = (id, data) => request.patch(`/tasks/${encodeURIComponent(id)}`, data)
export const deleteTask = (id) => request.delete(`/tasks/${encodeURIComponent(id)}`)
