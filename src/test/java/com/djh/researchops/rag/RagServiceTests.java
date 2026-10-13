package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTests {
    @TempDir Path directory;

    RagService service(KnowledgeStore store) throws Exception {
        Files.writeString(directory.resolve("a.md"), "# DEMO：视角覆盖\n" + "视角不足可能导致重建缺失。".repeat(100));
        var properties = DemoDocumentLoaderTests.properties(directory);
        return new RagService(new DemoDocumentLoader(properties), store, properties);
    }

    @Test void repeatedImportAcrossServiceInstancesSkipsPersistedPoints() throws Exception {
        var store = new MemoryStore();
        var first = service(store).importDemo();
        assertThat(first.addedCount()).isEqualTo(first.chunkCount()).isGreaterThan(1);
        assertThat(first.skippedCount()).isZero();
        var second = service(store).importDemo();
        assertThat(second.addedCount()).isZero();
        assertThat(second.skippedCount()).isEqualTo(first.chunkCount());
        assertThat(store.points).hasSize(first.chunkCount());
        assertThat(store.addCalls).isEqualTo(1);
    }
    @Test void retriesPartialFailureWithoutDuplicatesOrFalseSuccess() throws Exception {
        var store = new MemoryStore();
        var service = service(store);
        store.partialFailure = true;
        assertThatThrownBy(service::importDemo).hasMessageContaining("模拟写入失败");
        assertThat(store.points).hasSize(1);
        store.partialFailure = false;
        var recovered = service.importDemo();
        assertThat(recovered.skippedCount()).isEqualTo(1);
        assertThat(store.points).hasSize(recovered.chunkCount());
    }
    @Test void changedSourceIncludingShrinkIsRejectedWithoutOverwriting() throws Exception {
        var store = new MemoryStore();
        var service = service(store);
        service.importDemo();
        var before = new LinkedHashMap<>(store.points);
        Files.writeString(directory.resolve("a.md"), "# DEMO：视角覆盖\n新的短文本");
        assertThatThrownBy(service::importDemo).isInstanceOf(BusinessException.class).hasMessageContaining("已变化");
        assertThat(store.points).isEqualTo(before);
        assertThat(store.addCalls).isEqualTo(1);
    }
    @Test void preflightChecksAllSourcesBeforeAddingNewDocument() throws Exception {
        var store = new MemoryStore();
        var service = service(store);
        service.importDemo();
        Files.writeString(directory.resolve("0-new.md"), "# DEMO：新文件\n新内容");
        Files.writeString(directory.resolve("a.md"), "# DEMO：视角覆盖\n已修改");
        assertThatThrownBy(service::importDemo).hasMessageContaining("已变化");
        assertThat(store.addCalls).isEqualTo(1);
    }
    @Test void incompleteReadbackDoesNotClaimImportSuccess() throws Exception {
        var store = mock(KnowledgeStore.class);
        when(store.existingChunks(anyString())).thenReturn(Map.of());
        var service = service(store);
        assertThatThrownBy(service::importDemo).hasMessageContaining("写入后校验未通过");
    }
    @Test void mapsTopKMetadataContentAndOptionalScoreWithoutChat() throws Exception {
        var store = new MemoryStore();
        var service = service(store);
        service.importDemo();
        var document = store.points.values().iterator().next();
        store.results = List.of(document.mutate().score(0.87).build(), document);
        var hits = service.search("  3DGS 视角覆盖不足会造成什么问题？  ", 2);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).content()).isEqualTo(document.getText());
        assertThat(hits.get(0).source()).isEqualTo(document.getMetadata().get("source"));
        assertThat(hits.get(0).title()).isEqualTo("DEMO：视角覆盖");
        assertThat(hits.get(0).documentId()).isEqualTo(document.getMetadata().get("documentId"));
        assertThat(hits.get(0).chunkIndex()).isZero();
        assertThat(hits.get(0).isDemo()).isTrue();
        assertThat(hits.get(0).score()).isEqualTo(0.87);
        assertThat(hits.get(1).score()).isNull();
        assertThat(store.searchRequest.getQuery()).isEqualTo("3DGS 视角覆盖不足会造成什么问题？");
        assertThat(store.searchRequest.getTopK()).isEqualTo(2);
        assertThat(store.searchRequest.getFilterExpression().toString()).contains("ragNamespace").doesNotContain("isDemo");
        assertThat(store.lastCreate).isFalse();
    }
    @Test void emptySearchResultsRemainEmpty() throws Exception {
        assertThat(service(new MemoryStore()).search("没有匹配片段", 3)).isEmpty();
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 21})
    void rejectsInvalidTopKBeforeStoreAccess(int topK) throws Exception {
        var store = mock(KnowledgeStore.class);
        var service = service(store);
        assertThatThrownBy(() -> service.search("问题", topK)).hasMessageContaining("topK");
        verifyNoInteractions(store);
    }
    @Test void rejectsInvalidQueriesBeforeStoreAccess() throws Exception {
        var store = mock(KnowledgeStore.class);
        var service = service(store);
        for (String query : new String[]{null, " ", "甲".repeat(2001)}) {
            assertThatThrownBy(() -> service.search(query, 3)).hasMessageContaining("检索问题");
        }
        verifyNoInteractions(store);
    }
    @Test void collectionFailurePropagatesWithoutWritingOrSearching() throws Exception {
        var store = mock(KnowledgeStore.class);
        doThrow(new BusinessException(503, "Qdrant 不可用")).when(store).ensureCollection(anyBoolean());
        var service = service(store);
        assertThatThrownBy(service::importDemo).hasMessageContaining("不可用");
        assertThatThrownBy(() -> service.search("问题", 3)).hasMessageContaining("不可用");
        verify(store, never()).add(anyList());
        verify(store, never()).search(any());
    }

    static class MemoryStore implements KnowledgeStore {
        Map<String, Document> points = new LinkedHashMap<>();
        List<Document> results = new ArrayList<>();
        int addCalls;
        boolean partialFailure;
        boolean lastCreate;
        SearchRequest searchRequest;
        public void ensureCollection(boolean create) { lastCreate = create; }
        public Map<String, String> existingChunks(String documentId) {
            Map<String, String> result = new LinkedHashMap<>();
            points.forEach((id, doc) -> {
                if (documentId.equals(doc.getMetadata().get("documentId")))
                    result.put(id, (String) doc.getMetadata().get("importFingerprint"));
            });
            return result;
        }
        public void add(List<Document> chunks) {
            addCalls++;
            for (var chunk : chunks) {
                points.put(chunk.getId(), chunk);
                if (partialFailure) throw new BusinessException(503, "模拟写入失败");
            }
        }
        public List<Document> search(SearchRequest request) { searchRequest = request; return results; }
    }
}
