package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.*;
import com.djh.researchops.tool.*;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.ExperimentTaskVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// 脚本化本机模型只产生模型响应；Tool 执行、历史回传和连续调用由真实 Spring AI 完成。
// 测试中的日期选择模拟模型决策，不属于生产代码，也不证明真实 LLM 必然遵循 Prompt。
class AiToolChainingTests {

    private static final String QUESTION = "T-1 最近一次运行的 PSNR 是多少？";
    private static final LocalDateTime NEWEST = LocalDateTime.of(2026, 1, 3, 12, 0);
    private ResearchProjectService projects;
    private ExperimentRunService runs;
    private ExperimentTaskService tasks;
    private ExperimentMetricService metrics;
    private ExperimentLogService logs;
    private ResultArtifactService artifacts;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        projects = mock(ResearchProjectService.class);
        var project = new com.djh.researchops.vo.ResearchProjectVO(); project.setId(707L); project.setProjectCode("P-1");
        when(projects.getByProjectCode("P-1")).thenReturn(project);
        runs = mock(ExperimentRunService.class);
        tasks = mock(ExperimentTaskService.class);
        var task = new ExperimentTaskVO(); task.setId(3L); task.setTaskCode("T-1");
        when(tasks.getByTaskCode("T-1")).thenReturn(task);
        for (var entry : Map.of("R-2", 12L, "R-3", 20L, "R-1", 101L).entrySet()) {
            var resolved = new ExperimentRunVO(); resolved.setId(entry.getValue()); resolved.setRunCode(entry.getKey());
            when(runs.getByRunCode(entry.getKey())).thenReturn(resolved);
        }
        metrics = mock(ExperimentMetricService.class);
        logs = mock(ExperimentLogService.class);
        artifacts = mock(ResultArtifactService.class);
        runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean(ResearchProjectService.class, () -> projects)
                .withBean(ExperimentRunService.class, () -> runs)
                .withBean(ExperimentTaskService.class, () -> tasks)
                .withBean(ExperimentMetricService.class, () -> metrics)
                .withBean(ExperimentLogService.class, () -> logs)
                .withBean(ResultArtifactService.class, () -> artifacts)
                .withUserConfiguration(AiProfileTests.AutoConfigurationOnly.class, AiChatService.class,
                        ProjectTaskTools.class, TaskRunTools.class, RunMetricTools.class, RunLogTools.class, RunArtifactTools.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {31.2345, 29.87654})
    void chainsTaskThenMetricsUsingCreatedAtInsteadOfLargestIdOrStartedAt(double psnr) throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        when(metrics.getByRunId(12L, null)).thenReturn(List.of(metric(12L, "SSIM", 0.95, null),
                metric(12L, "PSNR", psnr, "dB")));
        assertThat(chat(Mode.CHAIN, QUESTION, 3)).isEqualTo("T-1 最近一次 R-2 的 PSNR 是 " + psnr + " dB。");
        var order = inOrder(tasks, runs, metrics);
        order.verify(tasks).getByTaskCode("T-1");
        order.verify(runs).getByTaskId(3L, null);
        order.verify(runs).getByRunCode("R-2");
        order.verify(metrics).getByRunId(12L, null);
        verifyNoMoreInteractions(runs, metrics);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void naturalLanguageTaskOneUsesBusinessCodeNotInternalId() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        when(metrics.getByRunId(12L, null)).thenReturn(List.of(metric(12L, "PSNR", 30.0, "dB")));
        assertThat(chat(Mode.CHAIN, "Task 1 最近一次 Run 的 PSNR？", 3)).contains("T-1", "R-2", "30.0 dB");
        verify(tasks).getByTaskCode("T-1");
        verify(runs).getByTaskId(3L, null);
        verify(runs).getByRunCode("R-2");
        verify(metrics).getByRunId(12L, null);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void equalCreatedAtUsesLargerIdAsTieBreak() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(List.of(run(99L, NEWEST.minusDays(1)),
                run(20L, NEWEST), run(12L, NEWEST)));
        when(metrics.getByRunId(20L, null)).thenReturn(List.of(metric(20L, "PSNR", 30.5, "dB")));
        assertThat(chat(Mode.CHAIN, QUESTION, 3)).contains("R-3", "30.5 dB");
        verify(runs).getByTaskId(3L, null);
        verify(runs).getByRunCode("R-3");
        verify(metrics).getByRunId(20L, null);
        verifyNoMoreInteractions(runs, metrics);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void missingTaskStopsBeforeMetricQuery() throws Exception {
        when(tasks.getByTaskCode("T-1")).thenThrow(new BusinessException(404, "实验任务不存在"));
        assertThat(chat(Mode.CHAIN, QUESTION, 2)).isEqualTo("实验任务 T-1 不存在");
        verify(tasks).getByTaskCode("T-1");
        verifyNoInteractions(runs, metrics, logs, artifacts);
    }

    @Test
    void taskWithoutRunsStopsBeforeMetricQuery() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(List.of());
        assertThat(chat(Mode.CHAIN, QUESTION, 2)).isEqualTo("T-1 当前暂无 Run。");
        verify(runs).getByTaskId(3L, null);
        verifyNoMoreInteractions(runs);
        verifyNoInteractions(metrics, logs, artifacts);
    }

    @Test
    void latestRunWithoutPsnrDoesNotUseSsimOrFallBackToOlderRun() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        when(metrics.getByRunId(12L, null)).thenReturn(List.of(metric(12L, "SSIM", 0.99, null)));
        assertThat(chat(Mode.CHAIN, QUESTION, 3)).isEqualTo("最近一次 R-2 当前没有 PSNR 指标。");
        verify(runs).getByTaskId(3L, null);
        verify(runs).getByRunCode("R-2");
        verify(metrics).getByRunId(12L, null);
        verifyNoMoreInteractions(runs, metrics);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void latestRunWithoutAnyMetricsReportsNoPsnr() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        when(metrics.getByRunId(12L, null)).thenReturn(List.of());
        assertThat(chat(Mode.CHAIN, QUESTION, 3)).isEqualTo("最近一次 R-2 当前没有 PSNR 指标。");
        verify(metrics).getByRunId(12L, null);
        verifyNoMoreInteractions(metrics);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void selectedRunDisappearingStopsWithoutFallback() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        when(metrics.getByRunId(12L, null)).thenThrow(new BusinessException(404, "实验运行不存在"));
        assertThat(chat(Mode.CHAIN, QUESTION, 3)).isEqualTo("运行 R-2 不存在");
        verify(metrics).getByRunId(12L, null);
        verifyNoMoreInteractions(metrics);
        verifyNoInteractions(logs, artifacts);
    }

    @Test
    void missingCreatedAtDoesNotGuessLatestRun() throws Exception {
        when(runs.getByTaskId(3L, null)).thenReturn(List.of(run(99L, null), run(12L, NEWEST)));
        assertThat(chat(Mode.CHAIN, QUESTION, 2)).isEqualTo("缺少 createdAt，无法确定最近一次 Run。");
        verifyNoInteractions(metrics, logs, artifacts);
    }

    @Test
    void explicitRunPsnrRemainsSingleToolCall() throws Exception {
        when(metrics.getByRunId(101L, null)).thenReturn(List.of(metric(101L, "PSNR", 28.75, "dB")));
        assertThat(chat(Mode.SINGLE, "R-1 的 PSNR 是多少？", 2)).isEqualTo("R-1 的 PSNR 是 28.75 dB。");
        verify(metrics).getByRunId(101L, null);
        verifyNoMoreInteractions(metrics);
        verify(runs).getByRunCode("R-1");
        verifyNoInteractions(tasks, logs, artifacts);
    }

    @Test
    void generalKnowledgeDoesNotCallBusinessTools() throws Exception {
        assertThat(chat(Mode.KNOWLEDGE, "PSNR 是什么？", 1)).isEqualTo("PSNR 是峰值信噪比。");
        verifyNoInteractions(projects, tasks, runs, metrics, logs, artifacts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P-1 有哪些实验任务？", "Project 1 有哪些实验任务？", "项目 1 有哪些实验任务？"})
    void projectSingleQueryUsesBusinessCode(String question) throws Exception {
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of(projectTask("T-1", NEWEST)));
        assertThat(chat(Mode.PROJECT_SINGLE, question, 2)).isEqualTo("P-1 的实验任务：T-1");
        verify(projects).getByProjectCode("P-1"); verify(tasks).getByProjectId(707L, null);
        verifyNoMoreInteractions(tasks); verifyNoInteractions(runs, metrics, logs, artifacts);
    }

    @Test
    void projectChainsToRunsUsingRealNewestTaskCode() throws Exception {
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of(projectTask("T-99", NEWEST.minusDays(1)), projectTask("T-1", NEWEST)));
        when(runs.getByTaskId(3L, null)).thenReturn(multipleRuns());
        assertThat(chat(Mode.PROJECT_CHAIN, "P-1 最近创建的实验任务有哪些运行？", 3)).isEqualTo("T-1 的运行：R-77、R-2、R-50");
        var order = inOrder(projects, tasks, runs);
        order.verify(projects).getByProjectCode("P-1"); order.verify(tasks).getByProjectId(707L, null);
        order.verify(tasks).getByTaskCode("T-1"); order.verify(runs).getByTaskId(3L, null);
        verifyNoMoreInteractions(projects, tasks, runs); verifyNoInteractions(metrics, logs, artifacts);
    }

    @Test
    void projectEqualTimesUseFirstReturnedTask() throws Exception {
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of(projectTask("T-1", NEWEST), projectTask("T-99", NEWEST)));
        when(runs.getByTaskId(3L, null)).thenReturn(List.of());
        assertThat(chat(Mode.PROJECT_CHAIN, "P-1 最近创建的实验任务有哪些运行？", 3)).isEqualTo("T-1 当前暂无 Run。");
        verify(tasks).getByTaskCode("T-1"); verifyNoInteractions(metrics, logs, artifacts);
    }

    @Test
    void missingProjectStopsChain() throws Exception {
        when(projects.getByProjectCode("P-1")).thenThrow(new BusinessException(404, "研究项目不存在"));
        assertThat(chat(Mode.PROJECT_CHAIN, "P-1 最近创建的实验任务有哪些运行？", 2)).isEqualTo("研究项目 P-1 不存在");
        verifyNoInteractions(tasks, runs, metrics, logs, artifacts);
    }

    @Test
    void emptyProjectStopsChain() throws Exception {
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of());
        assertThat(chat(Mode.PROJECT_CHAIN, "P-1 最近创建的实验任务有哪些运行？", 2)).isEqualTo("P-1 当前没有实验任务。");
        verify(tasks).getByProjectId(707L, null);
        verifyNoMoreInteractions(tasks); verifyNoInteractions(runs, metrics, logs, artifacts);
    }

    @Test
    void missingTaskCreationTimeStopsChain() throws Exception {
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of(projectTask("T-1", null)));
        assertThat(chat(Mode.PROJECT_CHAIN, "P-1 最近创建的实验任务有哪些运行？", 2)).isEqualTo("缺少 createdAt，无法确定最近创建的 Task。");
        verify(tasks).getByProjectId(707L, null);
        verifyNoMoreInteractions(tasks); verifyNoInteractions(runs, metrics, logs, artifacts);
    }

    private ExperimentTaskVO projectTask(String code, LocalDateTime createdAt) {
        var task = new ExperimentTaskVO(); task.setId(3L); task.setProjectId(707L);
        task.setTaskCode(code); task.setName("实验任务"); task.setStatus("TODO"); task.setCreatedAt(createdAt);
        return task;
    }

    private String chat(Mode mode, String question, int expectedRequests) throws Exception {
        try (ScriptedModel model = new ScriptedModel(mode)) {
            AtomicReference<String> answer = new AtomicReference<>();
            runner.withPropertyValues("spring.profiles.active=ai", "LLM_API_KEY=test-placeholder-not-a-real-key",
                            "LLM_MODEL=deepseek-flash", "LLM_BASE_URL=" + model.baseUrl())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        answer.set(context.getBean(AiChatService.class).chat(question).getContent());
                    });
            assertThat(model.failure.get()).isNull();
            assertThat(model.requests).hasSize(expectedRequests);
            // 校验实际请求里四个工具都存在，但没有多余模型轮次或其他 Tool 的执行。
            var toolNames = StreamSupport.stream(model.requests.get(0).get("tools").spliterator(), false)
                    .map(tool -> tool.get("function").get("name").asString()).toList();
            assertThat(toolNames).containsExactlyInAnyOrder("queryProjectTasks", "queryTaskRuns", "queryRunMetrics", "queryRunLogs", "queryRunArtifacts");
            return answer.get();
        }
    }

    private List<ExperimentRunVO> multipleRuns() {
        // 故意按 id DESC 返回；id 最大的 Run 创建更早，但 startedAt 更晚。
        return List.of(run(99L, NEWEST.minusDays(2)), run(12L, NEWEST), run(5L, NEWEST.minusDays(1)));
    }

    private ExperimentRunVO run(Long id, LocalDateTime createdAt) {
        ExperimentRunVO run = new ExperimentRunVO();
        run.setId(id);
        run.setRunCode(Map.of(99L, "R-77", 12L, "R-2", 5L, "R-50", 20L, "R-3").get(id));
        run.setTaskId(3L);
        run.setStatus("COMPLETED");
        run.setCreatedAt(createdAt);
        run.setStartedAt(id == 99L ? NEWEST.plusDays(1) : NEWEST.minusDays(3));
        return run;
    }

    private ExperimentMetricVO metric(Long runId, String name, double value, String unit) {
        ExperimentMetricVO metric = new ExperimentMetricVO();
        metric.setId(1L);
        metric.setRunId(runId);
        metric.setMetricName(name);
        metric.setMetricValue(value);
        metric.setUnit(unit);
        return metric;
    }

    private enum Mode { CHAIN, SINGLE, KNOWLEDGE, PROJECT_SINGLE, PROJECT_CHAIN }

    private static final class ScriptedModel implements AutoCloseable {
        private final JsonMapper mapper = JsonMapper.builder().build();
        private final HttpServer server;
        private final Mode mode;
        private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private String selectedRunCode;
        private String selectedTaskCode;

        private ScriptedModel(Mode mode) throws Exception {
            this.mode = mode;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                String response;
                try {
                    requests.add(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    response = respond(requests.get(requests.size() - 1), requests.size());
                } catch (Throwable error) {
                    failure.set(error);
                    response = finalText("脚本化测试模型失败");
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
        }

        private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        private String respond(JsonNode request, int round) {
            if (mode == Mode.KNOWLEDGE) {
                assertThat(round).isEqualTo(1);
                return finalText("PSNR 是峰值信噪比。");
            }
            if (mode == Mode.PROJECT_SINGLE || mode == Mode.PROJECT_CHAIN) {
                return respondProject(request, round);
            }
            if (round == 1) {
                String system = request.get("messages").get(0).get("content").asString();
                assertThat(system).contains("Task 1", "T-1", "Run 2", "R-2", "不要求用户提供数据库 ID");
                for (JsonNode tool : request.get("tools")) {
                    var properties = tool.get("function").get("parameters").get("properties");
                    assertThat(properties.has("runId")).isFalse();
                    assertThat(properties.has("taskId")).isFalse();
                }
                if (mode == Mode.SINGLE) {
                    selectedRunCode = "R-1";
                    return toolCall("call_metric", "queryRunMetrics", "{\"runCode\":\"R-1\"}");
                }
                return toolCall("call_task", "queryTaskRuns", "{\"taskCode\":\"T-1\",\"status\":null}");
            }
            if (mode == Mode.CHAIN && round == 2) {
                JsonNode result = toolResult(request, "call_task");
                assertThat(result.get("taskCode").asString()).isEqualTo("T-1");
                assertNoInternalIds(result);
                assertThat(result.get("status").isNull()).isTrue();
                if (!result.get("success").asBoolean()) return finalText(result.get("message").asString());
                List<JsonNode> runs = StreamSupport.stream(result.get("runs").spliterator(), false).toList();
                if (runs.isEmpty()) return finalText("T-1 当前暂无 Run。");
                if (runs.stream().anyMatch(run -> run.get("createdAt") == null || run.get("createdAt").isNull())) {
                    return finalText("缺少 createdAt，无法确定最近一次 Run。");
                }
                // 仅脚本化 Mock Model 从收到的 Tool Result 中动态决定下一次模型 tool_call 参数。
                selectedRunCode = runs.stream().sorted(Comparator
                        .comparing((JsonNode run) -> LocalDateTime.parse(run.get("createdAt").asString())).reversed())
                        .findFirst().orElseThrow().get("runCode").asString();
                return toolCall("call_metric", "queryRunMetrics", mapper.writeValueAsString(Map.of("runCode", selectedRunCode)));
            }
            assertThat(round).isEqualTo(mode == Mode.CHAIN ? 3 : 2);
            JsonNode result = toolResult(request, "call_metric");
            assertThat(result.get("runCode").asString()).isEqualTo(selectedRunCode);
            assertNoInternalIds(result);
            if (!result.get("success").asBoolean()) return finalText(result.get("message").asString());
            var psnr = StreamSupport.stream(result.get("metrics").spliterator(), false)
                    .filter(metric -> "PSNR".equals(metric.get("metricName").asString())).findFirst();
            if (psnr.isEmpty()) return finalText("最近一次 " + selectedRunCode + " 当前没有 PSNR 指标。");
            JsonNode metric = psnr.orElseThrow();
            String unit = metric.get("unit").isNull() ? "" : " " + metric.get("unit").asString();
            return finalText((mode == Mode.CHAIN ? "T-1 最近一次 " : "") + selectedRunCode
                    + " 的 PSNR 是 " + metric.get("metricValue").asDouble() + unit + "。");
        }

        private String respondProject(JsonNode request, int round) {
            if (round == 1) {
                String system = request.get("messages").get(0).get("content").asString();
                assertThat(system).contains("Project 1", "项目 1", "P-1", "queryProjectTasks", "createdAt 最大", "最先出现", "projectId");
                assertThat(system).doesNotContain("当前没有提供项目（Project）查询");
                return toolCall("call_project", "queryProjectTasks", "{\"projectCode\":\"P-1\",\"status\":null}");
            }
            JsonNode project = toolResult(request, "call_project");
            assertNoInternalIds(project);
            if (round == 2) {
                assertThat(project.get("projectCode").asString()).isEqualTo("P-1");
                if (!project.get("success").asBoolean()) return finalText(project.get("message").asString());
                List<JsonNode> tasks = StreamSupport.stream(project.get("tasks").spliterator(), false).toList();
                if (tasks.isEmpty()) return finalText("P-1 当前没有实验任务。");
                if (mode == Mode.PROJECT_SINGLE) return finalText("P-1 的实验任务：" + tasks.get(0).get("taskCode").asString());
                if (tasks.stream().anyMatch(task -> task.get("createdAt").isNull())) return finalText("缺少 createdAt，无法确定最近创建的 Task。");
                selectedTaskCode = tasks.stream().sorted(Comparator.comparing((JsonNode task) -> LocalDateTime.parse(task.get("createdAt").asString())).reversed())
                        .findFirst().orElseThrow().get("taskCode").asString();
                return toolCall("call_runs", "queryTaskRuns", mapper.writeValueAsString(Map.of("taskCode", selectedTaskCode)));
            }
            assertThat(round).isEqualTo(3);
            JsonNode result = toolResult(request, "call_runs"); assertNoInternalIds(result);
            assertThat(result.get("taskCode").asString()).isEqualTo(selectedTaskCode);
            if (!result.get("success").asBoolean()) return finalText(result.get("message").asString());
            List<String> codes = StreamSupport.stream(result.get("runs").spliterator(), false).map(run -> run.get("runCode").asString()).toList();
            return finalText(codes.isEmpty() ? selectedTaskCode + " 当前暂无 Run。" : selectedTaskCode + " 的运行：" + String.join("、", codes));
        }

        private void assertNoInternalIds(JsonNode node) {
            if (node.isObject()) {
                assertThat(node.has("id")).isFalse();
                assertThat(node.has("projectId")).isFalse();
                assertThat(node.has("taskId")).isFalse();
                assertThat(node.has("runId")).isFalse();
            }
            if (node.isObject() || node.isArray()) node.forEach(this::assertNoInternalIds);
        }

        private JsonNode toolResult(JsonNode request, String callId) {
            JsonNode message = StreamSupport.stream(request.get("messages").spliterator(), false)
                    .filter(item -> "tool".equals(item.get("role").asString())
                            && callId.equals(item.get("tool_call_id").asString())).findFirst().orElseThrow();
            return mapper.readTree(message.get("content").asString());
        }

        private String toolCall(String callId, String name, String arguments) {
            return """
                    {"id":"test-chain","object":"chat.completion","created":0,"model":"deepseek-flash",
                    "choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{
                    "id":%s,"type":"function","function":{"name":%s,"arguments":%s}}]},"finish_reason":"tool_calls"}]}
                    """.formatted(mapper.writeValueAsString(callId), mapper.writeValueAsString(name), mapper.writeValueAsString(arguments));
        }

        private String finalText(String content) {
            return """
                    {"id":"test-answer","object":"chat.completion","created":0,"model":"deepseek-flash",
                    "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}]}
                    """.formatted(mapper.writeValueAsString(content));
        }

        @Override
        public void close() { server.stop(0); }
    }
}
