package com.djh.researchops.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

final class RagAskTestSupport {
    static RagAskProperties properties(Path root) { return properties(root, 0.60, 6000, 1200); }
    static RagAskProperties properties(Path root, double score, int context, int chunk) {
        return new RagAskProperties(true, score, context, chunk, 6000, 1024, root.toString());
    }
    static RagPromptContextBuilder builder(Path root, RagAskProperties properties) {
        return new RagPromptContextBuilder(properties, DemoDocumentLoaderTests.properties(root.resolve("docs/rag-demo")));
    }
    static RagService.SearchHit hit(Path root, int index, double score) throws Exception {
        Path directory = Files.createDirectories(root.resolve("docs/rag-demo"));
        Path file = directory.resolve("doc-" + index + ".md");
        String text = "# DEMO：视角覆盖\n视角覆盖不足可能导致局部重建缺失。";
        Files.writeString(file, text);
        return new RagService.SearchHit("chunk-" + index, "document-" + index, file.toUri().toString(),
                "DEMO：视角覆盖 " + index, index, true, text, score);
    }
    static ChatResponse answer(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
}
