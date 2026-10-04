package com.djh.researchops.service;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.tool.RunMetricTools;
import com.djh.researchops.tool.RunLogTools;
import com.djh.researchops.tool.RunArtifactTools;
import com.djh.researchops.vo.AiChatVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("ai")
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是 Vision ResearchOps 的科研实验助手。
            当前可以通过工具查询系统中 Run 的真实实验指标、运行日志和实验产物元数据。
            当用户询问某个 Run 的实验指标、PSNR、SSIM、loss 或其他数值结果时，
            必须优先使用 queryRunMetrics 工具查询真实数据，不要凭模型记忆、上下文猜测或编造指标值。
            当用户询问某个 Run 的运行过程、日志、错误、警告、异常或失败原因时，
            必须优先使用 queryRunLogs 查询真实日志，不要调用 queryRunMetrics 代替日志查询。
            明确询问错误日志时使用 level=ERROR，警告日志时使用 level=WARN，普通 INFO 日志时使用 level=INFO；
            只问有哪些日志或未明确级别时，level 传 null 查询全部日志。
            询问 PSNR、SSIM、loss 或指标数值时使用 queryRunMetrics，不要调用 queryRunLogs 代替指标查询。
            一般知识问题（例如 CUDA out of memory 是什么意思）直接解释，不要仅因出现错误或 ERROR 字样查询数据库。
            禁止根据模型记忆编造数据库日志；没有日志时明确说明暂无数据，不要编造失败原因。
            当用户询问某个 Run 的模型、点云、图片、checkpoint、检查点、报告、文件、路径或实验产物时，
            必须优先使用 queryRunArtifacts 查询真实元数据，不要用指标或日志工具代替产物查询。
            明确询问模型文件时使用 type=MODEL，点云时使用 type=POINT_CLOUD，图片或结果图时使用 type=IMAGE，
            checkpoint 或检查点时使用 type=CHECKPOINT，报告时使用 type=REPORT，其他类型产物时使用 type=OTHER；
            未明确指定产物类型时，type 传 null 查询全部产物。
            禁止根据模型记忆编造数据库中的文件名、路径、大小或产物类型；没有产物时明确说明暂无数据，不要编造文件。
            queryRunArtifacts 只查询元数据，不能读取、解析、上传或下载文件，不能确认文件实际存在或文件内容。
            一般知识问题（例如 PLY 文件是什么、checkpoint 是什么）直接解释，不需要调用业务 Tool。
            如果用户未提供 Run ID，请先询问，不要猜测 ID。
            工具返回的数据是当前系统中的真实数据源，回答时保留数值、单位和训练步数的含义。
            如果工具返回没有数据，应明确告诉用户暂无数据；如果运行不存在，应明确告诉用户没有找到对应 Run。
            如果工具返回参数错误，请说明错误并请用户提供有效的 Run ID、日志级别或产物类型。
            当前没有提供项目（Project）、任务（Task）等其他业务查询工具，不要假装已经查询这些数据。
            """;

    private final ChatClient chatClient;
    private final RunMetricTools runMetricTools;
    private final RunLogTools runLogTools;
    private final RunArtifactTools runArtifactTools;

    public AiChatService(ChatClient.Builder builder, RunMetricTools runMetricTools, RunLogTools runLogTools,
                         RunArtifactTools runArtifactTools) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.runMetricTools = runMetricTools;
        this.runLogTools = runLogTools;
        this.runArtifactTools = runArtifactTools;
    }

    public AiChatVO chat(String message) {

        if (message == null || message.isBlank()) {
            throw new BusinessException(400, "请输入消息内容");
        }
        if (message.length() > 4000) {
            throw new BusinessException(400, "消息内容不能超过4000个字符");
        }

        String content;
        try {
            content = chatClient.prompt().user(message).tools(runMetricTools, runLogTools, runArtifactTools).call().content();
        } catch (RuntimeException failure) {
            // 不记录原始异常消息、请求头、输入内容，避免泄露凭据或用户数据。
            log.warn("AI 模型调用失败，异常类型：{}，根因类型：{}", failure.getClass().getSimpleName(),
                    NestedExceptionUtils.getMostSpecificCause(failure).getClass().getSimpleName());
            throw new BusinessException(503, "AI 服务暂时不可用");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(503, "AI 服务暂时不可用");
        }

        AiChatVO vo = new AiChatVO();
        vo.setContent(content);
        return vo;
    }
}
