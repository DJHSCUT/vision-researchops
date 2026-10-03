import request from '../utils/request'

export const getArtifacts = (runId, type) => request.get(`/runs/${encodeURIComponent(runId)}/artifacts`,
  type ? { params: { type } } : undefined,
)
export const createArtifact = (runId, data) => request.post(`/runs/${encodeURIComponent(runId)}/artifacts`, data)
export const getArtifact = (artifactId) => request.get(`/artifacts/${encodeURIComponent(artifactId)}`)
export const updateArtifact = (artifactId, data) => request.patch(`/artifacts/${encodeURIComponent(artifactId)}`, data)
export const deleteArtifact = (artifactId) => request.delete(`/artifacts/${encodeURIComponent(artifactId)}`)
