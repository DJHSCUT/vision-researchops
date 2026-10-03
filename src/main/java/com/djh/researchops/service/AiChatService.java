package com.djh.researchops.service;

import com.djh.researchops.exception.BusinessException;
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
            当前阶段只能进行普通自然语言交流。
            如果用户询问具体项目、实验、运行、日志、指标或产物数据库信息，
            不要假装已经查询数据库。
            明确说明当前尚未调用业务数据工具。
            """;

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
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
            content = chatClient.prompt().user(message).call().content();
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
