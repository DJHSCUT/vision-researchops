package com.djh.researchops.rag;

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QdrantKnowledgeStoreTests {
    final QdrantClient client = mock(QdrantClient.class);
    final VectorStore vectors = mock(VectorStore.class);
    final QdrantKnowledgeStore store = new QdrantKnowledgeStore(client, vectors,
            DemoDocumentLoaderTests.properties(Path.of("docs/rag-demo")));
    final String collection = "researchops_knowledge_v1";

    Collections.VectorsConfig config(int dimensions, Collections.Distance distance) {
        return Collections.VectorsConfig.newBuilder().setParams(Collections.VectorParams.newBuilder()
                .setSize(dimensions).setDistance(distance)).build();
    }
    void collectionInfo(int dimensions, Collections.Distance distance) {
        var info = Collections.CollectionInfo.newBuilder().setConfig(Collections.CollectionConfig.newBuilder()
                .setParams(Collections.CollectionParams.newBuilder().setVectorsConfig(config(dimensions, distance)))).build();
        when(client.getCollectionInfoAsync(collection)).thenReturn(Futures.immediateFuture(info));
    }
    @Test void acceptsExistingCompatibleCollectionWithoutCreatingOrDeleting() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFuture(true));
        collectionInfo(1024, Collections.Distance.Cosine);
        store.ensureCollection(true);
        verify(client).collectionExistsAsync(collection);
        verify(client).getCollectionInfoAsync(collection);
        verifyNoMoreInteractions(client);
    }
    @Test void createsOnlyMissingCollectionWith1024Cosine() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFuture(false));
        when(client.createCollectionAsync(eq(collection), any(Collections.VectorParams.class)))
                .thenReturn(Futures.immediateFuture(Collections.CollectionOperationResponse.getDefaultInstance()));
        collectionInfo(1024, Collections.Distance.Cosine);
        store.ensureCollection(true);
        var captor = ArgumentCaptor.forClass(Collections.VectorParams.class);
        verify(client).createCollectionAsync(eq(collection), captor.capture());
        assertThat(captor.getValue().getSize()).isEqualTo(1024);
        assertThat(captor.getValue().getDistance()).isEqualTo(Collections.Distance.Cosine);
    }
    @Test void searchNeverCreatesMissingCollection() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFuture(false));
        assertThatThrownBy(() -> store.ensureCollection(false)).hasMessageContaining("先显式导入");
        verify(client).collectionExistsAsync(collection);
        verifyNoMoreInteractions(client);
    }
    @Test void incompatibleDimensionStopsWithoutMutation() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFuture(true));
        collectionInfo(768, Collections.Distance.Cosine);
        assertThatThrownBy(() -> store.ensureCollection(true)).hasMessageContaining("不兼容");
        verify(client).collectionExistsAsync(collection);
        verify(client).getCollectionInfoAsync(collection);
        verifyNoMoreInteractions(client);
    }
    @Test void rejectsDifferentDistanceAndNamedVectors() {
        assertThatThrownBy(() -> QdrantKnowledgeStore.validate(config(1024, Collections.Distance.Dot)))
                .hasMessageContaining("Cosine");
        assertThatThrownBy(() -> QdrantKnowledgeStore.validate(Collections.VectorsConfig.newBuilder()
                .setParamsMap(Collections.VectorParamsMap.newBuilder().putMap("named",
                        Collections.VectorParams.newBuilder().setSize(1024).setDistance(Collections.Distance.Cosine).build())).build()))
                .hasMessageContaining("未命名");
    }
    @Test void collectionCreationRaceValidatesWinnerWithoutRecreation() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFuture(false), Futures.immediateFuture(true));
        when(client.createCollectionAsync(eq(collection), any(Collections.VectorParams.class)))
                .thenReturn(Futures.immediateFailedFuture(new IllegalStateException("already exists")));
        collectionInfo(1024, Collections.Distance.Cosine);
        store.ensureCollection(true);
        verify(client, times(1)).createCollectionAsync(eq(collection), any(Collections.VectorParams.class));
    }
    @Test void scrollsAllPagesAndUsesOriginalDocumentFilter() {
        var id1 = Common.PointId.newBuilder().setUuid("00000000-0000-0000-0000-000000000001").build();
        var id2 = Common.PointId.newBuilder().setUuid("00000000-0000-0000-0000-000000000002").build();
        var point1 = Points.RetrievedPoint.newBuilder().setId(id1).putPayload("importFingerprint", ValueFactory.value("hash")).build();
        var point2 = Points.RetrievedPoint.newBuilder().setId(id2).putPayload("importFingerprint", ValueFactory.value("hash")).build();
        when(client.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(
                Futures.immediateFuture(Points.ScrollResponse.newBuilder().addResult(point1).setNextPageOffset(id2).build()),
                Futures.immediateFuture(Points.ScrollResponse.newBuilder().addResult(point2).build()));
        assertThat(store.existingChunks("document-id")).containsEntry(id1.getUuid(), "hash").containsEntry(id2.getUuid(), "hash");
        var captor = ArgumentCaptor.forClass(Points.ScrollPoints.class);
        verify(client, times(2)).scrollAsync(captor.capture());
        assertThat(captor.getAllValues().get(0).getFilter().getMust(0).getField().getKey()).isEqualTo("documentId");
        assertThat(captor.getAllValues().get(1).getOffset()).isEqualTo(id2);
    }
    @Test void unavailableQdrantNeverLooksLikeMissingCollection() {
        when(client.collectionExistsAsync(collection)).thenReturn(Futures.immediateFailedFuture(new IllegalStateException("connection refused")));
        assertThatThrownBy(() -> store.ensureCollection(true)).hasMessageContaining("Qdrant 操作失败").hasMessageContaining("6334");
        verify(client).collectionExistsAsync(collection);
        verifyNoMoreInteractions(client);
    }
}
