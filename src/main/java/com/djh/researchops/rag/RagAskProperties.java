package com.djh.researchops.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("researchops.rag.ask")
public record RagAskProperties(@DefaultValue("false") boolean enabled,
                               @DefaultValue("0.60") double minScore,
                               @DefaultValue("6000") int maxContextChars,
                               @DefaultValue("1200") int maxChunkChars,
                               @DefaultValue("6000") int maxAnswerChars,
                               @DefaultValue("1024") int maxOutputTokens,
                               @DefaultValue(".") String projectRoot) {
    public RagAskProperties {
        if (!Double.isFinite(minScore) || minScore < 0 || minScore > 1
                || maxContextChars < 1000 || maxContextChars > 16000
                || maxChunkChars < 100 || maxChunkChars > maxContextChars
                || maxAnswerChars < 200 || maxAnswerChars > 12000
                || maxOutputTokens < 128 || maxOutputTokens > 2048 || projectRoot == null || projectRoot.isBlank()) {
            throw new IllegalArgumentException("RAG 问答阈值或上下文/输出限制配置无效");
        }
    }
}
