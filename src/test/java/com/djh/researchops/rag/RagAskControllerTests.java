package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagAskControllerTests {
    @TempDir Path root;
    final RagService retrieval = mock(RagService.class);
    final ChatModel model = mock(ChatModel.class);
    MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        var properties = RagAskTestSupport.properties(root);
        var service = new RagAskService(retrieval, model, RagAskTestSupport.builder(root, properties),
                new RagCitationValidator(), properties, "researchops_knowledge_v1");
        mvc = MockMvcBuilders.standaloneSetup(new RagAskController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("观测不足可能造成缺失[1]。"));
    }
    @Test void validJsonReturnsProjectRelativeSourcesAndExplicitGroundingFlags() throws Exception {
        var result = mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  问题  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.grounded").value(true)).andExpect(jsonPath("$.data.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.data.sources[0].citationId").value(1))
                .andExpect(jsonPath("$.data.sources[0].source").value("docs/rag-demo/doc-0.md"))
                .andExpect(jsonPath("$.data.sources[0].score").value(.8)).andExpect(jsonPath("$.data.sources[0].isDemo").value(true))
                .andExpect(jsonPath("$.data.retrievedCount").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain(root.toString(), "file:///", "C:\\Users");
        verify(retrieval).search("问题", 3);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\" \"}",
            "{\"question\":42}", "{\"question\":{}}", "{\"question\":\"问题\",\"topK\":0}",
            "{\"question\":\"问题\",\"topK\":6}", "{\"question\":\"问题\",\"topK\":3.5}",
            "{\"question\":\"问题\",\"topK\":\"3\"}", "{\"question\":\"问题\",\"topK\":true}",
            "{\"question\":\"问题\",\"topK\":2147483648}", "{broken", "null", ""})
    void invalidJsonOrParametersReturnSafeApiResponse400BeforeAnyExternalWork(String body) throws Exception {
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(retrieval, model);
    }
    @Test void excessiveQuestionReturns400() throws Exception {
        String body = new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.Map.of("question", "甲".repeat(2001)));
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(retrieval, model);
    }
    @Test void explicitNullTopKUsesDefault() throws Exception {
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\",\"topK\":null}"))
                .andExpect(status().isOk());
        verify(retrieval).search("问题", 3);
    }
    @Test void nonLoopbackCannotUseTheDevelopmentEndpointEvenWithSpoofedForwardingHeader() throws Exception {
        mvc.perform(post("/dev/rag/ask").with(request -> { request.setRemoteAddr("10.0.0.8"); return request; })
                .header("X-Forwarded-For", "127.0.0.1").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(retrieval, model);
    }
    @ParameterizedTest @ValueSource(strings = {"Forwarded", "X-Forwarded-For", "X-Real-IP"})
    void forwardedRequestsAreRejectedInsteadOfTrustingProxyHeaders(String header) throws Exception {
        mvc.perform(post("/dev/rag/ask").header(header, "127.0.0.1").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(retrieval, model);
    }
    @Test void knowledgeInsufficiencyIs200WithEmptyCitedSources() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of());
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"无资料问题\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.data.grounded").value(false)).andExpect(jsonPath("$.data.sources").isEmpty());
        verifyNoInteractions(model);
    }
    @Test void deepSeekFailureIs503WithNoInsufficiencyPayload() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("private-model-response secret-key"));
        var result = mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.data").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("private-model-response", "secret-key", "IllegalStateException", "insufficientEvidence");
    }
    @Test void qdrantOrOllamaFailureRemains503WithoutModelCall() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenThrow(new BusinessException(503, "检索依赖暂时不可用"));
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(model);
    }
    @Test void malformedQdrantMetadataIs502InsteadOfAnInsufficiencyClaim() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenThrow(new BusinessException(502, "RAG 检索结果格式异常"));
        mvc.perform(post("/dev/rag/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"问题\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(model);
    }
}
