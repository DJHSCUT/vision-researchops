package com.djh.researchops.rag;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

public final class RagAskService {
    private static final String INSUFFICIENT = "当前知识库资料不足，或回答未通过引用校验，无法给出可靠回答。现有资料为 DEMO 教学示例。";
    private static final String DEMO_NOTICE = "以下基于 DEMO 教学资料，不代表真实实验结论、论文原文或官方规范。\n\n";
    private final RagService retrieval;
    private final ChatModel model;
    private final RagPromptContextBuilder contextBuilder;
    private final RagCitationValidator citations;
    private final RagAskProperties properties;
    private final String collection;

    public RagAskService(RagService retrieval, ChatModel model, RagPromptContextBuilder contextBuilder,
                         RagCitationValidator citations, RagAskProperties properties, String collection) {
        this.retrieval = retrieval;
        this.model = model;
        this.contextBuilder = contextBuilder;
        this.citations = citations;
        this.properties = properties;
        this.collection = collection;
    }

    public RagAskResponse ask(RagAskRequest request) {
        request.validate();
        var hits = retrieval.search(request.question(), request.topK());
        var context = contextBuilder.build(request.question(), hits);
        if (context.contextSources().isEmpty()) return insufficient(context, hits.isEmpty() ? "NO_RETRIEVAL" : "NO_QUALIFIED_EVIDENCE");
        ChatResponse response;
        try {
            // Spring AI 2.0.1 OpenAiChatModel requires provider-specific options.
            // Copy configured model/transport settings instead of replacing them or mutating the shared bean.
            var defaults = model.getDefaultOptions();
            var options = (defaults instanceof OpenAiChatOptions openAi ? openAi.mutate() : OpenAiChatOptions.builder())
                    .temperature(0.0).maxTokens(properties.maxOutputTokens())
                    .toolCallbacks(List.of()).toolContext(java.util.Map.of()).toolChoice("none").build();
            // Reuse the existing ChatModel. Direct messages avoid template interpolation of untrusted JSON.
            // No Tools, tool callbacks, memory, advisors, or auto-routing are attached to this request.
            response = model.call(new Prompt(List.of(new SystemMessage(context.system()), new UserMessage(context.user())),
                    options));
        } catch (RuntimeException failure) {
            throw RagDiagnostics.failure("rag-answer-generation", collection, "deepSeekChat", failure, 503,
                    "DeepSeek 问答服务暂时不可用，请稍后重试");
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw RagDiagnostics.failure("rag-answer-generation", collection, "validateChatResponse",
                    new IllegalStateException("Missing chat response"), 502, "DeepSeek 返回格式异常，请稍后重试");
        }
        if (response.hasToolCalls()) return insufficient(context, "INVALID_MODEL_OUTPUT");
        String answer = response.getResult().getOutput().getText();
        if (answer == null || answer.isBlank() || answer.length() > properties.maxAnswerChars()
                || RagPromptContextBuilder.ABSOLUTE_PATH.matcher(answer).find()) return insufficient(context, "INVALID_MODEL_OUTPUT");
        answer = answer.strip();
        if (answer.equals("INSUFFICIENT_EVIDENCE")) return insufficient(context, "MODEL_INSUFFICIENT_EVIDENCE");
        var validation = citations.validate(answer, context.contextSources());
        if (!validation.valid()) return insufficient(context, "CITATION_VALIDATION_FAILED");
        return new RagAskResponse(DEMO_NOTICE + answer, validation.citedSources(), true, false,
                context.retrievedCount(), context.retrievedSources(), "CITATIONS_VALIDATED");
    }

    private RagAskResponse insufficient(RagPromptContextBuilder.PreparedContext context, String reason) {
        return new RagAskResponse(INSUFFICIENT, List.of(), false, true, context.retrievedCount(), context.retrievedSources(), reason);
    }
}
