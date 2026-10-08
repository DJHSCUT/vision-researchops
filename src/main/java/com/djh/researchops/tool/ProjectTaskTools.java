package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ResearchProjectService;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.ProjectTasksToolResult;
import com.djh.researchops.vo.TaskToolItem;
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
public class ProjectTaskTools {
    private final ResearchProjectService researchProjectService;
    private final ExperimentTaskService experimentTaskService;

    @Tool(name = "queryProjectTasks", description = "通过 Project 业务编号查询研究项目下的真实实验任务及创建时间，可按任务状态筛选。询问项目有哪些任务或最近创建的任务时使用。返回的 taskCode 可直接用于 queryTaskRuns。一般知识问题不调用该工具。")
    public ProjectTasksToolResult queryProjectTasks(
            @ToolParam(description = "项目业务编号，例如 P-1；Project 1 或项目 1 表示 P-1，不是数据库 projectId。允许小写和省略连字符") String projectCode,
            @ToolParam(required = false, description = "可选任务状态，只允许 TODO、RUNNING、COMPLETED、FAILED；未指定时传 null 查询全部任务") String status) {
        projectCode = BusinessCodeParser.normalizeProjectCode(projectCode);
        String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        try (var trace = new ToolInvocationLog("queryProjectTasks", projectCode, "status", normalizedStatus)) {
            try {
                if (projectCode == null) {
                    return trace.complete(new ProjectTasksToolResult(null, normalizedStatus, false, "研究项目编号不合法，请提供 P-1 格式的业务编号", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }
                if (normalizedStatus != null && !List.of("TODO", "RUNNING", "COMPLETED", "FAILED").contains(normalizedStatus)) {

                    return trace.complete(new ProjectTasksToolResult(projectCode, normalizedStatus, false, "实验任务状态不合法", List.of()), Outcome.INVALID_ARGUMENT, 0);
                }

                List<TaskToolItem> tasks;
                try {
                    Long projectId = researchProjectService.getByProjectCode(projectCode).getId();
                    tasks = experimentTaskService.getByProjectId(projectId, normalizedStatus).stream()
                            .map(TaskToolItem::from).toList();
                } catch (BusinessException failure) {
                    if (failure.getCode() != 404 || !"研究项目不存在".equals(failure.getMessage())) throw failure;

                    return trace.complete(new ProjectTasksToolResult(projectCode, normalizedStatus, false, "研究项目 " + projectCode + " 不存在", List.of()), Outcome.NOT_FOUND, 0);
                }

                String message = tasks.isEmpty()
                        ? (normalizedStatus == null ? projectCode + " 当前没有实验任务。"
                        : projectCode + " 当前没有 " + normalizedStatus + " 状态的实验任务。") : "查询成功";
                return trace.complete(new ProjectTasksToolResult(projectCode, normalizedStatus, true, message, tasks), Outcome.SUCCESS, tasks.size());
            } catch (RuntimeException failure) {
                trace.error(failure);
                throw failure;
            }
        }
    }
}
