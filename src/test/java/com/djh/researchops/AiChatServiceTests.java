package com.djh.researchops;

import com.djh.researchops.controller.AiChatController;
import com.djh.researchops.exception.BusinessException;
import com.djh.researchops.exception.GlobalExceptionHandler;
import com.djh.researchops.service.AiChatService;
import com.djh.researchops.tool.RunMetricTools;
import com.djh.researchops.tool.RunLogTools;
import com.djh.researchops.tool.RunArtifactTools;
import com.djh.researchops.tool.TaskRunTools;
import com.djh.researchops.tool.ProjectTaskTools;
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
    private RunMetricTools tools;
    private RunLogTools logTools;
    private RunArtifactTools artifactTools;
    private TaskRunTools taskRunTools;
    private ProjectTaskTools projectTaskTools;
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
        tools = mock(RunMetricTools.class);
        logTools = mock(RunLogTools.class);
        artifactTools = mock(RunArtifactTools.class);
        taskRunTools = mock(TaskRunTools.class);
        projectTaskTools = mock(ProjectTaskTools.class);
        service = new AiChatService(builder, tools, logTools, artifactTools, taskRunTools, projectTaskTools);
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
        verify(prompt).tools(tools, logTools, artifactTools, taskRunTools, projectTaskTools);
        verify(prompt).call();
        verify(response).content();
    }

    @Test
    void systemPromptDistinguishesTaskRunsAndRunToolsFromGeneralKnowledge() {
        verify(builder).defaultSystem(argThat((String text) ->
                text.contains("queryProjectTasks") && text.contains("Project 1") && text.contains("项目 1") && text.contains("Vision ResearchOps") && text.contains("queryRunMetrics")
                        && text.contains("必须优先使用") && text.contains("不要凭模型记忆")
                        && text.contains("queryRunLogs") && text.contains("level=ERROR")
                        && text.contains("level=WARN") && text.contains("level=INFO")
                        && text.contains("level 传 null") && text.contains("一般知识问题")
                        && text.contains("不要调用 queryRunMetrics 代替日志查询")
                        && text.contains("不要调用 queryRunLogs 代替指标查询")
                        && text.contains("不要编造失败原因")
                        && text.contains("queryRunArtifacts") && text.contains("type=MODEL")
                        && text.contains("type=POINT_CLOUD") && text.contains("type=IMAGE")
                        && text.contains("type=CHECKPOINT") && text.contains("type=REPORT")
                        && text.contains("type=OTHER") && text.contains("type 传 null")
                        && text.contains("文件名、路径、大小或产物类型")
                        && text.contains("PLY 文件是什么") && text.contains("checkpoint 是什么")
                        && text.contains("不要编造文件")
                        && text.contains("queryTaskRuns") && text.contains("status=FAILED")
                        && text.contains("status=RUNNING") && text.contains("status=COMPLETED")
                        && text.contains("status=PENDING") && text.contains("status 传 null")
                        && text.contains("FAILED 状态是什么意思") && text.contains("Experiment Run 是什么")
                        && text.contains("不要把 Task 编号当成 Run 编号")
                        && text.contains("Task 1") && text.contains("T-1") && text.contains("Run 2") && text.contains("R-2")
                        && text.contains("不要求用户提供数据库 ID") && text.contains("runCode")
                        && !text.contains("queryTaskRuns(taskId=") && !text.contains("真实 id 作为 runId")
                        && text.contains("连续调用多个工具") && text.contains("最少工具")
                        && text.contains("createdAt 最大") && text.contains("createdAt 完全相同")
                        && text.contains("不能直接假设业务编号数字最大") && text.contains("不能用 startedAt")
                        && text.contains("不要继续查询指标") && text.contains("当前没有 PSNR 指标")
                        && text.contains("不要选择另一个 Run") && text.contains("用 SSIM 代替 PSNR")
                        && !text.contains("本阶段不进行") && !text.contains("暂不支持跨 Run")
                        && text.contains("暂无数据") && text.contains("不要假装已经查询这些数据")));
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
