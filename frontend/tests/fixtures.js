export const source = (citationId = 1, overrides = {}) => ({
  citationId, title: '3DGS 多视角采集与重叠率', source: 'docs/rag-demo/01-multiview-coverage.md',
  chunkIndex: 0, score: 0.796, isDemo: true, usedInContext: true, ...overrides,
})
export const ragResult = (overrides = {}) => ({
  answer: '## DEMO 说明\n\n**覆盖不足**可能造成局部缺失。[1]\n\n|问题|现象|\n|---|---|\n|遮挡|缺失|',
  sources: [source()], grounded: true, insufficientEvidence: false, retrievedCount: 2,
  retrievedSources: [source(), source(3, { chunkIndex: 1, usedInContext: false, score: 0.4 })],
  evidenceStatus: 'CITATIONS_VALIDATED', ...overrides,
})
