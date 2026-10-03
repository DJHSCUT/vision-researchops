package com.djh.researchops.service;

import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.tool.RunMetricTools;
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
            你可以通过工具查询系统中的真实业务数据，当前已提供实验运行指标查询工具。
            当用户询问某个 Run 的实验指标、PSNR、SSIM、loss 或其他数值结果时，
            必须优先使用 queryRunMetrics 工具查询真实数据，不要凭模型记忆、上下文猜测或编造指标值。
            如果用户未提供 Run ID，请先询问，不要猜测 ID。
            工具返回的数据是当前系统中的真实数据源，回答时保留数值、单位和训练步数的含义。
            如果工具返回没有数据，应明确告诉用户暂无数据；如果运行不存在，应明确告诉用户没有找到对应 Run。
            如果工具返回参数错误，请说明错误并请用户提供有效的 Run ID。
            当前没有提供日志、产物、项目、任务等其他业务查询工具，不要假装已经查询这些数据。
            """;

    private final ChatClient chatClient;
    private final RunMetricTools runMetricTools;

    public AiChatService(ChatClient.Builder builder, RunMetricTools runMetricTools) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.runMetricTools = runMetricTools;
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
            content = chatClient.prompt().user(message).tools(runMetricTools).call().content();
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
