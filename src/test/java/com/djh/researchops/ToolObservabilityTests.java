package com.djh.researchops;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.*;
import com.djh.researchops.tool.*;
import com.djh.researchops.util.*;
import com.djh.researchops.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolObservabilityTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ToolInvocationLog.class);
    private ListAppender<ILoggingEvent> appender;
    private final ResearchProjectService projects = mock(ResearchProjectService.class);
    private final ExperimentTaskService tasks = mock(ExperimentTaskService.class);
    private final ExperimentRunService runs = mock(ExperimentRunService.class);
    private final ExperimentMetricService metrics = mock(ExperimentMetricService.class);
    private final ExperimentLogService logs = mock(ExperimentLogService.class);
    private final ResultArtifactService artifacts = mock(ResultArtifactService.class);

    @BeforeEach void capture() {
        appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        var project = new ResearchProjectVO(); project.setId(707L); when(projects.getByProjectCode("P-1")).thenReturn(project);
        var task = new ExperimentTaskVO(); task.setId(303L); when(tasks.getByTaskCode("T-1")).thenReturn(task);
        var run = new ExperimentRunVO(); run.setId(101L); when(runs.getByRunCode("R-1")).thenReturn(run);
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of()); when(runs.getByTaskId(303L, null)).thenReturn(List.of());
        when(metrics.getByRunId(101L, null)).thenReturn(List.of()); when(logs.getByRunId(101L, null)).thenReturn(List.of()); when(artifacts.getByRunId(101L, null)).thenReturn(List.of());
    }
    @AfterEach void cleanup() { logger.detachAppender(appender); appender.stop(); MDC.remove(AiRequestLogContext.REQUEST_ID); }

    static Stream<String> tools() { return Stream.of("queryProjectTasks", "queryTaskRuns", "queryRunMetrics", "queryRunLogs", "queryRunArtifacts"); }

    @ParameterizedTest @MethodSource("tools")
    void successRecordsOneTimedResultWithCount(String tool) {
        try (var request = AiRequestLogContext.open()) { invoke(tool, validCode(tool), null); }
        String message = onlyLog(tool, "SUCCESS");
        assertThat(message).contains("count=0", "errorType=null");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        UUID.fromString(appender.list.get(0).getMDCPropertyMap().get(AiRequestLogContext.REQUEST_ID));
    }

    @ParameterizedTest @MethodSource("tools")
    void invalidCodesNeverLogRawSensitiveInputOrCallServices(String tool) {
        invoke(tool, "secret-key\r\nforged-log", null);
        assertThat(onlyLog(tool, "INVALID_ARGUMENT")).contains("code=null", "count=0").doesNotContain("secret-key", "forged-log");
        verifyNoInteractions(projects, tasks, runs, metrics, logs, artifacts);
    }

    @ParameterizedTest @MethodSource("tools")
    void missingRecordsAreNotSuccess(String tool) {
        String code = validCode(tool);
        if (tool.equals("queryProjectTasks")) when(projects.getByProjectCode(code)).thenThrow(new BusinessException(404, "研究项目不存在"));
        else if (tool.equals("queryTaskRuns")) when(tasks.getByTaskCode(code)).thenThrow(new BusinessException(404, "实验任务不存在"));
        else when(runs.getByRunCode(code)).thenThrow(new BusinessException(404, "实验运行不存在"));
        invoke(tool, code, null);
        assertThat(onlyLog(tool, "NOT_FOUND")).contains("count=0", "errorType=NOT_FOUND");
    }

    @ParameterizedTest @MethodSource("tools")
    void exceptionsKeepOriginalFailureAndLogOnlyType(String tool) {
        String code = validCode(tool);
        var failure = new IllegalStateException("password=secret API-Key=secret");
        if (tool.equals("queryProjectTasks")) when(projects.getByProjectCode(code)).thenThrow(failure);
        else if (tool.equals("queryTaskRuns")) when(tasks.getByTaskCode(code)).thenThrow(failure);
        else when(runs.getByRunCode(code)).thenThrow(failure);
        assertThatThrownBy(() -> invoke(tool, code, null)).isSameAs(failure);
        assertThat(onlyLog(tool, "ERROR")).contains("errorType=IllegalStateException", "count=null").doesNotContain("password", "secret", "API-Key");
    }

    @ParameterizedTest @MethodSource("toolsWithFilters")
    void invalidFiltersAreRedacted(String tool) {
        invoke(tool, validCode(tool), "password=secret\nforged-log");
        assertThat(onlyLog(tool, "INVALID_ARGUMENT")).doesNotContain("password", "secret", "forged-log");
        verifyNoInteractions(projects, tasks, runs, metrics, logs, artifacts);
    }
    static Stream<String> toolsWithFilters() { return tools().filter(name -> !name.equals("queryRunMetrics")); }

    @ParameterizedTest @MethodSource("toolsWithFilters")
    void validFiltersAreLoggedAfterNormalization(String tool) {
        String filter = tool.equals("queryRunArtifacts") ? "MODEL" : tool.equals("queryRunLogs") ? "ERROR" : "FAILED";
        String key = tool.equals("queryRunArtifacts") ? "type" : tool.equals("queryRunLogs") ? "level" : "status";
        when(tasks.getByProjectId(707L, "FAILED")).thenReturn(List.of());
        when(runs.getByTaskId(303L, "FAILED")).thenReturn(List.of());
        when(logs.getByRunId(101L, "ERROR")).thenReturn(List.of());
        when(artifacts.getByRunId(101L, "MODEL")).thenReturn(List.of());
        invoke(tool, validCode(tool), " " + filter.toLowerCase(java.util.Locale.ROOT) + " ");
        assertThat(onlyLog(tool, "SUCCESS")).contains(key + "=" + filter);
    }

    @Test void requestScopeRestoresMdcOnExceptionAndSeparatesRequests() {
        MDC.put(AiRequestLogContext.REQUEST_ID, "outer-context");
        String first;
        try (var request = AiRequestLogContext.open()) {
            first = MDC.get(AiRequestLogContext.REQUEST_ID);
            assertThat(first).isNotEqualTo("outer-context");
        }
        assertThat(MDC.get(AiRequestLogContext.REQUEST_ID)).isEqualTo("outer-context");
        assertThatThrownBy(() -> { try (var request = AiRequestLogContext.open()) {
            assertThat(MDC.get(AiRequestLogContext.REQUEST_ID)).isNotEqualTo(first); throw new IllegalStateException();
        } }).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(AiRequestLogContext.REQUEST_ID)).isEqualTo("outer-context");
    }

    @Test void taskDataDoesNotEnterLogsAndCountIsReal() {
        var task = new ExperimentTaskVO(); task.setTaskCode("T-47"); task.setName("sensitive-task-name");
        when(tasks.getByProjectId(707L, null)).thenReturn(List.of(task));
        invoke("queryProjectTasks", "p1", null);
        assertThat(onlyLog("queryProjectTasks", "SUCCESS")).contains("code=P-1", "count=1").doesNotContain("T-47", "sensitive-task-name", "707");
    }

    private String onlyLog(String tool, String result) {
        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("tool=" + tool, "result=" + result).matches(".*durationMs=[0-9]+.*");
        return message;
    }
    private static String validCode(String tool) { return tool.equals("queryProjectTasks") ? "P-1" : tool.equals("queryTaskRuns") ? "T-1" : "R-1"; }
    private Object invoke(String tool, String code, String filter) {
        return switch (tool) {
            case "queryProjectTasks" -> new ProjectTaskTools(projects, tasks).queryProjectTasks(code, filter);
            case "queryTaskRuns" -> new TaskRunTools(runs, tasks).queryTaskRuns(code, filter);
            case "queryRunMetrics" -> new RunMetricTools(metrics, runs).queryRunMetrics(code);
            case "queryRunLogs" -> new RunLogTools(logs, runs).queryRunLogs(code, filter);
            case "queryRunArtifacts" -> new RunArtifactTools(artifacts, runs).queryRunArtifacts(code, filter);
            default -> throw new IllegalArgumentException(tool);
        };
    }
}
