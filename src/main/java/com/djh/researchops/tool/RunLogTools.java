package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.vo.ExperimentLogVO;
import com.djh.researchops.vo.RunLogsToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@Profile("ai")
@RequiredArgsConstructor
public class RunLogTools {

    private final ExperimentLogService experimentLogService;

    @Tool(name = "queryRunLogs", description = "查询指定实验运行 Run 的真实运行日志。当用户询问某个 Run 的运行过程、错误、警告、异常、失败原因或日志内容时使用。可通过 level 筛选 INFO、WARN、ERROR；如果用户没有明确指定日志级别，可以查询全部日志。不要用于查询实验指标或实验产物。一般知识问题（例如 CUDA out of memory 是什么意思）不需要查询数据库，不要仅因出现错误或 ERROR 字样调用此工具。")
    public RunLogsToolResult queryRunLogs(
            @ToolParam(description = "实验运行 Run 的数据库 ID，必须为正整数，例如用户说 Run 1 时传入 1") Long runId,
            @ToolParam(required = false, description = "可选日志级别，只允许 INFO、WARN、ERROR。用户询问错误日志时传 ERROR，警告日志时传 WARN，普通日志时传 INFO；没有明确级别时可以传 null") String level) {
        String normalizedLevel = level == null ? null : level.trim().toUpperCase(Locale.ROOT);
        log.info("queryRunLogs invoked, runId={}, level={}", runId, normalizedLevel);
        if (runId == null || runId <= 0) {
            return new RunLogsToolResult(runId, normalizedLevel, false, "实验运行 ID 必须为正整数", List.of());
        }
        if (normalizedLevel != null && !List.of("INFO", "WARN", "ERROR").contains(normalizedLevel)) {
            return new RunLogsToolResult(runId, normalizedLevel, false, "日志级别不合法", List.of());
        }

        List<ExperimentLogVO> logs;
        try {
            logs = experimentLogService.getByRunId(runId, normalizedLevel);
        } catch (BusinessException failure) {
            // 仅将现有 Service 明确表示的 Run 不存在转换为业务结果。
            if (failure.getCode() != 404 || !"实验运行不存在".equals(failure.getMessage())) {
                throw failure;
            }
            log.info("queryRunLogs run not found, runId={}, level={}", runId, normalizedLevel);
            return new RunLogsToolResult(runId, normalizedLevel, false, "实验运行不存在", List.of());
        }
        log.info("queryRunLogs completed, runId={}, level={}, logCount={}", runId, normalizedLevel, logs.size());
        String message = logs.isEmpty()
                ? (normalizedLevel == null ? "当前实验运行暂无日志" : "当前实验运行暂无 " + normalizedLevel + " 日志")
                : "查询成功";
        return new RunLogsToolResult(runId, normalizedLevel, true, message, logs);
    }
}
