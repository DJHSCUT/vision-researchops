package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.tool.RunLogTools;
import com.djh.researchops.vo.ExperimentLogVO;
import com.djh.researchops.vo.RunLogsToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 只模拟业务 Service，不连接数据库或外部模型。
class RunLogToolsTests {

    private ExperimentLogService service;
    private RunLogTools tools;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentLogService.class);
        tools = new RunLogTools(service);
    }

    @Test
    void allLogsQuerySucceeds() { assertSuccessfulQuery(null, null); }

    @Test
    void errorQuerySucceeds() { assertSuccessfulQuery("ERROR", "ERROR"); }

    @Test
    void warnQuerySucceeds() { assertSuccessfulQuery("WARN", "WARN"); }

    @Test
    void infoQuerySucceeds() { assertSuccessfulQuery("INFO", "INFO"); }

    @Test
    void lowercaseLevelIsNormalized() { assertSuccessfulQuery("error", "ERROR"); }

    @Test
    void mixedCaseLevelIsNormalized() { assertSuccessfulQuery("Error", "ERROR"); }

    @Test
    void surroundingWhitespaceIsTrimmed() { assertSuccessfulQuery(" ERROR ", "ERROR"); }

    @Test
    void invalidDebugIsRejectedBeforeService() { assertInvalidLevel("DEBUG"); }

    @Test
    void emptyLevelIsRejectedBeforeService() { assertInvalidLevel(""); }

    @Test
    void whitespaceLevelIsRejectedBeforeService() { assertInvalidLevel("   "); }

    @Test
    void nullRunIdIsRejectedBeforeService() { assertInvalidRunId(null); }

    @Test
    void zeroRunIdIsRejectedBeforeService() { assertInvalidRunId(0L); }

    @Test
    void negativeRunIdIsRejectedBeforeService() { assertInvalidRunId(-1L); }

    @Test
    void multipleLogsPreserveServiceListOrderAndContents() {
        List<ExperimentLogVO> logs = List.of(log(4L, "ERROR", "CUDA out of memory"),
                log(2L, "WARN", "Low memory"), log(1L, "INFO", "Training started"));
        when(service.getByRunId(1L, null)).thenReturn(logs);
        assertSame(logs, tools.queryRunLogs(1L, null).getLogs());
    }

    @Test
    void existingRunWithoutLogsReturnsSuccess() {
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        RunLogsToolResult result = tools.queryRunLogs(1L, null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无日志", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void existingRunWithoutErrorLogsReturnsSuccess() {
        when(service.getByRunId(1L, "ERROR")).thenReturn(List.of());
        RunLogsToolResult result = tools.queryRunLogs(1L, "ERROR");
        assertTrue(result.isSuccess());
        assertEquals("ERROR", result.getLevel());
        assertEquals("当前实验运行暂无 ERROR 日志", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void missingRunReturnsStructuredFailure() {
        when(service.getByRunId(99999L, null)).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunLogsToolResult result = tools.queryRunLogs(99999L, null);
        assertEquals(99999L, result.getRunId());
        assertNull(result.getLevel());
        assertFalse(result.isSuccess());
        assertEquals("实验运行不存在", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(1L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunLogs(1L, null)));
    }

    @Test
    void otherBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "其他资源不存在"))) {
            doThrow(failure).when(service).getByRunId(1L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunLogs(1L, null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByRunId(1L, "WARN")).thenReturn(List.of());
        tools.queryRunLogs(1L, "warn");
        verify(service).getByRunId(1L, "WARN");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnLogServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ExperimentLogService.class},
                RunLogTools.class.getConstructors()[0].getParameterTypes());
        assertTrue(Arrays.stream(RunLogTools.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getPackageName().contains(".mapper")));
    }

    @Test
    void springAiSchemaMakesLevelOptionalAndAcceptsOmittedLevel() {
        var callback = ToolCallbacks.from(tools)[0];
        assertEquals("queryRunLogs", callback.getToolDefinition().name());
        JsonNode schema = JsonMapper.builder().build().readTree(callback.getToolDefinition().inputSchema());
        assertEquals(List.of("runId"), JsonMapper.builder().build()
                .convertValue(schema.get("required"), List.class));
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        String result = callback.call("{\"runId\":1}");
        assertTrue(result.contains("当前实验运行暂无日志"));
        verify(service).getByRunId(1L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedLevel) {
        List<ExperimentLogVO> logs = List.of(log(4L, expectedLevel == null ? "INFO" : expectedLevel, "真实测试日志"));
        when(service.getByRunId(1L, expectedLevel)).thenReturn(logs);
        RunLogsToolResult result = tools.queryRunLogs(1L, input);
        assertEquals(1L, result.getRunId());
        assertEquals(expectedLevel, result.getLevel());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertSame(logs, result.getLogs());
        verify(service).getByRunId(1L, expectedLevel);
    }

    private void assertInvalidLevel(String level) {
        RunLogsToolResult result = tools.queryRunLogs(1L, level);
        assertFalse(result.isSuccess());
        assertEquals("日志级别不合法", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
        verifyNoInteractions(service);
    }

    private void assertInvalidRunId(Long runId) {
        RunLogsToolResult result = tools.queryRunLogs(runId, null);
        assertEquals(runId, result.getRunId());
        assertFalse(result.isSuccess());
        assertEquals("实验运行 ID 必须为正整数", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
        verifyNoInteractions(service);
    }

    private ExperimentLogVO log(Long id, String level, String content) {
        ExperimentLogVO log = new ExperimentLogVO();
        log.setId(id);
        log.setRunId(1L);
        log.setLevel(level);
        log.setContent(content);
        log.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        return log;
    }
}
