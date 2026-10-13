package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

public final class RagService {
    private final DemoDocumentLoader loader;
    private final KnowledgeStore store;
    private final RagProperties properties;

    public RagService(DemoDocumentLoader loader, KnowledgeStore store, RagProperties properties) {
        this.loader = loader;
        this.store = store;
        this.properties = properties;
    }

    // Local requests are serialized; stable UUID upsert also prevents duplicate points across processes/restarts.
    public synchronized ImportResult importDemo() {
        List<Document> chunks = loader.loadChunks();
        store.ensureCollection(true);
        Map<String, List<Document>> documents = chunks.stream().collect(Collectors.groupingBy(
                chunk -> (String) chunk.getMetadata().get("documentId"), LinkedHashMap::new, Collectors.toList()));
        List<Document> missing = new ArrayList<>();
        // Preflight ALL sources before writing any points, including partial import recovery.
        for (var entry : documents.entrySet()) {
            Map<String, String> existing = store.existingChunks(entry.getKey());
            String fingerprint = (String) entry.getValue().get(0).getMetadata().get("importFingerprint");
            var expectedIds = entry.getValue().stream().map(Document::getId).collect(Collectors.toSet());
            if (existing.values().stream().anyMatch(value -> !fingerprint.equals(value))
                    || !expectedIds.containsAll(existing.keySet())) {
                throw new BusinessException(409, "DEMO 源文档或切分配置已变化/已有片段不兼容；请使用新的 Collection 或单独规划迁移，未覆盖或删除已有片段");
            }
            for (Document chunk : entry.getValue()) if (!existing.containsKey(chunk.getId())) missing.add(chunk);
        }
        if (!missing.isEmpty()) {
            store.add(missing);
            for (var entry : documents.entrySet()) {
                var persisted = store.existingChunks(entry.getKey());
                if (entry.getValue().stream().anyMatch(chunk -> !chunk.getMetadata().get("importFingerprint")
                        .equals(persisted.get(chunk.getId())))) {
                    throw new BusinessException(503, "Qdrant 写入后校验未通过；未确认导入完成，请重试补齐片段");
                }
            }
        }
        return new ImportResult(properties.collectionName(), RagProperties.MODEL, RagProperties.DIMENSIONS,
                documents.size(), chunks.size(), missing.size(), chunks.size() - missing.size());
    }

    public List<SearchHit> search(String query, int topK) {
        if (query == null || query.isBlank() || query.length() > 2000) {
            throw new BusinessException(400, "检索问题不能为空且不能超过 2000 个字符");
        }
        if (topK < 1 || topK > 20) throw new BusinessException(400, "topK 必须为 1–20");
        store.ensureCollection(false);
        // Spring AI 2.0.1's Qdrant EQ converter accepts strings/numbers, not Boolean.
        // The dedicated namespace identifies this corpus; validate isDemo strictly on returned payloads.
        var documents = store.search(SearchRequest.builder().query(query.strip()).topK(topK).similarityThresholdAll()
                .filterExpression("ragNamespace == '" + RagProperties.NAMESPACE + "'").build());
        try {
            if (documents == null) throw new IllegalArgumentException("Missing RAG result list");
            return documents.stream().map(SearchHit::from).toList();
        } catch (RuntimeException failure) {
            throw RagDiagnostics.failure("result-mapping", properties.collectionName(), "mapSearchHits", failure,
                    502, "RAG 检索结果格式异常，请查看后端诊断日志");
        }
    }

    public record ImportResult(String collection, String embeddingModel, int dimensions, int documentCount,
                               int chunkCount, int addedCount, int skippedCount) { }

    public record SearchHit(String chunkId, String documentId, String source, String title, int chunkIndex,
                            boolean isDemo, String content, Double score) {
        static SearchHit from(Document document) {
            if (document == null) throw invalidField("document");
            var metadata = document.getMetadata();
            for (String field : List.of("documentId", "source", "title")) {
                if (!(metadata.get(field) instanceof String value) || value.isBlank()) throw invalidField(field);
            }
            if (!(metadata.get("chunkIndex") instanceof Number index) || !Double.isFinite(index.doubleValue())
                    || index.doubleValue() < 0 || index.doubleValue() > Integer.MAX_VALUE
                    || index.doubleValue() != index.intValue()) throw invalidField("chunkIndex");
            if (!Boolean.TRUE.equals(metadata.get("isDemo"))) throw invalidField("isDemo");
            if (document.getText() == null || document.getText().isBlank()) throw invalidField("content");
            if (document.getId() == null || document.getId().isBlank()) throw invalidField("chunkId");
            if (document.getScore() != null && !Double.isFinite(document.getScore())) throw invalidField("score");
            return new SearchHit(document.getId(), (String) metadata.get("documentId"),
                    (String) metadata.get("source"), (String) metadata.get("title"), index.intValue(), true,
                    document.getText(), document.getScore());
        }

        private static IllegalArgumentException invalidField(String field) {
            return new IllegalArgumentException("Invalid RAG result field: " + field);
        }
    }
}
