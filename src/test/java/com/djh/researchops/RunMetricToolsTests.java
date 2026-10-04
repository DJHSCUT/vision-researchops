package com.djh.researchops;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.tool.RunMetricTools;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.RunMetricsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.MetricToolItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 只模拟业务 Service，不连接 MySQL 或 LLM。
class RunMetricToolsTests {

    private ExperimentMetricService service;
    private RunMetricTools tools;
    private ExperimentRunService resolver;

    @BeforeEach
    void setUp() {
        service = mock(ExperimentMetricService.class);
        resolver = mock(ExperimentRunService.class);
        ExperimentRunVO resolved = new ExperimentRunVO();
        resolved.setId(101L);
        resolved.setRunCode("R-1");
        when(resolver.getByRunCode("R-1")).thenReturn(resolved);
        tools = new RunMetricTools(service, resolver);
    }

    @Test
    void runOneQuerySucceeds() {
        when(service.getByRunId(101L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        RunMetricsToolResult result = tools.queryRunMetrics("R-1");
        assertEquals("R-1", result.getRunCode());
        assertTrue(result.isSuccess());
        assertEquals("查询成功", result.getMessage());
    }

    @Test
    void delegatesToServiceWithoutNameFilter() {
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        tools.queryRunMetrics("R-1");
        verify(service).getByRunId(101L, null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsServiceMetricsPreservingDecisionFields() {
        List<ExperimentMetricVO> metrics = List.of(metric("SSIM", 0.912, null, null));
        when(service.getByRunId(101L, null)).thenReturn(metrics);
        assertEquals(metrics.stream().map(MetricToolItem::from).toList(), tools.queryRunMetrics("R-1").getMetrics());
    }

    @Test
    void psnrValueRemainsUnchanged() {
        when(service.getByRunId(101L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertEquals(28.9, tools.queryRunMetrics("R-1").getMetrics().get(0).metricValue());
    }

    @Test
    void unitRemainsUnchanged() {
        when(service.getByRunId(101L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertEquals("dB", tools.queryRunMetrics("R-1").getMetrics().get(0).unit());
    }

    @Test
    void nullStepIsPreserved() {
        when(service.getByRunId(101L, null)).thenReturn(List.of(metric("PSNR", 28.9, "dB", null)));
        assertNull(tools.queryRunMetrics("R-1").getMetrics().get(0).step());
    }

    @Test
    void allTrainingStepsAndPreciseValuesArePreserved() {
        List<ExperimentMetricVO> metrics = List.of(
                metric("train_loss", 0.0037, null, 30000L),
                metric("train_loss", 0.007, null, 10000L),
                metric("train_loss", 0.012, null, 5000L),
                metric("train_loss", 0.021, null, 1000L));
        when(service.getByRunId(101L, null)).thenReturn(metrics);
        assertEquals(metrics.stream().map(MetricToolItem::from).toList(), tools.queryRunMetrics("R-1").getMetrics());
        assertEquals(0.0037, tools.queryRunMetrics("R-1").getMetrics().get(0).metricValue());
    }

    @Test
    void existingRunWithoutMetricsReturnsSuccessfulEmptyResult() {
        when(service.getByRunId(101L, null)).thenReturn(List.of());
        RunMetricsToolResult result = tools.queryRunMetrics("R-1");
        assertTrue(result.isSuccess());
        assertEquals("当前实验运行暂无指标数据", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
    }

    @Test
    void missingRunReturnsFailureInsteadOfThrowing() {
        when(resolver.getByRunCode("R-99999")).thenThrow(new BusinessException(404, "实验运行不存在"));
        RunMetricsToolResult result = tools.queryRunMetrics("R-99999");
        assertEquals("R-99999", result.getRunCode());
        assertFalse(result.isSuccess());
        assertEquals("运行 R-99999 不存在", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
    }

    @Test
    void unknownSystemFailureIsNotSwallowed() {
        RuntimeException failure = new IllegalStateException("test database failure");
        when(service.getByRunId(101L, null)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> tools.queryRunMetrics("R-1")));
    }

    @Test
    void otherBusinessFailuresAreNotConvertedToMissingRun() {
        for (BusinessException failure : List.of(new BusinessException(500, "内部错误"),
                new BusinessException(404, "其他资源不存在"))) {
            doThrow(failure).when(service).getByRunId(101L, null);
            assertSame(failure, assertThrows(BusinessException.class, () -> tools.queryRunMetrics("R-1")));
        }
    }

    @Test
    void nullRunCodeIsRejectedBeforeCallingService() {
        assertInvalidRunCode(null);
    }

    @Test
    void zeroRunCodeIsRejectedBeforeCallingService() {
        assertInvalidRunCode("R-0");
    }

    @Test
    void negativeRunCodeIsRejectedBeforeCallingService() {
        assertInvalidRunCode("R--1");
    }

    private void assertInvalidRunCode(String runCode) {
        RunMetricsToolResult result = tools.queryRunMetrics(runCode);
        assertFalse(result.isSuccess());
        assertEquals("实验运行编号不合法，请提供 R-1 格式的业务编号", result.getMessage());
        assertTrue(result.getMetrics().isEmpty());
        verifyNoInteractions(service, resolver);
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
