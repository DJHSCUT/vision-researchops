package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagDevControllerTests {
    final RagService service = mock(RagService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RagDevController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void explicitImportReturnsCountsOnlyAfterServiceSuccess() throws Exception {
        when(service.importDemo()).thenReturn(new RagService.ImportResult("researchops_knowledge_v1", RagProperties.MODEL, 1024, 5, 10, 10, 0));
        mvc.perform(post("/dev/rag/demo/import")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimensions").value(1024)).andExpect(jsonPath("$.data.addedCount").value(10));
    }
    @Test void getSearchReturnsSourceTextAndScoreWithDefaultTopK() throws Exception {
        when(service.search("视角覆盖不足", 3)).thenReturn(List.of(new RagService.SearchHit("chunk", "doc", "file:///demo.md",
                "DEMO：覆盖", 0, true, "视角覆盖不足可能造成重建缺失。", 0.8)));
        mvc.perform(get("/dev/rag/search").param("query", "视角覆盖不足")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].source").value("file:///demo.md"))
                .andExpect(jsonPath("$.data[0].content").value("视角覆盖不足可能造成重建缺失。"))
                .andExpect(jsonPath("$.data[0].score").value(0.8));
    }
    @Test void externalFailureReturns503AndNoSuccessPayload() throws Exception {
        when(service.importDemo()).thenThrow(new BusinessException(503, "Ollama Embedding 不可用"));
        mvc.perform(post("/dev/rag/demo/import")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503)).andExpect(jsonPath("$.message").value("Ollama Embedding 不可用"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
