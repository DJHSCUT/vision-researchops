package com.djh.researchops.rag;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedDimensionEmbeddingModelTests {
    @Test void pinsModelAndEmbedsOnlyTextWithNoStartupProbe() {
        var delegate = mock(EmbeddingModel.class);
        var wrapper = new FixedDimensionEmbeddingModel(delegate);
        assertThat(wrapper.dimensions()).isEqualTo(1024);
        verifyNoInteractions(delegate);
        float[] vector = new float[1024]; vector[0] = 1;
        when(delegate.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(vector, 0))));
        assertThat(wrapper.embed(new Document("文本", Map.of("source", "hidden-metadata")))).isEqualTo(vector);
        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(delegate).call(captor.capture());
        assertThat(captor.getValue().getInstructions()).containsExactly("文本");
        var options = (OllamaEmbeddingOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo(RagProperties.MODEL);
        assertThat(options.getTruncate()).isFalse();
    }
    @ParameterizedTest @ValueSource(ints = {0, 768, 1023, 1025})
    void rejectsWrongDimensions(int dimensions) {
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[dimensions], 0))));
        assertThatThrownBy(() -> new FixedDimensionEmbeddingModel(delegate).embed("问题")).hasMessageContaining("必须为 1024");
    }
    @Test void rejectsZeroAndNonFiniteVectors() {
        var delegate = mock(EmbeddingModel.class);
        float[] vector = new float[1024];
        when(delegate.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(vector, 0))));
        var model = new FixedDimensionEmbeddingModel(delegate);
        assertThatThrownBy(() -> model.embed("问题")).hasMessageContaining("零向量");
        vector[0] = Float.NaN;
        assertThatThrownBy(() -> model.embed("问题")).hasMessageContaining("非有限");
    }
    @Test void rejectsMissingBatchResults() {
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any())).thenReturn(new EmbeddingResponse(List.of()));
        assertThatThrownBy(() -> new FixedDimensionEmbeddingModel(delegate).embed("问题")).hasMessageContaining("数量");
    }
    @Test void unavailableOllamaProducesClearErrorWithoutLeakingRawResponse() {
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any())).thenThrow(new IllegalStateException("sensitive raw response"));
        assertThatThrownBy(() -> new FixedDimensionEmbeddingModel(delegate).embed("问题"))
                .hasMessageContaining("Ollama Embedding 不可用").hasMessageNotContaining("sensitive");
    }
}
