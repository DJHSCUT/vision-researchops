package com.djh.researchops.rag;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("researchops.rag")
public record RagProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("docs/rag-demo") Path demoDirectory,
        @DefaultValue("500") int chunkSize,
        @DefaultValue("80") int chunkOverlap,
        @DefaultValue("http://localhost:11434") String ollamaBaseUrl,
        @DefaultValue("120s") Duration ollamaTimeout,
        @DefaultValue("localhost") String qdrantHost,
        @DefaultValue("6334") int qdrantPort,
        @DefaultValue("false") boolean qdrantUseTls,
        @DefaultValue("15s") Duration qdrantTimeout,
        @DefaultValue("researchops_knowledge_v1") String collectionName) {

    public static final String MODEL = "qwen3-embedding:0.6b";
    public static final int DIMENSIONS = 1024;
    public static final String NAMESPACE = "researchops-rag-demo-v1";

    public RagProperties {
        if (chunkSize < 100 || chunkSize > 2000 || chunkOverlap < 0 || chunkOverlap >= chunkSize / 2) {
            throw new IllegalArgumentException("RAG chunk-size 必须为 100–2000，overlap 必须小于 size/2");
        }
        if (qdrantPort < 1 || qdrantPort > 65535 || collectionName == null || collectionName.isBlank()
                || ollamaTimeout.isZero() || ollamaTimeout.isNegative()
                || qdrantTimeout.isZero() || qdrantTimeout.isNegative()) {
            throw new IllegalArgumentException("RAG 连接配置无效");
        }
    }
}
