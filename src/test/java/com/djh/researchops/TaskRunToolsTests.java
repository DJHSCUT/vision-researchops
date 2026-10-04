package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.tool.TaskRunTools;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.TaskRunsToolResult;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.vo.ExperimentTaskVO;
import com.djh.researchops.vo.RunToolItem;
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
    private ExperimentTaskService resolver;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentRunService.class);
        resolver = mock(ExperimentTaskService.class);
        ExperimentTaskVO resolved = new ExperimentTaskVO();
        resolved.setId(303L);
        resolved.setTaskCode("T-1");
        when(resolver.getByTaskCode("T-1")).thenReturn(resolved);
        tools = new TaskRunTools(service, resolver);
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
        TaskRunsToolResult result = tools.queryTaskRuns("T-1", status);
        assertFalse(result.isSuccess());
        assertEquals("实验运行状态不合法", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
        verifyNoInteractions(service, resolver);
    }

    @Test
    void nullTaskCodeIsRejected() { assertInvalidTaskCode(null); }

    @Test
    void zeroTaskCodeIsRejected() { assertInvalidTaskCode("T-0"); }

    @Test
    void negativeTaskCodeIsRejected() { assertInvalidTaskCode("T--1"); }

    @Test
    void multipleRunsPreserveFieldsAndServiceOrder() {
        List<ExperimentRunVO> runs = List.of(run(7L, "FAILED"), run(3L, "COMPLETED"));
        when(service.getByTaskId(303L, null)).thenReturn(runs);
        assertEquals(runs.stream().map(RunToolItem::from).toList(), tools.queryTaskRuns("T-1", null).getRuns());
    }

    @Test
    void runStatusIsPreserved() {
        assertEquals("FAILED", queryFailedRun().status());
    }

    @Test
    void startedAtIsPreserved() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), queryFailedRun().startedAt());
    }

    @Test
    void finishedAtIsPreserved() {
        assertEquals(LocalDateTime.of(2026, 1, 2, 5, 6), queryFailedRun().finishedAt());
    }

    @Test
    void errorMessageIsPreserved() {
        assertEquals("测试失败信息", queryFailedRun().errorMessage());
    }

    @Test
    void existingTaskWithoutRunsReturnsSuccess() {
        when(service.getByTaskId(303L, null)).thenReturn(List.of());
        TaskRunsToolResult result = tools.queryTaskRuns("T-1", null);
        assertTrue(result.isSuccess());
        assertEquals("当前实验任务暂无运行记录", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void existingTaskWithoutFailedRunsReturnsSuccess() {
        when(service.getByTaskId(303L, "FAILED")).thenReturn(List.of());
        TaskRunsToolResult result = tools.queryTaskRuns("T-1", "failed");
        assertTrue(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertEquals("当前实验任务暂无 FAILED 状态运行记录", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void missingTaskReturnsStructuredFailure() {
        when(resolver.getByTaskCode("T-99999")).thenThrow(new BusinessException(404, "实验任务不存在"));
        TaskRunsToolResult result = tools.queryTaskRuns("T-99999", null);
        assertEquals("T-99999", result.getTaskCode());
        assertNull(result.getStatus());
        assertFalse(result.isSuccess());
        assertEquals("实验任务 T-99999 不存在", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
    }

    @Test
    void unknownSystemFailureIsRethrown() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByTaskId(303L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryTaskRuns("T-1", null)));
    }

    @Test
    void unrelatedBusinessFailuresAreRethrown() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "实验运行不存在"))) {
            doThrow(failure).when(service).getByTaskId(303L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryTaskRuns("T-1", null)));
        }
    }

    @Test
    void delegatesExactlyOnceToExistingService() {
        when(service.getByTaskId(303L, "FAILED")).thenReturn(List.of());
        tools.queryTaskRuns("T-1", "failed");
        verify(service).getByTaskId(303L, "FAILED");
        verifyNoMoreInteractions(service);
    }

    @Test
    void toolDependsOnlyOnServiceWithoutMapperDependency() {
        assertArrayEquals(new Class<?>[]{ExperimentRunService.class, ExperimentTaskService.class},
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
        assertEquals(List.of("taskCode"), mapper.convertValue(schema.get("required"), List.class));
        when(service.getByTaskId(303L, null)).thenReturn(List.of());
        var result = mapper.readTree(callback.call("{\"taskCode\":\"T-1\"}"));
        assertTrue(result.get("success").asBoolean());
        verify(service).getByTaskId(303L, null);
    }

    @Test
    void springAiCallbackAcceptsExplicitNullStatus() {
        when(service.getByTaskId(303L, null)).thenReturn(List.of());
        var result = JsonMapper.builder().build().readTree(
                ToolCallbacks.from(tools)[0].call("{\"taskCode\":\"T-1\",\"status\":null}"));
        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("status").isNull());
        verify(service).getByTaskId(303L, null);
    }

    private void assertSuccessfulQuery(String input, String expectedStatus) {
        List<ExperimentRunVO> runs = List.of(run(7L, expectedStatus == null ? "FAILED" : expectedStatus));
        when(service.getByTaskId(303L, expectedStatus)).thenReturn(runs);
        TaskRunsToolResult result = tools.queryTaskRuns("T-1", input);
        assertEquals("T-1", result.getTaskCode());
        assertEquals(expectedStatus, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
        assertEquals(runs.stream().map(RunToolItem::from).toList(), result.getRuns());
        verify(service).getByTaskId(303L, expectedStatus);
    }

    private void assertInvalidTaskCode(String taskCode) {
        TaskRunsToolResult result = tools.queryTaskRuns(taskCode, null);
        assertNull(result.getTaskCode());
        assertFalse(result.isSuccess());
        assertEquals("实验任务编号不合法，请提供 T-1 格式的业务编号", result.getMessage());
        assertTrue(result.getRuns().isEmpty());
        verifyNoInteractions(service, resolver);
    }

    private RunToolItem queryFailedRun() {
        when(service.getByTaskId(303L, "FAILED")).thenReturn(List.of(run(7L, "FAILED")));
        return tools.queryTaskRuns("T-1", "FAILED").getRuns().get(0);
    }

    private ExperimentRunVO run(Long id, String status) {
        ExperimentRunVO run = new ExperimentRunVO();
        run.setId(id);
        run.setRunCode("R-" + (id + 10));
        run.setTaskId(1L);
        run.setRunName("测试运行 " + id);
        run.setStatus(status);
        run.setStartedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 2, 5, 6));
        run.setErrorMessage("测试失败信息");
        return run;
    }
}
