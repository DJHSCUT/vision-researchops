package com.djh.researchops;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.*;
import com.djh.researchops.tool.*;
import com.djh.researchops.util.AiRequestLogContext;
import com.djh.researchops.util.ToolInvocationLog;
import com.djh.researchops.vo.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static com.djh.researchops.AgentEvalAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 仅本机脚本模型；Spring AI 执行真实 Tool Calling，不测量 DeepSeek 的选择准确率。 */
class AgentEvaluationTests {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final List<Map<String, Object>> REPORT = new ArrayList<>();
    private final List<Call> actual = new ArrayList<>();
    private ResearchProjectService projects;
    private ExperimentTaskService tasks;
    private ExperimentRunService runs;
    private ExperimentMetricService metrics;
    private ExperimentLogService logs;
    private ResultArtifactService artifacts;

    static Stream<Named<JsonNode>> cases() throws Exception {
        try (var stream = AgentEvaluationTests.class.getResourceAsStream("/agent-eval/cases.json")) {
            if (stream == null) throw new IllegalStateException("agent-eval/cases.json missing");
            return nodes(MAPPER.readTree(stream)).stream().map(testCase -> Named.of(testCase.get("id").asString(), testCase));
        }
    }

    @BeforeAll static void clearReport() { REPORT.clear(); }

    @ParameterizedTest(name = "agent-eval {index}: {0}")
    @MethodSource("cases")
    void evaluatesOfflineSpringAiExecution(JsonNode testCase) throws Exception {
        fixtures(testCase.get("fixture").asString());
        var projectTool = spy(new ProjectTaskTools(projects, tasks));
        var taskTool = spy(new TaskRunTools(runs, tasks));
        var metricTool = spy(new RunMetricTools(metrics, runs));
        var logTool = spy(new RunLogTools(logs, runs));
        var artifactTool = spy(new RunArtifactTools(artifacts, runs));
        doAnswer(this::capture).when(projectTool).queryProjectTasks(nullable(String.class), nullable(String.class));
        doAnswer(this::capture).when(taskTool).queryTaskRuns(nullable(String.class), nullable(String.class));
        doAnswer(this::capture).when(metricTool).queryRunMetrics(nullable(String.class));
        doAnswer(this::capture).when(logTool).queryRunLogs(nullable(String.class), nullable(String.class));
        doAnswer(this::capture).when(artifactTool).queryRunArtifacts(nullable(String.class), nullable(String.class));
        var runner = new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean(ProjectTaskTools.class, () -> projectTool).withBean(TaskRunTools.class, () -> taskTool)
                .withBean(RunMetricTools.class, () -> metricTool).withBean(RunLogTools.class, () -> logTool)
                .withBean(RunArtifactTools.class, () -> artifactTool)
                .withUserConfiguration(AiProfileTests.AutoConfigurationOnly.class, AiChatService.class);
        Logger logger = (Logger) LoggerFactory.getLogger(ToolInvocationLog.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        String answer = null;
        Evaluation evaluation = null;
        String failure = null;
        try (var model = new ScriptedModel(testCase)) {
            AtomicReference<String> output = new AtomicReference<>();
            runner.withPropertyValues("spring.profiles.active=ai", "LLM_API_KEY=test-placeholder-not-a-real-key",
                    "LLM_MODEL=deepseek-flash", "LLM_BASE_URL=" + model.baseUrl()).run(context -> {
                assertThat(context).hasNotFailed();
                output.set(context.getBean(AiChatService.class).chat(testCase.get("question").asString()).getContent());
            });
            answer = output.get();
            assertThat(model.failure.get()).isNull();
            assertThat(model.round).isEqualTo(actual.size() + 1);
            evaluation = evaluate(testCase, actual, answer);
            assertThat(evaluation.failures()).isEmpty();
            assertThat(appender.list).hasSize(actual.size());
            List<String> requestIds = appender.list.stream().map(e -> e.getMDCPropertyMap().get(AiRequestLogContext.REQUEST_ID)).distinct().toList();
            if (!actual.isEmpty()) { assertThat(requestIds).hasSize(1); assertThat(requestIds.get(0)).isNotBlank().isNotEqualTo("-"); }
            assertThat(MDC.get(AiRequestLogContext.REQUEST_ID)).isNull();
            if (testCase.get("id").asString().equals("retry_invalid_status")) {
                assertThat(appender.list.get(0).getFormattedMessage()).contains("result=INVALID_ARGUMENT");
                assertThat(appender.list.get(1).getFormattedMessage()).contains("result=SUCCESS");
                assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("status=ACTIVE");
                assertThat(appender.list.get(0).getArgumentArray()[1]).isNotEqualTo(appender.list.get(1).getArgumentArray()[1]);
                verify(projects, times(1)).getByProjectCode("P-1");
                verify(tasks, times(1)).getByProjectId(707L, null);
            }
            if (actual.isEmpty()) verifyNoInteractions(projects, tasks, runs, metrics, logs, artifacts);
        } catch (Throwable error) {
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            throw error;
        } finally {
            logger.detachAppender(appender); appender.stop();
            if (evaluation == null) evaluation = evaluate(testCase, actual, answer);
            var row = new LinkedHashMap<String, Object>();
            row.put("case", testCase.get("id").asString()); row.put("expected", evaluation.expected()); row.put("actual", evaluation.actual());
            row.put("extraCalls", evaluation.extraCalls()); row.put("metrics", evaluation.metrics());
            row.put("result", failure == null && evaluation.passed() ? "PASS" : "FAIL");
            row.put("failureReasons", failure == null ? evaluation.failures() : List.of(failure));
            row.put("callResults", actual.stream().map(c -> Map.of("tool", c.tool(), "success", c.result().get("success").asBoolean())).toList());
            REPORT.add(row);
        }
    }

    private Object capture(InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(name.equals("queryProjectTasks") ? "projectCode" : name.equals("queryTaskRuns") ? "taskCode" : "runCode", invocation.getArgument(0));
        if (invocation.getArguments().length == 2) arguments.put(name.equals("queryRunLogs") ? "level" : name.equals("queryRunArtifacts") ? "type" : "status", invocation.getArgument(1));
        Object result = invocation.callRealMethod();
        actual.add(new Call(name, MAPPER.valueToTree(arguments), MAPPER.valueToTree(result)));
        return result;
    }

    @AfterAll static void writeReport() throws Exception {
        Path directory = Path.of("target", "agent-eval"); Files.createDirectories(directory);
        long passed = REPORT.stream().filter(row -> row.get("result").equals("PASS")).count();
        var summary = new LinkedHashMap<String, Object>(); summary.put("mode", "OFFLINE_SCRIPTED_MODEL");
        summary.put("total", REPORT.size()); summary.put("passed", passed); summary.put("failed", REPORT.size() - passed); summary.put("cases", REPORT);
        Files.writeString(directory.resolve("summary.json"), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
        StringBuilder markdown = new StringBuilder("# Agent Evaluation V1 — offline scripted model\n\nThis report verifies Spring AI execution and scripted fixtures, not DeepSeek selection accuracy.\n\n");
        markdown.append("Total: ").append(REPORT.size()).append("; Passed: ").append(passed).append("; Failed: ").append(REPORT.size() - passed).append("\n\n");
        for (var row : REPORT) {
            markdown.append("Case: ").append(row.get("case")).append("\n\nExpected: ").append(row.get("expected"))
                    .append("\n\nActual: ").append(row.get("actual")).append("\n\nResult: ").append(row.get("result"))
                    .append("; Extra calls: ").append(row.get("extraCalls")).append("\n\nMetrics: ").append(row.get("metrics"))
                    .append("\n\nCall results: ").append(row.get("callResults")).append("\n\nFailure reasons: ").append(row.get("failureReasons")).append("\n\n---\n\n");
        }
        Files.writeString(directory.resolve("summary.md"), markdown.toString());
        System.out.println("Agent Evaluation V1: total=" + REPORT.size() + " passed=" + passed + " failed=" + (REPORT.size() - passed) + " report=" + directory.resolve("summary.md"));
    }

    private void fixtures(String fixture) {
        projects = mock(ResearchProjectService.class); tasks = mock(ExperimentTaskService.class); runs = mock(ExperimentRunService.class);
        metrics = mock(ExperimentMetricService.class); logs = mock(ExperimentLogService.class); artifacts = mock(ResultArtifactService.class);
        var project = new ResearchProjectVO(); project.setId(707L); project.setProjectCode("P-1"); when(projects.getByProjectCode("P-1")).thenReturn(project);
        when(projects.getByProjectCode("P-99999")).thenThrow(new BusinessException(404, "研究项目不存在"));
        LocalDateTime newest = LocalDateTime.of(2026, 1, 3, 12, 0);
        var task = new ExperimentTaskVO(); task.setId(303L); task.setProjectId(707L); task.setTaskCode("T-1"); task.setName("真实任务"); task.setStatus("TODO"); task.setCreatedAt(newest);
        var olderTask = new ExperimentTaskVO(); olderTask.setId(999L); olderTask.setTaskCode("T-77"); olderTask.setName("更旧任务"); olderTask.setStatus("TODO"); olderTask.setCreatedAt(newest.minusDays(1));
        if (fixture.equals("task_tie")) { olderTask.setCreatedAt(newest); olderTask.setId(202L); }
        when(tasks.getByProjectId(707L, null)).thenReturn(fixture.equals("empty_project") ? List.of() : fixture.equals("task_tie") ? List.of(task, olderTask) : List.of(olderTask, task));
        when(tasks.getByTaskCode("T-1")).thenReturn(task); when(tasks.getByTaskCode("T-99999")).thenThrow(new BusinessException(404, "实验任务不存在"));
        var latest = new ExperimentRunVO(); latest.setId(12L); latest.setTaskId(303L); latest.setRunCode("R-2"); latest.setStatus("COMPLETED"); latest.setCreatedAt(newest);
        var older = new ExperimentRunVO(); older.setId(999L); older.setRunCode("R-77"); older.setStatus("FAILED"); older.setCreatedAt(newest.minusDays(1)); older.setStartedAt(newest.plusDays(1));
        when(runs.getByTaskId(303L, null)).thenReturn(List.of(older, latest));
        var direct = new ExperimentRunVO(); direct.setId(101L); direct.setRunCode("R-1"); when(runs.getByRunCode("R-1")).thenReturn(direct); when(runs.getByRunCode("R-2")).thenReturn(latest);
        when(runs.getByRunCode("R-99999")).thenThrow(new BusinessException(404, "实验运行不存在"));
        var metric = new ExperimentMetricVO(); metric.setMetricName("PSNR"); metric.setMetricValue(31.25); metric.setUnit("dB");
        var ssim = new ExperimentMetricVO(); ssim.setMetricName("SSIM"); ssim.setMetricValue(0.95);
        when(metrics.getByRunId(101L, null)).thenReturn(List.of(metric)); when(metrics.getByRunId(12L, null)).thenReturn(fixture.equals("no_psnr") ? List.of(ssim) : List.of(metric));
        var log = new ExperimentLogVO(); log.setLevel("ERROR"); log.setContent("CUDA out of memory"); when(logs.getByRunId(101L, "ERROR")).thenReturn(List.of(log));
        var artifact = new ResultArtifactVO(); artifact.setArtifactName("model.bin"); artifact.setArtifactType("MODEL"); artifact.setStoragePath("/fixtures/model.bin"); when(artifacts.getByRunId(101L, "MODEL")).thenReturn(List.of(artifact));
    }

    private static final class ScriptedModel implements AutoCloseable {
        private final HttpServer server;
        private final JsonNode testCase;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private int round;
        private String selectedTask;
        private String selectedRun;

        ScriptedModel(JsonNode testCase) throws Exception {
            this.testCase = testCase;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                String response;
                try { response = respond(MAPPER.readTree(exchange.getRequestBody().readAllBytes())); }
                catch (Throwable error) { failure.set(error); response = finalText("script failed"); }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length); try (var out = exchange.getResponseBody()) { out.write(bytes); }
            }); server.start();
        }
        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        private String respond(JsonNode request) {
            int step = round++;
            List<JsonNode> script = nodes(testCase.get("script"));
            JsonNode last = null;
            if (step > 0) {
                String callId = "eval_" + (step - 1);
                JsonNode message = nodes(request.get("messages")).stream().filter(m -> m.get("role").asString().equals("tool") && m.get("tool_call_id").asString().equals(callId)).findFirst().orElseThrow();
                last = MAPPER.readTree(message.get("content").asString());
                assertNoIds(last);
                if (last.has("tasks") && last.get("success").asBoolean() && !last.get("tasks").isEmpty()) selectedTask = latestCode(last.get("tasks"), "taskCode");
                if (last.has("runs") && last.get("success").asBoolean() && !last.get("runs").isEmpty()) selectedRun = latestCode(last.get("runs"), "runCode");
            }
            if (step < script.size()) {
                JsonNode call = script.get(step);
                Map<String, Object> arguments = MAPPER.convertValue(call.get("arguments"), Map.class);
                arguments.replaceAll((key, value) -> "$latestTask".equals(value) ? Objects.requireNonNull(selectedTask) : "$latestRun".equals(value) ? Objects.requireNonNull(selectedRun) : value);
                return toolCall("eval_" + step, call.get("tool").asString(), MAPPER.writeValueAsString(arguments));
            }
            assertThat(step).isEqualTo(script.size());
            if (last == null) return finalText("PSNR 是峰值信噪比。");
            if (!last.get("success").asBoolean()) return finalText(last.get("message").asString());
            if (last.has("tasks")) return finalText(last.get("tasks").isEmpty() ? last.get("message").asString() : "P-1 的实验任务：" + join(last.get("tasks"), "taskCode"));
            if (last.has("runs")) return finalText(last.get("taskCode").asString() + " 的运行：" + join(last.get("runs"), "runCode"));
            if (last.has("logs")) return finalText(last.get("runCode").asString() + " ERROR 日志：" + join(last.get("logs"), "content"));
            if (last.has("artifacts")) return finalText(last.get("runCode").asString() + " MODEL 产物：" + join(last.get("artifacts"), "artifactName"));
            var psnr = nodes(last.get("metrics")).stream().filter(m -> m.get("metricName").asString().equals("PSNR")).findFirst();
            String prefix = (selectedTask == null && selectedRun != null ? "T-1 " : "") + last.get("runCode").asString();
            return finalText(psnr.isEmpty() ? prefix + " 当前没有 PSNR 指标。" : prefix + " PSNR=" + psnr.get().get("metricValue").asDouble() + " " + psnr.get().get("unit").asString());
        }
        private static String latestCode(JsonNode items, String field) {
            return nodes(items).stream().sorted(Comparator.comparing((JsonNode n) -> LocalDateTime.parse(n.get("createdAt").asString())).reversed()).findFirst().orElseThrow().get(field).asString();
        }
        private static String join(JsonNode items, String field) { return String.join("、", nodes(items).stream().map(n -> n.get(field).asString()).toList()); }
        private static void assertNoIds(JsonNode node) {
            if (node.isObject()) for (String field : List.of("id", "projectId", "taskId", "runId")) assertThat(node.has(field)).isFalse();
            if (node.isObject() || node.isArray()) node.forEach(ScriptedModel::assertNoIds);
        }
        private String toolCall(String id, String name, String arguments) {
            return """
                    {"id":"eval","object":"chat.completion","created":0,"model":"deepseek-flash",
                    "choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":%s,"type":"function","function":{"name":%s,"arguments":%s}}]},"finish_reason":"tool_calls"}]}
                    """.formatted(MAPPER.writeValueAsString(id), MAPPER.writeValueAsString(name), MAPPER.writeValueAsString(arguments));
        }
        private String finalText(String text) {
            return """
                    {"id":"eval","object":"chat.completion","created":0,"model":"deepseek-flash",
                    "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}]}
                    """.formatted(MAPPER.writeValueAsString(text));
        }
        @Override public void close() { server.stop(0); }
    }
}
