import request from '../utils/request'

export const getProjects = () => request.get('/projects')
export const getProject = (id) => request.get(`/projects/${encodeURIComponent(id)}`)
export const createProject = (fields) => request.post('/projects', fields)
export const updateProject = (id, changes) => request.patch(`/projects/${encodeURIComponent(id)}`, changes)
export const deleteProject = (id) => request.delete(`/projects/${encodeURIComponent(id)}`)
