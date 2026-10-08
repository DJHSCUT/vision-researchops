package com.djh.researchops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import static com.djh.researchops.AgentEvalAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class AgentEvalAssertionsTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final tools.jackson.databind.JsonNode expected = mapper.readTree("""
            {"expectedCalls":[
            {"tool":"queryTaskRuns","arguments":{"taskCode":"T-1","status":null},"success":true,"resultChecks":{}},
            {"tool":"queryRunMetrics","arguments":{"runCode":"R-2"},"success":true,"resultChecks":{"/runCode":"R-2"}}],
            "answerContains":["R-2","31.25"],"answerForbidden":["R-77"]}
            """);
    private List<Call> correct() { return new ArrayList<>(List.of(
            new Call("queryTaskRuns", mapper.readTree("{\"taskCode\":\"T-1\",\"status\":null}"), mapper.readTree("{\"success\":true}")),
            new Call("queryRunMetrics", mapper.readTree("{\"runCode\":\"R-2\"}"), mapper.readTree("{\"success\":true,\"runCode\":\"R-2\"}")))); }

    @Test void acceptsDifferentNaturalLanguageWithRequiredFacts() {
        assertThat(evaluate(expected, correct(), "查询结果显示 R-2 的值为 31.25 dB。").passed()).isTrue();
        assertThat(evaluate(expected, correct(), "R-2: PSNR = 31.25 dB").passed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong_tool", "wrong_argument", "wrong_order", "extra_call", "missing_call", "false_success", "wrong_field", "missing_fact", "fabricated_fact"})
    void detectsMutatedTraceOrAnswer(String mutation) {
        var calls = correct(); String answer = "R-2 PSNR 31.25 dB";
        switch (mutation) {
            case "wrong_tool" -> calls.set(1, new Call("queryRunLogs", calls.get(1).arguments(), calls.get(1).result()));
            case "wrong_argument" -> calls.set(1, new Call("queryRunMetrics", mapper.readTree("{\"runCode\":\"R-77\"}"), calls.get(1).result()));
            case "wrong_order" -> java.util.Collections.reverse(calls);
            case "extra_call" -> calls.add(calls.get(1));
            case "missing_call" -> calls.remove(1);
            case "false_success" -> calls.set(1, new Call("queryRunMetrics", calls.get(1).arguments(), mapper.readTree("{\"success\":false,\"runCode\":\"R-2\"}")));
            case "wrong_field" -> calls.set(1, new Call("queryRunMetrics", calls.get(1).arguments(), mapper.readTree("{\"success\":true,\"runCode\":\"R-77\"}")));
            case "missing_fact" -> answer = "已完成";
            case "fabricated_fact" -> answer += " R-77";
        }
        var result = evaluate(expected, calls, answer);
        assertThat(result.passed()).isFalse(); assertThat(result.failures()).isNotEmpty();
        if (mutation.equals("extra_call")) assertThat(result.extraCalls()).isEqualTo(1);
    }
}
