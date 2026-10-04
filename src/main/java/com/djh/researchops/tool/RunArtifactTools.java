package com.djh.researchops.tool;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.service.ResultArtifactService;
import com.djh.researchops.vo.ResultArtifactVO;
import com.djh.researchops.vo.RunArtifactsToolResult;
import com.djh.researchops.service.ExperimentRunService;
import com.djh.researchops.util.BusinessCodeParser;
import com.djh.researchops.vo.ArtifactToolItem;
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
public class RunArtifactTools {

    private static final List<String> ARTIFACT_TYPES =
            List.of("IMAGE", "MODEL", "POINT_CLOUD", "CHECKPOINT", "REPORT", "OTHER");

    private final ResultArtifactService resultArtifactService;

    private final ExperimentRunService experimentRunService;

    @Tool(name = "queryRunArtifacts", description = "查询指定实验运行 Run 的真实实验产物。当用户询问某个 Run 产生了哪些文件、模型、点云、图片、检查点、报告、结果文件、文件路径或实验产物时使用。可以通过 type 筛选具体产物类型。不要用于查询实验指标或运行日志。一般知识问题例如 PLY 是什么、checkpoint 是什么，不需要查询数据库。")
    public RunArtifactsToolResult queryRunArtifacts(
            @ToolParam(description = "实验运行 Run 的用户业务编号，例如 R-2。Run 2 或运行 2 表示 R-2，不是数据库 ID；允许小写和省略连字符") String runCode,
            @ToolParam(description = "可选实验产物类型，只允许 IMAGE、MODEL、POINT_CLOUD、CHECKPOINT、REPORT、OTHER。用户询问图片或结果图时传 IMAGE；模型文件传 MODEL；点云传 POINT_CLOUD；检查点传 CHECKPOINT；报告传 REPORT；未明确指定类型时可以传 null", required = false) String type) {
        runCode = BusinessCodeParser.normalizeRunCode(runCode);
        String normalizedType = type == null ? null : type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        log.info("queryRunArtifacts invoked, runCode={}, type={}", runCode, normalizedType);
        if (runCode == null) {
            return new RunArtifactsToolResult(runCode, normalizedType, false, "实验运行编号不合法，请提供 R-1 格式的业务编号", List.of());
        }
        if (normalizedType != null && !ARTIFACT_TYPES.contains(normalizedType)) {
            return new RunArtifactsToolResult(runCode, normalizedType, false, "实验产物类型不合法", List.of());
        }

        List<ResultArtifactVO> artifacts;
        try {
            Long runId = experimentRunService.getByRunCode(runCode).getId();
            artifacts = resultArtifactService.getByRunId(runId, normalizedType);
        } catch (BusinessException failure) {
            // 只转换 Service 明确表示的 Run 不存在，其他业务或系统故障继续抛出。
            if (failure.getCode() != 404 || !"实验运行不存在".equals(failure.getMessage())) {
                throw failure;
            }
            log.info("queryRunArtifacts run not found, runCode={}, type={}", runCode, normalizedType);
            return new RunArtifactsToolResult(runCode, normalizedType, false, "运行 " + runCode + " 不存在", List.of());
        }
        log.info("queryRunArtifacts completed, runCode={}, type={}, artifactCount={}", runCode, normalizedType, artifacts.size());
        String message = artifacts.isEmpty()
                ? (normalizedType == null ? "当前实验运行暂无实验产物" : "当前实验运行暂无 " + normalizedType + " 类型实验产物")
                : "查询成功";
        return new RunArtifactsToolResult(runCode, normalizedType, true, message, artifacts.stream().map(ArtifactToolItem::from).toList());
    }
}
