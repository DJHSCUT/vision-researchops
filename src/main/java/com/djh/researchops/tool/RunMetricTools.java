package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.RunMetricsToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("ai")
@RequiredArgsConstructor
public class RunMetricTools {

    private final ExperimentMetricService experimentMetricService;

    @Tool(name = "queryRunMetrics", description = "查询指定实验运行 Run 的真实实验指标。当用户询问某个 Run 的 PSNR、SSIM、loss、训练指标、数值结果或指标列表时使用。数据来自 Vision ResearchOps 数据库，不要用于查询日志或实验产物。")
    public RunMetricsToolResult queryRunMetrics(
            @ToolParam(description = "实验运行 Run 的数据库 ID，必须为正整数，例如用户说 Run 1 时传入 1") Long runId) {
        log.info("queryRunMetrics invoked, runId={}", runId);
        if (runId == null || runId <= 0) {
            return new RunMetricsToolResult(runId, false, "实验运行 ID 必须为正整数", List.of());
        }

        List<ExperimentMetricVO> metrics;
        try {
            metrics = experimentMetricService.getByRunId(runId, null);
        } catch (BusinessException failure) {
            // 仅将已知的 Run 不存在转换成工具结果，其他故障继续交给 AI 异常处理。
            if (failure.getCode() != 404 || !"实验运行不存在".equals(failure.getMessage())) {
                throw failure;
            }
            log.info("queryRunMetrics run not found, runId={}", runId);
            return new RunMetricsToolResult(runId, false, "实验运行不存在", List.of());
        }
        log.info("queryRunMetrics completed, runId={}, metricCount={}", runId, metrics.size());
        return new RunMetricsToolResult(runId, true,
                metrics.isEmpty() ? "当前实验运行暂无指标数据" : "查询成功", metrics);
    }
}
