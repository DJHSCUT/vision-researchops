package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.*;
import com.djh.researchops.tool.*;
import com.djh.researchops.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessCodeToolTests {
    private ExperimentTaskService tasks;
    private ExperimentRunService runs;
    private ExperimentMetricService metrics;
    private ExperimentLogService logs;
    private ResultArtifactService artifacts;
    private TaskRunTools taskTools;
    private RunMetricTools metricTools;
    private RunLogTools logTools;
    private RunArtifactTools artifactTools;

    @BeforeEach
    void setUp() {
        tasks = mock(ExperimentTaskService.class);
        runs = mock(ExperimentRunService.class);
        metrics = mock(ExperimentMetricService.class);
        logs = mock(ExperimentLogService.class);
        artifacts = mock(ResultArtifactService.class);
        taskTools = new TaskRunTools(runs, tasks);
        metricTools = new RunMetricTools(metrics, runs);
        logTools = new RunLogTools(logs, runs);
        artifactTools = new RunArtifactTools(artifacts, runs);
        var task = new ExperimentTaskVO(); task.setId(505L); task.setTaskCode("T-1");
        when(tasks.getByTaskCode("T-1")).thenReturn(task);
        var run = new ExperimentRunVO(); run.setId(101L); run.setRunCode("R-1");
        when(runs.getByRunCode("R-1")).thenReturn(run);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T-1", "T1", "t1", " t-1 "})
    void taskNormalizationResolvesRealInternalId(String input) {
        var run = new ExperimentRunVO(); run.setId(808L); run.setTaskId(505L); run.setRunCode("R-9");
        when(runs.getByTaskId(505L, null)).thenReturn(List.of(run));
        var result = taskTools.queryTaskRuns(input, null);
        assertEquals("T-1", result.getTaskCode());
        assertEquals("R-9", result.getRuns().get(0).runCode());
        var order = inOrder(tasks, runs);
        order.verify(tasks).getByTaskCode("T-1");
        order.verify(runs).getByTaskId(505L, null);
        verifyNoMoreInteractions(tasks, runs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"R-1", "R1", "r1", " r-1 "})
    void allRunToolsNormalizeBeforeResolving(String input) {
        when(metrics.getByRunId(101L, null)).thenReturn(List.of());
        when(logs.getByRunId(101L, "ERROR")).thenReturn(List.of());
        when(artifacts.getByRunId(101L, "MODEL")).thenReturn(List.of());
        assertEquals("R-1", metricTools.queryRunMetrics(input).getRunCode());
        assertEquals("R-1", logTools.queryRunLogs(input, "ERROR").getRunCode());
        assertEquals("R-1", artifactTools.queryRunArtifacts(input, "MODEL").getRunCode());
        verify(runs, times(3)).getByRunCode("R-1");
        verify(metrics).getByRunId(101L, null);
        verify(logs).getByRunId(101L, "ERROR");
        verify(artifacts).getByRunId(101L, "MODEL");
        verifyNoMoreInteractions(runs, metrics, logs, artifacts);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"T-0", "T-abc", "R-1", "T--1"})
    void malformedTaskCodeStopsBeforeAllServices(String input) {
        assertFalse(taskTools.queryTaskRuns(input, null).isSuccess());
        verifyNoInteractions(tasks, runs, metrics, logs, artifacts);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"R-0", "R-test", "T-1", "R--1"})
    void malformedRunCodeStopsBeforeAllServices(String input) {
        assertFalse(metricTools.queryRunMetrics(input).isSuccess());
        assertFalse(logTools.queryRunLogs(input, "ERROR").isSuccess());
        assertFalse(artifactTools.queryRunArtifacts(input, "MODEL").isSuccess());
        verifyNoInteractions(tasks, runs, metrics, logs, artifacts);
    }

    @Test
    void missingTaskDoesNotQueryRuns() {
        when(tasks.getByTaskCode("T-99999")).thenThrow(new BusinessException(404, "实验任务不存在"));
        var result = taskTools.queryTaskRuns("T-99999", null);
        assertFalse(result.isSuccess());
        assertEquals("实验任务 T-99999 不存在", result.getMessage());
        verify(tasks).getByTaskCode("T-99999");
        verifyNoInteractions(runs, metrics, logs, artifacts);
    }

    @Test
    void missingRunDoesNotQueryAnyRunData() {
        when(runs.getByRunCode("R-99999")).thenThrow(new BusinessException(404, "实验运行不存在"));
        var metric = metricTools.queryRunMetrics("R-99999");
        var log = logTools.queryRunLogs("R-99999", "ERROR");
        var artifact = artifactTools.queryRunArtifacts("R-99999", "MODEL");
        assertFalse(metric.isSuccess()); assertFalse(log.isSuccess()); assertFalse(artifact.isSuccess());
        assertEquals("运行 R-99999 不存在", metric.getMessage());
        assertEquals(metric.getMessage(), log.getMessage()); assertEquals(metric.getMessage(), artifact.getMessage());
        verifyNoInteractions(tasks, metrics, logs, artifacts);
    }

    @Test
    void unexpectedResolverFailuresAreNotHidden() {
        var failure = new IllegalStateException("test-only resolver failure");
        when(tasks.getByTaskCode("T-1")).thenThrow(failure);
        when(runs.getByRunCode("R-1")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> taskTools.queryTaskRuns("T-1", null)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> metricTools.queryRunMetrics("R-1")));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> logTools.queryRunLogs("R-1", null)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> artifactTools.queryRunArtifacts("R-1", null)));
        verifyNoInteractions(metrics, logs, artifacts);
    }

    @Test
    void callbackResultsContainOnlyDecisionFieldsAndNoInternalIds() {
        var time = LocalDateTime.of(2026, 1, 2, 3, 4);
        var run = new ExperimentRunVO(); run.setId(808L); run.setTaskId(505L); run.setRunCode("R-9");
        run.setRunName("第二次训练"); run.setStatus("FAILED"); run.setCreatedAt(time);
        run.setStartedAt(time.plusMinutes(1)); run.setFinishedAt(time.plusMinutes(2)); run.setErrorMessage("真实失败信息");
        when(runs.getByTaskId(505L, null)).thenReturn(List.of(run));
        var metric = new ExperimentMetricVO(); metric.setId(11L); metric.setRunId(101L);
        metric.setMetricName("PSNR"); metric.setMetricValue(31.5); metric.setUnit("dB"); metric.setStep(42L); metric.setCreatedAt(time);
        when(metrics.getByRunId(101L, null)).thenReturn(List.of(metric));
        var log = new ExperimentLogVO(); log.setId(12L); log.setRunId(101L); log.setLevel("ERROR"); log.setContent("真实错误日志"); log.setCreatedAt(time);
        when(logs.getByRunId(101L, "ERROR")).thenReturn(List.of(log));
        var artifact = new ResultArtifactVO(); artifact.setId(13L); artifact.setRunId(101L);
        artifact.setArtifactName("模型"); artifact.setArtifactType("MODEL"); artifact.setStoragePath("/fixture/model.bin");
        artifact.setDescription("真实描述"); artifact.setFileSizeBytes(5_000_000_123L); artifact.setCreatedAt(time);
        when(artifacts.getByRunId(101L, "MODEL")).thenReturn(List.of(artifact));
        assertFields(taskTools, "{\"taskCode\":\"T-1\"}", "runs",
                Set.of("runCode", "runName", "status", "createdAt", "startedAt", "finishedAt", "errorMessage"));
        assertFields(metricTools, "{\"runCode\":\"R-1\"}", "metrics",
                Set.of("metricName", "metricValue", "unit", "step", "createdAt"));
        assertFields(logTools, "{\"runCode\":\"R-1\",\"level\":\"ERROR\"}", "logs",
                Set.of("level", "content", "createdAt"));
        assertFields(artifactTools, "{\"runCode\":\"R-1\",\"type\":\"MODEL\"}", "artifacts",
                Set.of("artifactName", "artifactType", "storagePath", "description", "fileSizeBytes", "createdAt"));
        assertEquals(RunToolItem.from(run), taskTools.queryTaskRuns("T-1", null).getRuns().get(0));
        assertEquals(MetricToolItem.from(metric), metricTools.queryRunMetrics("R-1").getMetrics().get(0));
        assertEquals(LogToolItem.from(log), logTools.queryRunLogs("R-1", "ERROR").getLogs().get(0));
        assertEquals(ArtifactToolItem.from(artifact), artifactTools.queryRunArtifacts("R-1", "MODEL").getArtifacts().get(0));
    }

    @Test
    void schemasRequireStringBusinessCodeAndExposeOnlyFourExistingTools() {
        var mapper = JsonMapper.builder().build();
        var callbacks = ToolCallbacks.from(taskTools, metricTools, logTools, artifactTools);
        assertEquals(4, callbacks.length);
        for (var callback : callbacks) {
            var schema = mapper.readTree(callback.getToolDefinition().inputSchema());
            String code = callback.getToolDefinition().name().equals("queryTaskRuns") ? "taskCode" : "runCode";
            assertEquals(List.of(code), mapper.convertValue(schema.get("required"), List.class));
            assertEquals("string", schema.get("properties").get(code).get("type").asString());
            assertFalse(schema.get("properties").has("runId"));
            assertFalse(schema.get("properties").has("taskId"));
        }
    }

    private void assertFields(Object tool, String arguments, String list, Set<String> fields) {
        var mapper = JsonMapper.builder().build();
        Map<?, ?> result = mapper.readValue(ToolCallbacks.from(tool)[0].call(arguments), Map.class);
        assertNoInternalIds(result);
        assertEquals(fields, ((Map<?, ?>) ((List<?>) result.get(list)).get(0)).keySet());
    }

    private void assertNoInternalIds(Object value) {
        if (value instanceof Map<?, ?> map) {
            assertFalse(map.containsKey("id")); assertFalse(map.containsKey("taskId")); assertFalse(map.containsKey("runId"));
            map.values().forEach(this::assertNoInternalIds);
        } else if (value instanceof List<?> list) list.forEach(this::assertNoInternalIds);
    }
}
