package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.tool.RunLogTools;
import com.djh.researchops.vo.ExperimentLogVO;
import com.djh.researchops.vo.RunLogsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.LogToolItem;
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
    private ExperimentRunService resolver;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentLogService.class);
        resolver = mock(ExperimentRunService.class);
        ExperimentRunVO resolved = new ExperimentRunVO();
        resolved.setId(101L);
        resolved.setRunCode("R-1");
        when(resolver.getByRunCode("R-1")).thenReturn(resolved);
        tools = new RunLogTools(service, resolver);
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
    void nullRunCodeIsRejectedBeforeService() { assertInvalidRunCode(null); }

    @Test
    void zeroRunCodeIsRejectedBeforeService() { assertInvalidRunCode("R-0"); }

    @Test
    void negativeRunCodeIsRejectedBeforeService() { assertInvalidRunCode("R--1"); }

    @Test
    void multipleLogsPreserveServiceListOrderAndContents() {
        List<ExperimentLogVO> logs = List.of(log(4L, "ERROR", "CUDA out of memory"),
                log(2L, "WARN", "Low memory"), log(1L, "INFO", "Training started"));
        when(service.getByRunId(101L, null)).thenReturn(logs);
        assertEquals(logs.stream().map(LogToolItem::from).toList(), tools.queryRunLogs("R-1", null).getLogs());
    }

    @Test
    void existingRunWithoutLogsReturnsSuccess() {
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        RunLogsToolResult result = tools.queryRunLogs("R-1", null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无日志", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void existingRunWithoutErrorLogsReturnsSuccess() {
        when(service.getByRunId(101L, "ERROR")).thenReturn(List.of());
        RunLogsToolResult result = tools.queryRunLogs("R-1", "ERROR");
        assertTrue(result.isSuccess());
        assertEquals("ERROR", result.getLevel());
        assertEquals("当前实验运行暂无 ERROR 日志", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void missingRunReturnsStructuredFailure() {
        when(resolver.getByRunCode("R-99999")).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunLogsToolResult result = tools.queryRunLogs("R-99999", null);
        assertEquals("R-99999", result.getRunCode());
        assertNull(result.getLevel());
        assertFalse(result.isSuccess());
        assertEquals("运行 R-99999 不存在", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(101L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunLogs("R-1", null)));
    }

    @Test
    void otherBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "其他资源不存在"))) {
            doThrow(failure).when(service).getByRunId(101L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunLogs("R-1", null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByRunId(101L, "WARN")).thenReturn(List.of());
        tools.queryRunLogs("R-1", "warn");
        verify(service).getByRunId(101L, "WARN");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnLogServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ExperimentLogService.class, ExperimentRunService.class},
                RunLogTools.class.getConstructors()[0].getParameterTypes());
        assertTrue(Arrays.stream(RunLogTools.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getPackageName().contains(".mapper")));
    }

    @Test
    void springAiSchemaMakesLevelOptionalAndAcceptsOmittedLevel() {
        var callback = ToolCallbacks.from(tools)[0];
        assertEquals("queryRunLogs", callback.getToolDefinition().name());
        JsonNode schema = JsonMapper.builder().build().readTree(callback.getToolDefinition().inputSchema());
        assertEquals(List.of("runCode"), JsonMapper.builder().build()
                .convertValue(schema.get("required"), List.class));
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        String result = callback.call("{\"runCode\":\"R-1\"}");
        assertTrue(result.contains("当前实验运行暂无日志"));
        verify(service).getByRunId(101L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedLevel) {
        List<ExperimentLogVO> logs = List.of(log(4L, expectedLevel == null ? "INFO" : expectedLevel, "真实测试日志"));
        when(service.getByRunId(101L, expectedLevel)).thenReturn(logs);
        RunLogsToolResult result = tools.queryRunLogs("R-1", input);
        assertEquals("R-1", result.getRunCode());
        assertEquals(expectedLevel, result.getLevel());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertEquals(logs.stream().map(LogToolItem::from).toList(), result.getLogs());
        verify(service).getByRunId(101L, expectedLevel);
    }

    private void assertInvalidLevel(String level) {
        RunLogsToolResult result = tools.queryRunLogs("R-1", level);
        assertFalse(result.isSuccess());
        assertEquals("日志级别不合法", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
        verifyNoInteractions(service, resolver);
    }

    private void assertInvalidRunCode(String runCode) {
        RunLogsToolResult result = tools.queryRunLogs(runCode, null);
        assertNull(result.getRunCode());
        assertFalse(result.isSuccess());
        assertEquals("实验运行编号不合法，请提供 R-1 格式的业务编号", result.getMessage());
        assertTrue(result.getLogs().isEmpty());
        verifyNoInteractions(service, resolver);
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
