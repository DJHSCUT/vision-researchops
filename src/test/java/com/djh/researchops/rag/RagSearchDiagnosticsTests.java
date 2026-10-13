package com.djh.researchops.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.google.common.util.concurrent.Futures;
import io.grpc.Status;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagSearchDiagnosticsTests {
    final RagProperties properties = DemoDocumentLoaderTests.properties(Path.of("docs/rag-demo"));
    final QdrantClient client = mock(QdrantClient.class);
    final EmbeddingModel embeddings = mock(EmbeddingModel.class);
    final QdrantVectorStore vectors = QdrantVectorStore.builder(client, embeddings)
            .collectionName(properties.collectionName()).initializeSchema(false).build();
    final QdrantKnowledgeStore adapter = new QdrantKnowledgeStore(client, vectors, properties);
    final Logger logger = (Logger) LoggerFactory.getLogger(RagDiagnostics.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach void captureLogsAndVector() {
        logs.start(); logger.addAppender(logs);
        float[] vector = new float[1024]; vector[0] = 1;
        when(embeddings.embed(anyString())).thenReturn(vector);
    }
    @AfterEach void detachLogs() { logger.detachAppender(logs); logs.stop(); }

    SearchRequest request(String filter) {
        return SearchRequest.builder().query("sensitive-query-text").topK(3).similarityThresholdAll().filterExpression(filter).build();
    }
    String logged() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n")); }
    RagService service(KnowledgeStore store) { return new RagService(new DemoDocumentLoader(properties), store, properties); }
    Map<String, Object> metadata() {
        return new LinkedHashMap<>(Map.of("documentId", "doc-id", "source", "file:///demo.md", "title", "DEMO：视角",
                "chunkIndex", 0L, "isDemo", true, "ragNamespace", RagProperties.NAMESPACE));
    }
    Points.ScoredPoint point() {
        var payload = io.qdrant.client.grpc.Points.ScoredPoint.newBuilder()
                .setId(Common.PointId.newBuilder().setUuid("00000000-0000-0000-0000-000000000001"))
                .setScore(0.8f).putPayload("doc_content", ValueFactory.value("DEMO 视角覆盖不足可能造成重建缺失。"));
        metadata().forEach((key, value) -> {
            if (value instanceof String text) payload.putPayload(key, ValueFactory.value(text));
            else if (value instanceof Boolean flag) payload.putPayload(key, ValueFactory.value(flag));
            else payload.putPayload(key, ValueFactory.value(((Number) value).longValue()));
        });
        return payload.build();
    }

    @Test void reproducesOriginalBooleanEqFailureWithActualSpringAiConverterBeforeAnyRpcOrEmbedding() {
        assertThatThrownBy(() -> adapter.search(request("ragNamespace == 'researchops-rag-demo-v1' && isDemo == true")))
                .isInstanceOf(BusinessException.class).hasRootCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(500));
        verifyNoInteractions(client);
        verify(embeddings, never()).embed(anyString());
        assertThat(logged()).contains("stage=vector-store-search", "operation=similaritySearch", "collection=researchops_knowledge_v1",
                "Caused by: java.lang.IllegalArgumentException", "QdrantFilterExpressionConverter.buildEqCondition",
                "Invalid value type for EQ").doesNotContain("sensitive-query-text");
    }

    @Test void fixedServiceUsesActualSpringAiConverterRpcAndPayloadDecoderForTopK() {
        when(client.searchAsync(any(Points.SearchPoints.class))).thenReturn(Futures.immediateFuture(List.of(point())));
        var hits = service(readOnlyCheckedStore()).search("sensitive-query-text", 3);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).title()).isEqualTo("DEMO：视角");
        assertThat(hits.get(0).content()).contains("重建缺失");
        assertThat(hits.get(0).source()).isEqualTo("file:///demo.md");
        assertThat(hits.get(0).score()).isCloseTo(0.8, within(0.000001));
        assertThat(hits.get(0).chunkIndex()).isZero();
        assertThat(hits.get(0).isDemo()).isTrue();
        var captor = ArgumentCaptor.forClass(Points.SearchPoints.class);
        verify(client).searchAsync(captor.capture());
        assertThat(captor.getValue().getLimit()).isEqualTo(3);
        assertThat(captor.getValue().getVectorCount()).isEqualTo(1024);
        assertThat(captor.getValue().getFilter().toString()).contains("ragNamespace", RagProperties.NAMESPACE).doesNotContain("isDemo");
    }
    @Test void actualSdkEmptyResponseRemainsEmpty() {
        when(client.searchAsync(any(Points.SearchPoints.class))).thenReturn(Futures.immediateFuture(List.of()));
        assertThat(service(readOnlyCheckedStore()).search("sensitive-query-text", 3)).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = Status.Code.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "INVALID_ARGUMENT", "UNIMPLEMENTED"})
    void grpcFailureRecordsStatusFullCauseAndSafeHttpSemantics(Status.Code status) throws Exception {
        when(client.searchAsync(any(Points.SearchPoints.class))).thenReturn(Futures.immediateFailedFuture(
                Status.fromCode(status).withDescription("secret-api-key sensitive-query-text private-document").asRuntimeException()));
        int expected = status == Status.Code.UNAVAILABLE || status == Status.Code.DEADLINE_EXCEEDED ? 503 : 502;
        var mvc = MockMvcBuilders.standaloneSetup(new RagDevController(service(readOnlyCheckedStore())))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(get("/dev/rag/search").param("query", "sensitive-query-text"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected))
                .andExpect(jsonPath("$.data").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-api-key", "sensitive-query-text", "StatusRuntimeException", "Caused by");
        assertThat(logged()).contains("stage=vector-store-search", "grpcStatus=" + status, "Caused by:",
                "io.grpc.StatusRuntimeException", "java.util.concurrent.ExecutionException", "QdrantVectorStore.doSimilaritySearch")
                .doesNotContain("secret-api-key", "sensitive-query-text", "private-document");
        assertThat(logs.list).allMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR);
    }

    @Test void queryEmbeddingFailureRetainsOwnStageAndSkipsQdrantRpc() throws Exception {
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any())).thenThrow(new IllegalStateException("secret-key private-document sensitive-query-text"));
        var fixed = new FixedDimensionEmbeddingModel(delegate, properties.collectionName());
        var queryVectors = QdrantVectorStore.builder(client, fixed).collectionName(properties.collectionName()).build();
        var store = new QdrantKnowledgeStore(client, queryVectors, properties);
        var mvc = MockMvcBuilders.standaloneSetup(new RagDevController(service(searchOnly(store))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/dev/rag/search").param("query", "sensitive-query-text"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ollama")));
        verifyNoInteractions(client);
        assertThat(logged()).contains("stage=embedding", "operation=ollamaEmbed", "java.lang.IllegalStateException")
                .doesNotContain("secret-key", "private-document", "sensitive-query-text", "stage=vector-store-search");
    }
    @Test void wrongQueryVectorDimensionsStopBeforeSearchRpcAndLogValidation() {
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[768], 0))));
        var fixed = new FixedDimensionEmbeddingModel(delegate, properties.collectionName());
        var queryVectors = QdrantVectorStore.builder(client, fixed).collectionName(properties.collectionName()).build();
        var store = new QdrantKnowledgeStore(client, queryVectors, properties);
        assertThatThrownBy(() -> store.search(request("ragNamespace == 'researchops-rag-demo-v1'")))
                .hasMessageContaining("1024").satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));
        verifyNoInteractions(client);
        assertThat(logged()).contains("stage=embedding-validation", "operation=validateEmbedding");
    }

    @ParameterizedTest @ValueSource(strings = {"documentId", "source", "title", "chunkIndex", "isDemo"})
    void missingMetadataProducesSafe502AndMappingLog(String field) throws Exception {
        var meta = metadata(); meta.remove(field);
        var store = mock(KnowledgeStore.class);
        when(store.search(any())).thenReturn(List.of(new Document("chunk-id", "private-document", meta)));
        var mvc = MockMvcBuilders.standaloneSetup(new RagDevController(service(store)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/dev/rag/search").param("query", "sensitive-query-text"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value("RAG 检索结果格式异常，请查看后端诊断日志"));
        assertThat(logged()).contains("stage=result-mapping", "operation=mapSearchHits", "Invalid RAG result field: " + field)
                .doesNotContain("private-document", "sensitive-query-text");
    }
    @Test void wrongMetadataTypesAndNonDemoResultsNeverLeakThrough() {
        for (var invalid : List.of(Map.entry("title", 42), Map.entry("chunkIndex", "0"), Map.entry("chunkIndex", -1),
                Map.entry("chunkIndex", 0.5), Map.entry("isDemo", "true"), Map.entry("isDemo", false))) {
            var meta = metadata(); meta.put(invalid.getKey(), invalid.getValue());
            var store = mock(KnowledgeStore.class);
            when(store.search(any())).thenReturn(List.of(new Document("chunk-id", "private-document", meta)));
            assertThatThrownBy(() -> service(store).search("sensitive-query-text", 3))
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
        }
    }
    @Test void malformedSdkPayloadShowsDecoderFramesInsteadOfConnectionBlame() {
        when(client.searchAsync(any(Points.SearchPoints.class))).thenReturn(Futures.immediateFuture(List.of(
                point().toBuilder().putPayload("doc_content", ValueFactory.value(123L)).build())));
        assertThatThrownBy(() -> adapter.search(request("ragNamespace == 'researchops-rag-demo-v1'")))
                .isInstanceOf(BusinessException.class).satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(500));
        assertThat(logged()).contains("stage=vector-store-search", "QdrantVectorStore.toDocument", "ClassCastException");
    }
    @Test void unexpectedJavaExceptionIs500WithPreservedCauseAndRedactedSuppressedTrace() {
        var bad = new IllegalStateException("private-document", new NullPointerException("secret-key"));
        bad.addSuppressed(new IllegalArgumentException("sensitive-query-text"));
        var vectorMock = mock(VectorStore.class);
        when(vectorMock.similaritySearch(any(SearchRequest.class))).thenThrow(bad);
        var store = new QdrantKnowledgeStore(client, vectorMock, properties);
        assertThatThrownBy(() -> store.search(request("ragNamespace == 'researchops-rag-demo-v1'")))
                .satisfies(error -> { assertThat(((BusinessException) error).getCode()).isEqualTo(500); assertThat(error.getCause()).isSameAs(bad); });
        assertThat(logged()).contains("Caused by: java.lang.NullPointerException", "Suppressed: java.lang.IllegalArgumentException")
                .doesNotContain("secret-key", "private-document", "sensitive-query-text");
    }

    // Collection validation is tested separately; keep these regression tests completely offline.
    KnowledgeStore readOnlyCheckedStore() { return searchOnly(adapter); }
    KnowledgeStore searchOnly(QdrantKnowledgeStore searchAdapter) {
        return new KnowledgeStore() {
            public void ensureCollection(boolean create) { assertThat(create).isFalse(); }
            public Map<String, String> existingChunks(String id) { throw new AssertionError("No import"); }
            public void add(List<Document> documents) { throw new AssertionError("No write"); }
            public List<Document> search(SearchRequest request) { return searchAdapter.search(request); }
        };
    }
}
