package com.djh.researchops;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.stream.StreamSupport;

/** 可确定的执行轨迹检查；不对自然语言答案做全文相等比较。 */
final class AgentEvalAssertions {
    record Call(String tool, JsonNode arguments, JsonNode result) { }
    record Evaluation(Map<String, Boolean> metrics, List<String> failures, List<String> expected,
                      List<String> actual, int extraCalls) {
        boolean passed() { return failures.isEmpty(); }
    }
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static Evaluation evaluate(JsonNode testCase, List<Call> calls, String answer) {
        List<JsonNode> expectedCalls = nodes(testCase.get("expectedCalls"));
        List<String> expected = expectedCalls.stream().map(c -> c.get("tool").asString()).toList();
        List<String> actual = calls.stream().map(Call::tool).toList();
        Map<String, Boolean> metrics = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        check(metrics, failures, "Tool Selection Accuracy", frequencies(expected).equals(frequencies(actual)), "工具种类或调用次数不符");
        check(metrics, failures, "Tool Sequence Correctness", expected.equals(actual), "预期=" + expected + ", 实际=" + actual);
        int extras = frequencies(actual).entrySet().stream().mapToInt(e -> Math.max(0, e.getValue() - frequencies(expected).getOrDefault(e.getKey(), 0))).sum();
        check(metrics, failures, "Unnecessary Tool Calls", extras == 0, "额外调用=" + extras);
        boolean arguments = calls.size() == expectedCalls.size();
        boolean errors = calls.size() == expectedCalls.size();
        for (int i = 0; i < Math.min(calls.size(), expectedCalls.size()); i++) {
            JsonNode expectedCall = expectedCalls.get(i);
            Call call = calls.get(i);
            if (!expectedCall.get("arguments").equals(call.arguments())) {
                arguments = false;
                failures.add("参数不符，调用 " + (i + 1) + ": expected=" + expectedCall.get("arguments") + ", actual=" + call.arguments());
            }
            if (call.result().get("success").asBoolean() != expectedCall.get("success").asBoolean()) errors = false;
            Map<String, Object> checks = MAPPER.convertValue(expectedCall.get("resultChecks"), Map.class);
            for (var entry : checks.entrySet()) {
                JsonNode value = call.result().at(entry.getKey());
                JsonNode wanted = MAPPER.valueToTree(entry.getValue());
                boolean equal = value.isNumber() && wanted.isNumber() ? value.asDouble() == wanted.asDouble() : value.equals(wanted);
                if (!equal) { errors = false; failures.add("结果字段不符，调用 " + (i + 1) + " " + entry.getKey()); }
            }
        }
        check(metrics, failures, "Tool Argument Correctness", arguments, "参数或调用次数不符");
        check(metrics, failures, "Error Handling", errors, "失败、停止或重试的结果轨迹不符");
        boolean completion = answer != null && !answer.isBlank();
        for (JsonNode token : testCase.get("answerContains")) completion &= answer != null && answer.contains(token.asString());
        for (JsonNode token : testCase.get("answerForbidden")) completion &= answer != null && !answer.contains(token.asString());
        check(metrics, failures, "Task Completion", completion, "答案缺少必要业务字段或出现禁止结果");
        return new Evaluation(metrics, failures, expected, actual, extras);
    }

    private static void check(Map<String, Boolean> metrics, List<String> failures, String name, boolean passed, String reason) {
        metrics.put(name, passed);
        if (!passed) failures.add(name + ": " + reason);
    }
    private static Map<String, Integer> frequencies(List<String> names) {
        Map<String, Integer> counts = new HashMap<>(); names.forEach(name -> counts.merge(name, 1, Integer::sum)); return counts;
    }
    static List<JsonNode> nodes(JsonNode node) { return StreamSupport.stream(node.spliterator(), false).toList(); }
}
