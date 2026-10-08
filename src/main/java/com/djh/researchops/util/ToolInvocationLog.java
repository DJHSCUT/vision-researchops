package com.djh.researchops.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 每次 Tool 调用一条结束日志；不记录原始参数、返回集合、异常消息或 Prompt。 */
@Slf4j
public final class ToolInvocationLog implements AutoCloseable {
    public enum Outcome { SUCCESS, INVALID_ARGUMENT, NOT_FOUND, ERROR }
    private final long started = System.nanoTime();
    private final String requestId = MDC.get(AiRequestLogContext.REQUEST_ID);
    private final String callId = UUID.randomUUID().toString();
    private final String toolName;
    private final String code;
    private final String filterName;
    private final String filter;
    private Outcome outcome = Outcome.ERROR;
    private Integer count;
    private String errorType;

    public ToolInvocationLog(String toolName, String code, String filterName, String filter) {
        this.toolName = toolName;
        this.code = code;
        this.filterName = filterName;
        // 仅白名单内的筛选值可进入日志；非法值可能包含任意用户内容。
        List<String> allowed = switch (toolName) {
            case "queryProjectTasks" -> List.of("TODO", "RUNNING", "COMPLETED", "FAILED");
            case "queryTaskRuns" -> List.of("PENDING", "RUNNING", "COMPLETED", "FAILED");
            case "queryRunLogs" -> List.of("INFO", "WARN", "ERROR");
            case "queryRunArtifacts" -> List.of("IMAGE", "MODEL", "POINT_CLOUD", "CHECKPOINT", "REPORT", "OTHER");
            default -> List.of();
        };
        this.filter = filter != null && allowed.contains(filter) ? filter : null;
    }

    public <T> T complete(T result, Outcome outcome, Integer count) {
        this.outcome = outcome;
        this.count = count;
        this.errorType = switch (outcome) {
            case SUCCESS -> null;
            case INVALID_ARGUMENT -> code == null ? "INVALID_CODE" : "INVALID_" + filterName.toUpperCase(Locale.ROOT);
            default -> outcome.name();
        };
        return result;
    }

    public void error(RuntimeException failure) {
        outcome = Outcome.ERROR;
        errorType = failure.getClass().getSimpleName();
    }

    @Override
    public void close() {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (filterName == null) {
            log.info("requestId={} callId={} tool={} code={} result={} count={} durationMs={} errorType={}",
                    requestId == null ? "-" : requestId, callId, toolName, code, outcome, count, durationMs, errorType);
        } else {
            log.info("requestId={} callId={} tool={} code={} {}={} result={} count={} durationMs={} errorType={}",
                    requestId == null ? "-" : requestId, callId, toolName, code, filterName, filter, outcome, count, durationMs, errorType);
        }
    }
}
