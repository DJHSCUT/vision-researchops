package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import static org.assertj.core.api.Assertions.*;

class DemoDocumentLoaderTests {
    @TempDir Path directory;

    static RagProperties properties(Path directory) {
        return new RagProperties(true, directory, 500, 80, "http://localhost:11434", Duration.ofSeconds(120),
                "localhost", 6334, false, Duration.ofSeconds(15), "researchops_knowledge_v1");
    }

    @Test void loadsRealDemoCorpusWithTraceableMetadataAndStableIds() {
        var loader = new DemoDocumentLoader(properties(Path.of("docs/rag-demo")));
        var chunks = loader.loadChunks();
        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks.stream().map(c -> c.getMetadata().get("documentId")).distinct()).hasSize(5);
        for (var chunk : chunks) {
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getMetadata()).containsKeys("documentId", "source", "title", "chunkIndex", "isDemo");
            assertThat(chunk.getMetadata().get("isDemo")).isEqualTo(true);
            assertThat(chunk.getMetadata().get("title").toString()).contains("DEMO");
            assertThat(Files.exists(Path.of(java.net.URI.create((String) chunk.getMetadata().get("source"))))).isTrue();
            assertThatCode(() -> UUID.fromString(chunk.getId())).doesNotThrowAnyException();
        }
        assertThat(loader.loadChunks()).isEqualTo(chunks);
    }

    @Test void sortsMarkdownIgnoresOtherFilesAndIndexesFromZero() throws Exception {
        Files.writeString(directory.resolve("b.md"), "# DEMO：B\n" + "乙".repeat(1000));
        Files.writeString(directory.resolve("a.md"), "# DEMO：A\n" + "甲".repeat(1000));
        Files.writeString(directory.resolve("other.txt"), "not markdown");
        var chunks = new DemoDocumentLoader(properties(directory)).loadChunks();
        assertThat(chunks.get(0).getMetadata()).containsEntry("title", "DEMO：A").containsEntry("chunkIndex", 0);
        var bySource = chunks.stream().collect(java.util.stream.Collectors.groupingBy(c -> c.getMetadata().get("source")));
        assertThat(bySource).hasSize(2);
        for (var document : bySource.values()) {
            for (int i = 0; i < document.size(); i++) {
                assertThat(document.get(i).getMetadata()).containsEntry("chunkIndex", i).containsEntry("chunkCount", document.size());
            }
        }
    }

    @Test void normalizesBomAndWindowsLineEndings() throws Exception {
        Path file = directory.resolve("a.md");
        Files.writeString(file, "\uFEFF# DEMO：测试\r\n\r\n内容");
        var loader = new DemoDocumentLoader(properties(directory));
        var original = loader.loadChunks();
        Files.writeString(file, "# DEMO：测试\n\n内容");
        assertThat(loader.loadChunks()).isEqualTo(original);
    }

    @Test void rejectsMissingDirectory() {
        assertThatThrownBy(() -> new DemoDocumentLoader(properties(directory.resolve("missing"))).loadChunks())
                .isInstanceOf(BusinessException.class).hasMessageContaining("目录不存在");
    }
    @Test void rejectsEmptyDirectory() {
        assertThatThrownBy(() -> new DemoDocumentLoader(properties(directory)).loadChunks()).hasMessageContaining("没有 Markdown");
    }
    @Test void rejectsUnmarkedDocument() throws Exception {
        Files.writeString(directory.resolve("real.md"), "# 未标注\n内容");
        assertThatThrownBy(() -> new DemoDocumentLoader(properties(directory)).loadChunks()).hasMessageContaining("DEMO 标注");
    }
    @Test void rejectsBlankDocument() throws Exception {
        Files.writeString(directory.resolve("empty.md"), "   ");
        assertThatThrownBy(() -> new DemoDocumentLoader(properties(directory)).loadChunks()).hasMessageContaining("DEMO 标注");
    }

    @Test void splitterPreservesAllTextWithExactOverlapAndMetadata() {
        String text = "汉😀".repeat(700);
        var chunks = new MarkdownTextSplitter(500, 80).split(new Document(text, Map.of("source", "demo")));
        StringBuilder reconstructed = new StringBuilder(chunks.get(0).getText());
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).getText();
            String current = chunks.get(i).getText();
            int prefixEnd = current.offsetByCodePoints(0, 80);
            assertThat(current.substring(0, prefixEnd)).isEqualTo(previous.substring(previous.offsetByCodePoints(previous.length(), -80)));
            reconstructed.append(current.substring(prefixEnd));
        }
        assertThat(reconstructed.toString()).isEqualTo(text);
        for (var chunk : chunks) {
            assertThat(chunk.getText().codePointCount(0, chunk.getText().length())).isLessThanOrEqualTo(500);
            assertThat(chunk.getMetadata()).containsEntry("source", "demo");
        }
    }
    @Test void splitterPrefersNewlinesAndHandlesShortText() {
        var splitter = new MarkdownTextSplitter(100, 10);
        var chunks = splitter.split(new Document("甲".repeat(70) + "\n" + "乙".repeat(100)));
        assertThat(chunks.get(0).getText()).endsWith("\n");
        assertThat(splitter.split(new Document("短文本"))).extracting(Document::getText).containsExactly("短文本");
    }
    @Test void splitterPrefersChineseSentenceBoundary() {
        var chunks = new MarkdownTextSplitter(100, 10).split(new Document("甲".repeat(70) + "。" + "乙".repeat(100)));
        assertThat(chunks.get(0).getText()).endsWith("。");
    }
    @Test void splitterRejectsUnsafeOverlapAndReturnsNoBlankChunks() {
        assertThatThrownBy(() -> new MarkdownTextSplitter(100, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MarkdownTextSplitter(100, 10).split(new Document(" ".repeat(200)))).isEmpty();
    }
}
