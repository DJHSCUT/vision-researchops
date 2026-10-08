package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.vo.ExperimentRunVO;
import com.djh.researchops.vo.TaskRunsToolResult;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.RunToolItem;
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
public class TaskRunTools {

    private final ExperimentRunService experimentRunService;

    private final ExperimentTaskService experimentTaskService;

    @Tool(name = "queryTaskRuns", description = "查询指定实验任务 Task 下的真实实验运行记录。当用户询问某个 Task 跑过哪些 Run、有哪些实验运行、是否存在失败运行、正在运行的实验、已完成实验或运行状态时使用。可以通过 status 筛选运行状态。不要用于查询某个 Run 的指标、日志或实验产物。一般知识问题不需要调用该工具。")
    public TaskRunsToolResult queryTaskRuns(
            @ToolParam(description = "实验任务 Task 的用户业务编号，例如 T-1。Task 1 或任务 1 表示 T-1，不是数据库 ID；允许小写和省略连字符") String taskCode,
            @ToolParam(required = false, description = "可选实验运行状态，只允许 PENDING、RUNNING、COMPLETED、FAILED。用户询问失败运行时传 FAILED，正在运行时传 RUNNING，已完成时传 COMPLETED，等待运行时传 PENDING；未明确指定状态时可以传 null") String status) {
        taskCode = BusinessCodeParser.normalizeTaskCode(taskCode);
        String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        try (var trace = new ToolInvocationLog("queryTaskRuns", taskCode, "status", normalizedStatus)) {
            try {
                if (taskCode == null) {
                    return trace.complete(new TaskRunsToolResult(taskCode, normalizedStatus, false, "实验任务编号不合法，请提供 T-1 格式的业务编号", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }
                if (normalizedStatus != null && !List.of("PENDING", "RUNNING", "COMPLETED", "FAILED").contains(normalizedStatus)) {
                    return trace.complete(new TaskRunsToolResult(taskCode, normalizedStatus, false, "实验运行状态不合法", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }

                List<ExperimentRunVO> runs;
                try {
                    Long taskId = experimentTaskService.getByTaskCode(taskCode).getId();
                    runs = experimentRunService.getByTaskId(taskId, normalizedStatus);
                } catch (BusinessException failure) {
                    // 只转换 Service 明确表示的 Task 不存在；其他故障继续向外抛出。
                    if (failure.getCode() != 404 || !"实验任务不存在".equals(failure.getMessage())) {
                        throw failure;
                    }

                    return trace.complete(new TaskRunsToolResult(taskCode, normalizedStatus, false, "实验任务 " + taskCode + " 不存在", List.of()), Outcome.NOT_FOUND, 0);
                }

                String message = runs.isEmpty()
                        ? (normalizedStatus == null ? "当前实验任务暂无运行记录" : "当前实验任务暂无 " + normalizedStatus + " 状态运行记录")
                        : "查询成功";
                return trace.complete(new TaskRunsToolResult(taskCode, normalizedStatus, true, message, runs.stream().map(RunToolItem::from).toList()), Outcome.SUCCESS, runs.size());
            } catch (RuntimeException failure) {
                trace.error(failure);
                throw failure;
            }
        }
    }
}
