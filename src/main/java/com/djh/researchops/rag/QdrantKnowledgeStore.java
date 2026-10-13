package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

public final class QdrantKnowledgeStore implements KnowledgeStore {
    private final QdrantClient client;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    public QdrantKnowledgeStore(QdrantClient client, VectorStore vectorStore, RagProperties properties) {
        this.client = client;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public void ensureCollection(boolean createIfMissing) {
        String collection = properties.collectionName();
        if (!await(client.collectionExistsAsync(collection), "collectionExists")) {
            if (!createIfMissing) throw new BusinessException(409, "RAG Collection 不存在，请先显式导入 DEMO 文档");
            try {
                await(client.createCollectionAsync(collection, Collections.VectorParams.newBuilder()
                        .setSize(RagProperties.DIMENSIONS).setDistance(Collections.Distance.Cosine).build()), "createCollection");
            } catch (BusinessException failure) {
                // A second instance may have just created it. Never recreate/delete it.
                if (!await(client.collectionExistsAsync(collection), "collectionExists")) throw failure;
            }
        }
        validate(clientInfo());
    }

    private Collections.VectorsConfig clientInfo() {
        return await(client.getCollectionInfoAsync(properties.collectionName()), "getCollectionInfo")
                .getConfig().getParams().getVectorsConfig();
    }

    static void validate(Collections.VectorsConfig config) {
        if (!config.hasParams() || config.getParams().getSize() != RagProperties.DIMENSIONS
                || config.getParams().getDistance() != Collections.Distance.Cosine) {
            throw new BusinessException(409, "Qdrant Collection 不兼容：需要未命名的 1024 维 Cosine 向量；已有数据未被修改");
        }
    }

    @Override
    public Map<String, String> existingChunks(String documentId) {
        Map<String, String> existing = new LinkedHashMap<>();
        var request = Points.ScrollPoints.newBuilder().setCollectionName(properties.collectionName()).setLimit(100)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true))
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false))
                .setFilter(Common.Filter.newBuilder().addMust(ConditionFactory.matchKeyword("documentId", documentId)));
        while (true) {
            var page = await(client.scrollAsync(request.build()), "scroll");
            for (var point : page.getResultList()) {
                var fingerprint = point.getPayloadMap().get("importFingerprint");
                existing.put(point.getId().getUuid(), fingerprint == null ? "" : fingerprint.getStringValue());
            }
            if (!page.hasNextPageOffset()) break;
            request.setOffset(page.getNextPageOffset());
        }
        return existing;
    }

    @Override
    public void add(List<Document> chunks) {
        try { vectorStore.add(chunks); }
        catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { throw unavailable(e, "vector-store-add", "add"); }
    }

    @Override
    public List<Document> search(SearchRequest request) {
        try { return vectorStore.similaritySearch(request); }
        catch (BusinessException e) { throw e; }
        catch (RuntimeException e) {
            int code = RagDiagnostics.searchHttpStatus(e);
            throw RagDiagnostics.failure("vector-store-search", properties.collectionName(), "similaritySearch", e,
                    code, code == 503 ? "RAG 检索依赖暂时不可用，请查看后端诊断日志"
                            : code == 502 ? "RAG 检索依赖调用或返回格式异常，请查看后端诊断日志"
                            : "RAG 检索处理异常，请查看后端诊断日志");
        }
    }

    private <T> T await(Future<T> future, String operation) {
        try { return future.get(properties.qdrantTimeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e, "qdrant-client", operation);
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            throw unavailable(e, "qdrant-client", operation);
        }
    }

    private BusinessException unavailable(Throwable failure, String stage, String operation) {
        return RagDiagnostics.failure(stage, properties.collectionName(), operation, failure, 503,
                "Qdrant 操作失败，请检查服务、gRPC 端口 6334 和连接配置；本次操作未确认完成，重试导入可补齐缺失片段");
    }
}
