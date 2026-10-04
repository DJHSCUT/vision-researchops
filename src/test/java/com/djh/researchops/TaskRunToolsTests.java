package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.tool.TaskRunTools;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.TaskRunsToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 仅模拟 Service，不连接 MySQL 或外部模型。
class TaskRunToolsTests {

    private ExperimentRunService service;
    private TaskRunTools tools;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentRunService.class);
        tools = new TaskRunTools(service);
    }

    @Test
    void allRunsQuerySucceeds() { assertSuccessfulQuery(null, null); }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING", "COMPLETED", "FAILED"})
    void eachAllowedStatusSucceeds(String status) { assertSuccessfulQuery(status, status); }

    @Test
    void lowercaseStatusIsNormalized() { assertSuccessfulQuery("failed", "FAILED"); }

    @Test
    void mixedCaseStatusIsNormalized() { assertSuccessfulQuery("Failed", "FAILED"); }

    @Test
    void surroundingWhitespaceIsTrimmed() { assertSuccessfulQuery(" FAILED ", "FAILED"); }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "ERROR", "STOPPED", "", "   ", "失败"})
    void invalidStatusReturnsFailureWithoutServiceCall(String status) {
        TaskRunsToolResult result = tools.queryTaskRuns(1L, status);
        assertFalse(result.isSuccess());
        assertEquals("实验运行状态不合法", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void nullTaskIdIsRejected() { assertInvalidTaskId(null); }

    @Test
    void zeroTaskIdIsRejected() { assertInvalidTaskId(0L); }

    @Test
    void negativeTaskIdIsRejected() { assertInvalidTaskId(-1L); }

    @Test
    void multipleRunsPreserveServiceListAndOrder() {
        List<ExperimentRunVO> runs = List.of(run(7L, "FAILED"), run(3L, "COMPLETED"));
        when(service.getByTaskId(1L, null)).thenReturn(runs);
        assertSame(runs, tools.queryTaskRuns(1L, null).getRuns());
    }

    @Test
    void runStatusIsPreserved() {
        assertEquals("FAILED", queryFailedRun().getStatus());
    }

    @Test
    void startedAtIsPreserved() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), queryFailedRun().getStartedAt());
    }

    @Test
    void finishedAtIsPreserved() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 5, 6), queryFailedRun().getFinishedAt());
    }

    @Test
    void errorMessageIsPreserved() {
        assertEquals("测试失败信息", queryFailedRun().getErrorMessage());
    }

    @Test
    void existingTaskWithoutRunsReturnsSuccess() {
        when(service.getByTaskId(1L, null)).thenReturn(List.of());
        TaskRunsToolResult result = tools.queryTaskRuns(1L, null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验任务暂无运行记录", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void existingTaskWithoutFailedRunsReturnsSuccess() {
        when(service.getByTaskId(1L, "FAILED")).thenReturn(List.of());
        TaskRunsToolResult result = tools.queryTaskRuns(1L, "failed");
        assertTrue(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertEquals("当前实验任务暂无 FAILED 状态运行记录", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void missingTaskReturnsStructuredFailure() {
        when(service.getByTaskId(99999L, null)).thenThrow(new BusinessException(404, "实验任务不存在"));
        TaskRunsToolResult result = tools.queryTaskRuns(99999L, null);
        assertEquals(99999L, result.getTaskId());
        assertNull(result.getStatus());
        assertFalse(result.isSuccess());
        assertEquals("实验任务不存在", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByTaskId(1L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryTaskRuns(1L, null)));
    }

    @Test
    void unrelatedBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "实验运行不存在"))) {
            doThrow(failure).when(service).getByTaskId(1L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryTaskRuns(1L, null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByTaskId(1L, "FAILED")).thenReturn(List.of());
        tools.queryTaskRuns(1L, "failed");
        verify(service).getByTaskId(1L, "FAILED");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ExperimentRunService.class},
                TaskRunTools.class.getConstructors()[0].getParameterTypes());
        assertTrue(Arrays.stream(TaskRunTools.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getPackageName().contains(".mapper")));
    }

    @Test
    void springAiSchemaMakesStatusOptionalAndAcceptsOmittedStatus() {
        var callback = ToolCallbacks.from(tools)[0];
        assertEquals("queryTaskRuns", callback.getToolDefinition().name());
        var mapper = JsonMapper.builder().build();
        var schema = mapper.readTree(callback.getToolDefinition().inputSchema());
        assertEquals(List.of("taskId"), mapper.convertValue(schema.get("required"), List.class));
        when(service.getByTaskId(1L, null)).thenReturn(List.of());
        var result = mapper.readTree(callback.call("{\"taskId\":1}"));
        assertTrue(result.get("success").asBoolean());
        verify(service).getByTaskId(1L, null);
    }

    @Test
    void springAiCallbackAcceptsExplicitNullStatus() {
        when(service.getByTaskId(1L, null)).thenReturn(List.of());
        var result = JsonMapper.builder().build().readTree(
                ToolCallbacks.from(tools)[0].call("{\"taskId\":1,\"status\":null}"));
        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("status").isNull());
        verify(service).getByTaskId(1L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedStatus) {
        List<ExperimentRunVO> runs = List.of(run(7L, expectedStatus == null ? "FAILED" : expectedStatus));
        when(service.getByTaskId(1L, expectedStatus)).thenReturn(runs);
        TaskRunsToolResult result = tools.queryTaskRuns(1L, input);
        assertEquals(1L, result.getTaskId());
        assertEquals(expectedStatus, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertSame(runs, result.getRuns());
        verify(service).getByTaskId(1L, expectedStatus);
    }

    private void assertInvalidTaskId(Long taskId) {
        TaskRunsToolResult result = tools.queryTaskRuns(taskId, null);
        assertEquals(taskId, result.getTaskId());
        assertFalse(result.isSuccess());
        assertEquals("实验任务 ID 必须为正整数", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
        verifyNoInteractions(service);
    }

    private ExperimentRunVO queryFailedRun() {
        when(service.getByTaskId(1L, "FAILED")).thenReturn(List.of(run(7L, "FAILED")));
        return tools.queryTaskRuns(1L, "FAILED").getRuns().get(0);
    }

    private ExperimentRunVO run(Long id, String status) {
        ExperimentRunVO run = new ExperimentRunVO();
        run.setId(id);
        run.setTaskId(1L);
        run.setRunName("测试运行 " + id);
        run.setStatus(status);
        run.setStartedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 2, 5, 6));
        run.setErrorMessage("测试失败信息");
        return run;
    }
}
