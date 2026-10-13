package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;

public final class DemoDocumentLoader {
    private final RagProperties properties;
    private final MarkdownTextSplitter splitter;

    public DemoDocumentLoader(RagProperties properties) {
        this.properties = properties;
        this.splitter = new MarkdownTextSplitter(properties.chunkSize(), properties.chunkOverlap());
    }

    public List<Document> loadChunks() {
        Path directory = properties.demoDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) throw new BusinessException(400, "DEMO 文档目录不存在：" + directory);
        try (var files = Files.list(directory)) {
            List<Path> markdown = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md")).sorted().toList();
            if (markdown.isEmpty()) throw new BusinessException(400, "DEMO 文档目录中没有 Markdown 文件");
            List<Document> chunks = new ArrayList<>();
            for (Path file : markdown) chunks.addAll(load(file));
            return List.copyOf(chunks);
        } catch (IOException e) {
            throw new BusinessException(400, "无法读取 DEMO Markdown 文档，请检查路径和文件权限");
        }
    }

    private List<Document> load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8).replace("\uFEFF", "")
                .replace("\r\n", "\n").replace('\r', '\n').strip();
        String title = text.lines().filter(line -> line.startsWith("# ")).findFirst()
                .map(line -> line.substring(2).strip()).orElse("");
        if (text.isBlank() || !title.contains("DEMO")) {
            throw new BusinessException(400, "测试文档必须包含带 DEMO 标注的一级标题：" + file.getFileName());
        }
        // File URI is an absolute, inspectable original source; callers cannot supply upload paths.
        String source = file.toUri().toString();
        String documentId = uuid(RagProperties.NAMESPACE + "|" + source);
        String fingerprint = hash(text + "|unicode-boundary-v1|" + properties.chunkSize() + "|"
                + properties.chunkOverlap() + "|" + RagProperties.MODEL);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("documentId", documentId);
        metadata.put("source", source);
        metadata.put("title", title);
        metadata.put("isDemo", true);
        metadata.put("ragNamespace", RagProperties.NAMESPACE);
        metadata.put("embeddingModel", RagProperties.MODEL);
        metadata.put("importFingerprint", fingerprint);
        List<Document> split = splitter.split(new Document(documentId, text, metadata));
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            var chunkMetadata = new LinkedHashMap<>(split.get(i).getMetadata());
            chunkMetadata.put("chunkIndex", i);
            chunkMetadata.put("chunkCount", split.size());
            result.add(new Document(uuid(documentId + "|chunk|" + i), split.get(i).getText(), chunkMetadata));
        }
        return result;
    }

    static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
