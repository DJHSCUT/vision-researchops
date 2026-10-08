package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.vo.ExperimentLogVO;
import com.djh.researchops.vo.RunLogsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.LogToolItem;
import lombok.RequiredArgsConstructor;
import com.djh.researchops.util.ToolInvocationLog;
import com.djh.researchops.util.ToolInvocationLog.Outcome;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@Profile("ai")
@RequiredArgsConstructor
public class RunLogTools {

    private final ExperimentLogService experimentLogService;

    private final ExperimentRunService experimentRunService;

    @Tool(name = "queryRunLogs", description = "查询指定实验运行 Run 的真实运行日志。当用户询问某个 Run 的运行过程、错误、警告、异常、失败原因或日志内容时使用。可通过 level 筛选 INFO、WARN、ERROR；如果用户没有明确指定日志级别，可以查询全部日志。不要用于查询实验指标或实验产物。一般知识问题（例如 CUDA out of memory 是什么意思）不需要查询数据库，不要仅因出现错误或 ERROR 字样调用此工具。")
    public RunLogsToolResult queryRunLogs(
            @ToolParam(description = "实验运行 Run 的用户业务编号，例如 R-2。Run 2 或运行 2 表示 R-2，不是数据库 ID；允许小写和省略连字符") String runCode,
            @ToolParam(required = false, description = "可选日志级别，只允许 INFO、WARN、ERROR。用户询问错误日志时传 ERROR，警告日志时传 WARN，普通日志时传 INFO；没有明确级别时可以传 null") String level) {
        runCode = BusinessCodeParser.normalizeRunCode(runCode);
        String normalizedLevel = level == null ? null : level.trim().toUpperCase(Locale.ROOT);
        try (var trace = new ToolInvocationLog("queryRunLogs", runCode, "level", normalizedLevel)) {
            try {
                if (runCode == null) {
                    return trace.complete(new RunLogsToolResult(runCode, normalizedLevel, false, "实验运行编号不合法，请提供 R-1 格式的业务编号", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }
                if (normalizedLevel != null && !List.of("INFO", "WARN", "ERROR").contains(normalizedLevel)) {
                    return trace.complete(new RunLogsToolResult(runCode, normalizedLevel, false, "日志级别不合法", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }

                List<ExperimentLogVO> logs;
                try {
                    Long runId = experimentRunService.getByRunCode(runCode).getId();
                    logs = experimentLogService.getByRunId(runId, normalizedLevel);
                } catch (BusinessException failure) {
                    // 仅将现有 Service 明确表示的 Run 不存在转换为业务结果。
                    if (failure.getCode() != 404 || !"实验运行不存在".equals(failure.getMessage())) {
                        throw failure;
                    }

                    return trace.complete(new RunLogsToolResult(runCode, normalizedLevel, false, "运行 " + runCode + " 不存在", List.of()), Outcome.NOT_FOUND, 0);
                }

                String message = logs.isEmpty()
                        ? (normalizedLevel == null ? "当前实验运行暂无日志" : "当前实验运行暂无 " + normalizedLevel + " 日志")
                        : "查询成功";
                return trace.complete(new RunLogsToolResult(runCode, normalizedLevel, true, message, logs.stream().map(LogToolItem::from).toList()), Outcome.SUCCESS, logs.size());
            } catch (RuntimeException failure) {
                trace.error(failure);
                throw failure;
            }
        }
    }
}
