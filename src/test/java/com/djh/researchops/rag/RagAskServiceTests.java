package com.djh.researchops.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.djh.researchops.exception.BusinessException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagAskServiceTests {
    @TempDir Path root;
    final RagService retrieval = mock(RagService.class);
    final ChatModel model = mock(ChatModel.class);
    RagAskService service;

    @BeforeEach void setUp() {
        var properties = RagAskTestSupport.properties(root);
        service = new RagAskService(retrieval, model, RagAskTestSupport.builder(root, properties),
                new RagCitationValidator(), properties, "researchops_knowledge_v1");
    }
    @Test void normalQuestionUsesExistingRetrievalThenChatAndReturnsOnlyActuallyCitedSources() throws Exception {
        when(retrieval.search("为什么缺失？", 3)).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8),
                RagAskTestSupport.hit(root, 1, .75), RagAskTestSupport.hit(root, 2, .7)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("缺少多视角观测约束，可能出现局部缺失[2]。"));
        var result = service.ask(new RagAskRequest("  为什么缺失？  ", null));
        assertThat(result.grounded()).isTrue();
        assertThat(result.insufficientEvidence()).isFalse();
        assertThat(result.answer()).contains("DEMO", "[2]");
        assertThat(result.sources()).extracting(RagAskResponse.Source::citationId).containsExactly(2);
        assertThat(result.sources().get(0).source()).isEqualTo("docs/rag-demo/doc-1.md");
        assertThat(result.retrievedSources()).hasSize(3).allMatch(s -> s.usedInContext() && s.isDemo());
        assertThat(result.retrievedCount()).isEqualTo(3);
        var order = inOrder(retrieval, model);
        order.verify(retrieval).search("为什么缺失？", 3);
        order.verify(model).call(any(Prompt.class));
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var prompt = captor.getValue();
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(prompt.getInstructions().get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(prompt.getInstructions().get(1).getText()).contains("为什么缺失？", "\"citationId\":1", "\"citationId\":3", "\"isDemo\":true")
                .doesNotContain(root.toString(), "file:///");
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(1024);
    }
    @Test void multipleCitationsPreserveFirstMentionOrderAndRemoveDuplicateReferences() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8), RagAskTestSupport.hit(root, 1, .7)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("先说明遮挡[2]，再说明覆盖[1][2]。"));
        assertThat(service.ask(new RagAskRequest("问题", 2)).sources()).extracting(RagAskResponse.Source::citationId).containsExactly(2, 1);
    }
    @Test void requestOptionsCopyDeepSeekDefaultsWithoutInheritingToolsOrMutatingSharedModel() throws Exception {
        var callback = mock(org.springframework.ai.tool.ToolCallback.class);
        var defaults = org.springframework.ai.openai.OpenAiChatOptions.builder().model("configured-deepseek")
                .baseUrl("http://example.test").toolCallbacks(List.of(callback)).build();
        when(model.getDefaultOptions()).thenReturn(defaults);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("说明[1]。"));
        assertThat(service.ask(new RagAskRequest("问题", 1)).grounded()).isTrue();
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var copied = (org.springframework.ai.openai.OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(copied.getModel()).isEqualTo("configured-deepseek");
        assertThat(copied.getToolCallbacks()).isEmpty();
        assertThat(copied.getToolChoice()).isEqualTo("none");
        assertThat(defaults.getToolCallbacks()).containsExactly(callback);
        verifyNoInteractions(callback);
    }
    @ParameterizedTest @ValueSource(strings = {"无引用回答", "结论[9]", "结论[1][9]", "结论[1,2]", "结论【1】", "结论[01]", "结论[1", "结论[1]]"})
    void invalidOrMissingCitationNeverReturnsUnverifiedGeneratedAnswer(String answer) throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer(answer));
        var result = service.ask(new RagAskRequest("问题", 1));
        assertThat(result.grounded()).isFalse();
        assertThat(result.insufficientEvidence()).isTrue();
        assertThat(result.sources()).isEmpty();
        assertThat(result.evidenceStatus()).isEqualTo("CITATION_VALIDATION_FAILED");
        assertThat(result.answer()).doesNotContain(answer);
    }
    @Test void emptyRetrievalDoesNotCallDeepSeek() {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of());
        var result = service.ask(new RagAskRequest("无资料的问题", 3));
        assertThat(result.retrievedCount()).isZero();
        assertThat(result.evidenceStatus()).isEqualTo("NO_RETRIEVAL");
        assertThat(result.insufficientEvidence()).isTrue();
        verifyNoInteractions(model);
    }
    @Test void lowScoresRemainRetrievedButAreNotContextOrCitedEvidence() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .59), RagAskTestSupport.hit(root, 1, .3)));
        var result = service.ask(new RagAskRequest("无关问题", 2));
        assertThat(result.retrievedCount()).isEqualTo(2);
        assertThat(result.retrievedSources()).hasSize(2).noneMatch(RagAskResponse.Source::usedInContext);
        assertThat(result.sources()).isEmpty();
        assertThat(result.insufficientEvidence()).isTrue();
        verifyNoInteractions(model);
    }
    @Test void lowScoreCitationIsRejectedEvenWhenItsTargetWasRetrieved() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8), RagAskTestSupport.hit(root, 1, .3)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("错误使用低分证据[2]。"));
        var result = service.ask(new RagAskRequest("问题", 2));
        assertThat(result.sources()).isEmpty();
        assertThat(result.grounded()).isFalse();
        assertThat(result.retrievedSources().get(1).usedInContext()).isFalse();
    }
    @Test void invalidMetadataIsNotSentToModelOrExposedAsSource() {
        var invalid = new RagService.SearchHit("chunk", "doc", "file:///C:/Users/private/secret.md", null, -1, true, "private-content", .9);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(invalid));
        var result = service.ask(new RagAskRequest("问题", 1));
        assertThat(result.retrievedCount()).isEqualTo(1);
        assertThat(result.retrievedSources()).isEmpty();
        assertThat(result.insufficientEvidence()).isTrue();
        assertThat(result.answer()).doesNotContain("Users", "private-content");
        verifyNoInteractions(model);
    }
    @Test void modelCanExplicitlyRefuseWhenContextDoesNotAnswerQuestion() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("INSUFFICIENT_EVIDENCE"));
        var result = service.ask(new RagAskRequest("没有覆盖的问题", 1));
        assertThat(result.evidenceStatus()).isEqualTo("MODEL_INSUFFICIENT_EVIDENCE");
        assertThat(result.grounded()).isFalse();
        assertThat(result.sources()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {" ", "内部路径 C:\\Users\\private\\secret.md [1]", "file:///C:/private.md [1]"})
    void unsafeOrBlankOutputIsNotReturned(String output) throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer(output));
        assertThat(service.ask(new RagAskRequest("问题", 1)).evidenceStatus()).isEqualTo("INVALID_MODEL_OUTPUT");
    }
    @Test void excessiveModelOutputIsRejected() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(RagAskTestSupport.answer("甲".repeat(6001) + "[1]"));
        assertThat(service.ask(new RagAskRequest("问题", 1)).sources()).isEmpty();
    }
    @Test void toolCallOutputCannotBeExecutedOrAcceptedAsGroundedAnswer() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        var message = AssistantMessage.builder().content("执行命令[1]").toolCalls(List.of(
                new AssistantMessage.ToolCall("id", "function", "queryRunMetrics", "{}"))).build();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(message))));
        var result = service.ask(new RagAskRequest("问题", 1));
        assertThat(result.evidenceStatus()).isEqualTo("INVALID_MODEL_OUTPUT");
        verify(model, times(1)).call(any(Prompt.class));
    }
    @Test void deepSeekExceptionIsServiceFailureNotKnowledgeInsufficiencyAndLogsAreRedacted() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("secret-api-key private-prompt"));
        var logger = (Logger) LoggerFactory.getLogger(RagDiagnostics.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        try {
            assertThatThrownBy(() -> service.ask(new RagAskRequest("问题", 1))).isInstanceOf(BusinessException.class)
                    .hasMessage("DeepSeek 问答服务暂时不可用，请稍后重试")
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(503));
            String logged = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logged).contains("stage=rag-answer-generation", "operation=deepSeekChat", "IllegalStateException")
                    .doesNotContain("secret-api-key", "private-prompt");
        } finally { logger.detachAppender(logs); logs.stop(); }
    }
    @Test void malformedModelResponseIs502NotKnowledgeInsufficiency() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(RagAskTestSupport.hit(root, 0, .8)));
        when(model.call(any(Prompt.class))).thenReturn(null);
        assertThatThrownBy(() -> service.ask(new RagAskRequest("问题", 1)))
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }
    @ParameterizedTest @ValueSource(ints = {503, 502, 409})
    void retrievalFaultsArePreservedAndDoNotCallDeepSeek(int code) {
        var failure = new BusinessException(code, "检索依赖或数据异常");
        when(retrieval.search(anyString(), anyInt())).thenThrow(failure);
        assertThatThrownBy(() -> service.ask(new RagAskRequest("问题", 3))).isSameAs(failure);
        verifyNoInteractions(model);
    }
    @Test void invalidRequestFailsBeforeRetrievalAndChat() {
        assertThatThrownBy(() -> service.ask(new RagAskRequest(" ", 3))).hasMessageContaining("question");
        assertThatThrownBy(() -> service.ask(new RagAskRequest("甲".repeat(2001), 3))).hasMessageContaining("2000");
        assertThatThrownBy(() -> service.ask(new RagAskRequest("问题", 6))).hasMessageContaining("topK");
        verifyNoInteractions(retrieval, model);
    }
}
