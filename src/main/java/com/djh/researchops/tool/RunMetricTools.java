package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.RunMetricsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.MetricToolItem;
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

    private final ExperimentRunService experimentRunService;

    @Tool(name = "queryRunMetrics", description = "查询指定实验运行 Run 的真实实验指标。当用户询问某个 Run 的 PSNR、SSIM、loss、训练指标、数值结果或指标列表时使用。数据来自 Vision ResearchOps 数据库，不要用于查询日志或实验产物。")
    public RunMetricsToolResult queryRunMetrics(
            @ToolParam(description = "实验运行 Run 的用户业务编号，例如 R-2。Run 2 或运行 2 表示 R-2，不是数据库 ID；允许小写和省略连字符") String runCode) {
        runCode = BusinessCodeParser.normalizeRunCode(runCode);
        log.info("queryRunMetrics invoked, runCode={}", runCode);
        if (runCode == null) {
            return new RunMetricsToolResult(runCode, false, "实验运行编号不合法，请提供 R-1 格式的业务编号", List.of());
        }

        List<ExperimentMetricVO> metrics;
        try {
            Long runId = experimentRunService.getByRunCode(runCode).getId();
            metrics = experimentMetricService.getByRunId(runId, null);
        } catch (BusinessException failure) {
            // 仅将已知的 Run 不存在转换成工具结果，其他故障继续交给 AI 异常处理。
            if (failure.getCode() != 404 || !"实验运行不存在".equals(failure.getMessage())) {
                throw failure;
            }
            log.info("queryRunMetrics run not found, runCode={}", runCode);
            return new RunMetricsToolResult(runCode, false, "运行 " + runCode + " 不存在", List.of());
        }
        log.info("queryRunMetrics completed, runCode={}, metricCount={}", runCode, metrics.size());
        return new RunMetricsToolResult(runCode, true,
                metrics.isEmpty() ? "当前实验运行暂无指标数据" : "查询成功", metrics.stream().map(MetricToolItem::from).toList());
    }
}
