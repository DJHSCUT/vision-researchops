package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ResearchProjectService;
import com.djh.researchops.service.ExperimentTaskService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.ProjectTasksToolResult;
import com.djh.researchops.vo.TaskToolItem;
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
public class ProjectTaskTools {
    private final ResearchProjectService researchProjectService;
    private final ExperimentTaskService experimentTaskService;

    @Tool(name = "queryProjectTasks", description = "通过 Project 业务编号查询研究项目下的真实实验任务及创建时间，可按任务状态筛选。询问项目有哪些任务或最近创建的任务时使用。返回的 taskCode 可直接用于 queryTaskRuns。一般知识问题不调用该工具。")
    public ProjectTasksToolResult queryProjectTasks(
            @ToolParam(description = "项目业务编号，例如 P-1；Project 1 或项目 1 表示 P-1，不是数据库 projectId。允许小写和省略连字符") String projectCode,
            @ToolParam(required = false, description = "可选任务状态，只允许 TODO、RUNNING、COMPLETED、FAILED；未指定时传 null 查询全部任务") String status) {
        projectCode = BusinessCodeParser.normalizeProjectCode(projectCode);
        String normalizedStatus = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (projectCode == null) {
            log.info("queryProjectTasks rejected, invalid projectCode");
            return new ProjectTasksToolResult(null, normalizedStatus, false, "研究项目编号不合法，请提供 P-1 格式的业务编号", List.of());
        }
        if (normalizedStatus != null && !List.of("TODO", "RUNNING", "COMPLETED", "FAILED").contains(normalizedStatus)) {
            log.info("queryProjectTasks rejected, projectCode={}, invalid status", projectCode);
            return new ProjectTasksToolResult(projectCode, normalizedStatus, false, "实验任务状态不合法", List.of());
        }
        log.info("queryProjectTasks invoked, projectCode={}, status={}", projectCode, normalizedStatus);
        List<TaskToolItem> tasks;
        try {
            Long projectId = researchProjectService.getByProjectCode(projectCode).getId();
            tasks = experimentTaskService.getByProjectId(projectId, normalizedStatus).stream()
                    .map(TaskToolItem::from).toList();
        } catch (BusinessException failure) {
            if (failure.getCode() != 404 || !"研究项目不存在".equals(failure.getMessage())) throw failure;
            log.info("queryProjectTasks project not found, projectCode={}, status={}", projectCode, normalizedStatus);
            return new ProjectTasksToolResult(projectCode, normalizedStatus, false, "研究项目 " + projectCode + " 不存在", List.of());
        }
        log.info("queryProjectTasks completed, projectCode={}, status={}, taskCount={}", projectCode, normalizedStatus, tasks.size());
        String message = tasks.isEmpty()
                ? (normalizedStatus == null ? projectCode + " 当前没有实验任务。"
                : projectCode + " 当前没有 " + normalizedStatus + " 状态的实验任务。") : "查询成功";
        return new ProjectTasksToolResult(projectCode, normalizedStatus, true, message, tasks);
    }
}
