package com.djh.researchops;

import com.djh.researchops.controller.AiChatController;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.djh.researchops.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 独立 MockMvc + 模拟 ChatClient，不连接数据库、不调用外部 LLM。
class AiChatServiceTests {

    private ChatClient.Builder builder;
    private ChatClient client;
    private ChatClient.ChatClientRequestSpec prompt;
    private ChatClient.CallResponseSpec response;
    private AiChatService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        client = mock(ChatClient.class);
        prompt = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        response = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(prompt);
        when(prompt.call()).thenReturn(response);
        service = new AiChatService(builder);
        mvc = MockMvcBuilders.standaloneSetup(new AiChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void normalMessageReturnsModelContentInApiResponse() throws Exception {
        when(response.content()).thenReturn("你好，我是 Vision ResearchOps 科研实验助手。");
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好，你能做什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.content").value("你好，我是 Vision ResearchOps 科研实验助手。"));
        verify(prompt).user("你好，你能做什么？");
        verify(prompt).call();
        verify(response).content();
    }

    @Test
    void systemPromptDeclaresNoBusinessDataAccess() {
        verify(builder).defaultSystem(argThat((String text) ->
                text.contains("Vision ResearchOps") && text.contains("普通自然语言交流")
                        && text.contains("不要假装已经查询数据库")
                        && text.contains("当前尚未调用业务数据工具")));
        verify(builder).build();
    }

    @Test
    void nullAndMissingMessageReturnChinese400() throws Exception {
        invalidMessage("{\"message\":null}", "请输入消息内容");
        invalidMessage("{}", "请输入消息内容");
    }

    @Test
    void emptyMessageReturnsChinese400() throws Exception {
        invalidMessage("{\"message\":\"\"}", "请输入消息内容");
    }

    @Test
    void whitespaceMessageReturnsChinese400() throws Exception {
        invalidMessage("{\"message\":\"   \"}", "请输入消息内容");
    }

    @Test
    void messageAbove4000ReturnsChinese400() throws Exception {
        invalidMessage("{\"message\":\"" + "字".repeat(4001) + "\"}", "消息内容不能超过4000个字符");
    }

    @Test
    void messageAt4000SucceedsWithoutChangingInput() throws Exception {
        when(response.content()).thenReturn("回答");
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "字".repeat(4000) + "\"}"))
                .andExpect(status().isOk());
        verify(prompt).user("字".repeat(4000));
    }

    @Test
    void modelFailureReturns503WithoutSensitiveDetails() throws Exception {
        when(response.content()).thenThrow(new IllegalStateException("Authorization: Bearer test-secret-only"));
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("AI 服务暂时不可用"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret-only"))));
        BusinessException error = assertThrows(BusinessException.class, () -> service.chat("你好"));
        assertEquals(503, error.getCode());
        assertEquals("AI 服务暂时不可用", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void nullAndEmptyModelContentReturn503() {
        for (String content : new String[]{null, "", "   "}) {
            when(response.content()).thenReturn(content);
            BusinessException error = assertThrows(BusinessException.class, () -> service.chat("你好"));
            assertEquals(503, error.getCode());
        }
    }

    @Test
    void serviceAlsoValidatesMessageBeforeCallingModel() {
        for (String message : new String[]{null, "", "   ", "字".repeat(4001)}) {
            BusinessException error = assertThrows(BusinessException.class, () -> service.chat(message));
            assertEquals(400, error.getCode());
        }
        verifyNoInteractions(client, prompt, response);
    }

    private void invalidMessage(String json, String message) throws Exception {
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(client, prompt, response);
    }
}
