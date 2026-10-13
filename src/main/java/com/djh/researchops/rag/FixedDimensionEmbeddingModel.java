package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

/** Pins model/options and validates EVERY batch/query vector before Qdrant receives it. */
public final class FixedDimensionEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final String collection;

    public FixedDimensionEmbeddingModel(EmbeddingModel delegate) {
        this(delegate, "researchops_knowledge_v1");
    }

    public FixedDimensionEmbeddingModel(EmbeddingModel delegate, String collection) {
        this.delegate = delegate;
        this.collection = collection;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        EmbeddingResponse response;
        try {
            response = delegate.call(new EmbeddingRequest(request.getInstructions(),
                    OllamaEmbeddingOptions.builder().model(RagProperties.MODEL).truncate(false).build()));
        } catch (RuntimeException e) {
            throw RagDiagnostics.failure("embedding", collection, "ollamaEmbed", e, 503,
                    "Ollama Embedding 不可用，请检查服务、qwen3-embedding:0.6b 模型和超时；本次操作未确认完成");
        }
        if (response == null || response.getResults().size() != request.getInstructions().size()) {
            throw invalidResponse("Ollama 返回的向量数量与文本数量不一致");
        }
        for (var embedding : response.getResults()) {
            float[] vector = embedding.getOutput();
            if (vector == null || vector.length != RagProperties.DIMENSIONS) {
                throw invalidResponse("Ollama 向量维度不兼容，必须为 1024；停止写入 Qdrant");
            }
            boolean nonzero = false;
            for (float value : vector) {
                if (!Float.isFinite(value)) throw invalidResponse("Ollama 返回非有限向量值；停止写入 Qdrant");
                nonzero |= value != 0;
            }
            if (!nonzero) throw invalidResponse("Ollama 返回零向量；停止写入 Qdrant");
        }
        return response;
    }

    private BusinessException invalidResponse(String message) {
        return RagDiagnostics.failure("embedding-validation", collection, "validateEmbedding", new IllegalStateException(message),
                503, message);
    }

    @Override public float[] embed(Document document) { return embed(document.getText()); }
    @Override public String getEmbeddingContent(Document document) { return document.getText(); }
    @Override public int dimensions() { return RagProperties.DIMENSIONS; }
}
