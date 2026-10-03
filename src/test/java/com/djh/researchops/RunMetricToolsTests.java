package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.tool.RunMetricTools;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.RunMetricsToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 只模拟业务 Service，不连接 MySQL 或 LLM。
class RunMetricToolsTests {

    private ExperimentMetricService service;
    private RunMetricTools tools;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentMetricService.class);
        tools = new RunMetricTools(service);
    }

    @Test
    void runOneQuerySucceeds() {
        when(service.getByRunId(1L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        RunMetricsToolResult result = tools.queryRunMetrics(1L);
        assertEquals(1L, result.getRunId());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
    }

    @Test
    void delegatesToServiceWithoutNameFilter() {
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        tools.queryRunMetrics(1L);
        verify(service).getByRunId(1L, null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsServiceMetricsWithoutReplacingRecords() {
        List<ExperimentMetricVO> metrics = List.of(metric("SSIM", 0.912, null, null));
        when(service.getByRunId(1L, null)).thenReturn(metrics);
        assertSame(metrics, tools.queryRunMetrics(1L).getMetrics());
    }

    @Test
    void psnrValueRemainsUnchanged() {
        when(service.getByRunId(1L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertEquals(28.9, tools.queryRunMetrics(1L).getMetrics().get(0).getMetricValue());
    }

    @Test
    void unitRemainsUnchanged() {
        when(service.getByRunId(1L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertEquals("dB", tools.queryRunMetrics(1L).getMetrics().get(0).getUnit());
    }

    @Test
    void nullStepIsPreserved() {
        when(service.getByRunId(1L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertNull(tools.queryRunMetrics(1L).getMetrics().get(0).getStep());
    }

    @Test
    void allTrainingStepsAndPreciseValuesArePreserved() {
        List<ExperimentMetricVO> metrics = List.of(
                metric("train_loss", 0.0037, null, 30000L),
                metric("train_loss", 0.007, null, 10000L),
                metric("train_loss", 0.012, null, 5000L),
                metric("train_loss", 0.021, null, 1000L));
        when(service.getByRunId(1L, null)).thenReturn(metrics);
        assertEquals(metrics, tools.queryRunMetrics(1L).getMetrics());
        assertEquals(0.0037, tools.queryRunMetrics(1L).getMetrics().get(0).getMetricValue());
    }

    @Test
    void existingRunWithoutMetricsReturnsSuccessfulEmptyResult() {
        when(service.getByRunId(1L, null)).thenReturn(List.of());
        RunMetricsToolResult result = tools.queryRunMetrics(1L);
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无指标数据", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
    }

    @Test
    void missingRunReturnsFailureInsteadOfThrowing() {
        when(service.getByRunId(99999L, null)).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunMetricsToolResult result = tools.queryRunMetrics(99999L);
        assertEquals(99999L, result.getRunId());
        assertFalse(result.isSuccess());
        assertEquals("实验运行不存在", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
    }

    @Test
    void unknownSystemFailureIsNotSwallowed() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(1L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunMetrics(1L)));
    }

    @Test
    void otherBusinessFailuresAreNotConvertedToMissingRun() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "其他资源不存在"))) {
            doThrow(failure).when(service).getByRunId(1L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunMetrics(1L)));
        }
    }

    @Test
    void nullRunIdIsRejectedBeforeCallingService() {
        assertInvalidRunId(null);
    }

    @Test
    void zeroRunIdIsRejectedBeforeCallingService() {
        assertInvalidRunId(0L);
    }

    @Test
    void negativeRunIdIsRejectedBeforeCallingService() {
        assertInvalidRunId(-1L);
    }

    private void assertInvalidRunId(Long runId) {
        RunMetricsToolResult result = tools.queryRunMetrics(runId);
        assertFalse(result.isSuccess());
        assertEquals("实验运行 ID 必须为正整数", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
        verifyNoInteractions(service);
    }

    private ExperimentMetricVO metric(String name, double value, String unit, Long step) {
        ExperimentMetricVO metric = new ExperimentMetricVO();
        metric.setId(1L);
        metric.setRunId(1L);
        metric.setMetricName(name);
        metric.setMetricValue(value);
        metric.setUnit(unit);
        metric.setStep(step);
        return metric;
    }
}
