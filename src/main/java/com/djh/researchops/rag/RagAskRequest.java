package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RagAskRequest(@JsonDeserialize(using = StrictQuestion.class) String question,
                            @JsonDeserialize(using = StrictTopK.class) Integer topK) {
    public RagAskRequest {
        question = question == null ? null : question.strip();
        topK = topK == null ? 3 : topK;
    }

    public void validate() {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new BusinessException(400, "question 必填且不能超过 2000 个字符");
        }
        if (topK < 1 || topK > 5) throw new BusinessException(400, "topK 必须为 1–5 的整数");
    }

    // Scope strict JSON typing to this DTO; do not change coercion for existing business APIs.
    public static final class StrictQuestion extends ValueDeserializer<String> {
        @Override public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) throw new IllegalArgumentException("question must be a string");
            return parser.getString();
        }
    }
    public static final class StrictTopK extends ValueDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) throw new IllegalArgumentException("topK must be an integer");
            return parser.getIntValue();
        }
    }
}
