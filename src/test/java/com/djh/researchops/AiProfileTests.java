package com.djh.researchops;

import com.djh.researchops.controller.AiChatController;
import com.djh.researchops.service.AiChatService;
import com.djh.researchops.service.ExperimentMetricService;
import com.djh.researchops.service.ExperimentLogService;
import com.djh.researchops.tool.RunLogTools;
import com.djh.researchops.tool.RunMetricTools;
import com.djh.researchops.vo.ExperimentMetricVO;
import com.djh.researchops.vo.ExperimentLogVO;
import com.djh.researchops.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 读取真实 YAML 并验证自动配置；不连接数据库或外部模型服务。
class AiProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean(ExperimentMetricService.class, () -> mock(ExperimentMetricService.class))
            .withBean(ExperimentLogService.class, () -> mock(ExperimentLogService.class))
            .withUserConfiguration(AutoConfigurationOnly.class, RunMetricTools.class, RunLogTools.class, AiChatService.class, AiChatController.class);

    @Test
    void ordinaryModeStartsWithoutApiKeyOrAiBeans() {
        runner.withPropertyValues("spring.profiles.active=", "spring.ai.openai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiChatService.class);
                    assertThat(context).doesNotHaveBean(AiChatController.class);
                    assertThat(context).doesNotHaveBean(RunMetricTools.class);
                    assertThat(context).doesNotHaveBean(RunLogTools.class);
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                    assertThat(context).doesNotHaveBean(ChatClient.Builder.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(ImageModel.class);
                });
    }

    @Test
    void aiProfileCreatesChatClientBuilderAndOnlyChatCapability() {
        // 仅用于构造客户端的测试凭据；本测试不执行任何网络请求。
        runner.withPropertyValues("spring.profiles.active=ai",
                        "LLM_API_KEY=test-placeholder-not-a-real-key", "LLM_MODEL=deepseek-flash",
                        "LLM_BASE_URL=https://api.deepseek.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(ChatClient.Builder.class);
                    assertThat(context).hasSingleBean(AiChatService.class);
                    assertThat(context).hasSingleBean(AiChatController.class);
                    assertThat(context).hasSingleBean(RunMetricTools.class);
                    assertThat(context).hasSingleBean(RunLogTools.class);
                    assertThat(context).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context).doesNotHaveBean(ImageModel.class);
                    assertThat(context.getEnvironment().getProperty("spring.ai.openai.base-url"))
                            .isEqualTo("https://api.deepseek.com");
                    assertThat(context.getEnvironment().getProperty("spring.ai.openai.chat.model"))
                            .isEqualTo("deepseek-flash");
                });
    }

    @Test
    void compatibleRootBaseUrlUsesChatCompletionsWithoutAddingV1() throws Exception {
        // 只监听本机回环地址，使用假凭据和固定响应，验证实际 SDK 的路径拼接。
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"id":"chat-test","object":"chat.completion","created":0,"model":"deepseek-flash",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"本地测试回答"},"finish_reason":"stop"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            runner.withPropertyValues("spring.profiles.active=ai",
                            "LLM_API_KEY=test-placeholder-not-a-real-key", "LLM_MODEL=deepseek-flash",
                            "LLM_BASE_URL=http://127.0.0.1:" + server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(AiChatService.class).chat("你好").getContent())
                                .isEqualTo("本地测试回答");
                        assertThat(path.get()).isEqualTo("/chat/completions");
                        assertThat(requestBody.get()).contains("deepseek-flash");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatClientAutomaticallyExecutesToolAndReturnsResultToModel() throws Exception {
        // 模拟模型先返回 tool_call，再返回最终回答；两个请求都只发送到本机。
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> firstBody = new AtomicReference<>();
        AtomicReference<String> secondBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int call = calls.incrementAndGet();
            String response;
            if (call == 1) {
                firstBody.set(body);
                response = """
                        {"id":"chat-tool","object":"chat.completion","created":0,"model":"deepseek-flash",
                        "choices":[{"index":0,"message":{"role":"assistant","content":null,
                        "tool_calls":[{"id":"call_metric","type":"function","function":{
                        "name":"queryRunMetrics","arguments":"{\\"runId\\":1}"}}]},"finish_reason":"tool_calls"}]}
                        """;
            } else {
                secondBody.set(body);
                response = """
                        {"id":"chat-answer","object":"chat.completion","created":0,"model":"deepseek-flash",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"已收到工具查询结果"},"finish_reason":"stop"}]}
                        """;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            runner.withPropertyValues("spring.profiles.active=ai",
                            "LLM_API_KEY=test-placeholder-not-a-real-key", "LLM_MODEL=deepseek-flash",
                            "LLM_BASE_URL=http://127.0.0.1:" + server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ExperimentMetricService metricService = context.getBean(ExperimentMetricService.class);
                        ExperimentMetricVO metric = new ExperimentMetricVO();
                        metric.setId(17L);
                        metric.setRunId(1L);
                        metric.setMetricName("PSNR");
                        metric.setMetricValue(31.2345);
                        metric.setUnit("dB");
                        metric.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
                        when(metricService.getByRunId(1L, null)).thenReturn(List.of(metric));

                        assertThat(context.getBean(AiChatService.class).chat("Run 1 的 PSNR 是多少？").getContent())
                                .isEqualTo("已收到工具查询结果");
                        verify(metricService).getByRunId(1L, null);
                        assertThat(calls.get()).isEqualTo(2);
                        assertThat(firstBody.get()).contains("queryRunMetrics", "queryRunLogs", "tools").doesNotContain("31.2345");
                        assertThat(secondBody.get()).contains("call_metric", "31.2345", "PSNR", "dB");
                    });
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"error", "all", "empty", "missing", "invalid"})
    void chatClientExecutesLogToolAndReturnsStructuredResult(String scenario) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> secondBody = new AtomicReference<>();
        String arguments = switch (scenario) {
            case "all" -> "{\"runId\":1}";
            case "missing" -> "{\"runId\":99999,\"level\":null}";
            case "invalid" -> "{\"runId\":1,\"level\":\"DEBUG\"}";
            default -> "{\"runId\":1,\"level\":\" error \"}";
        };
        String encodedArguments = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(arguments);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (calls.incrementAndGet() == 1) {
                response = """
                        {"id":"chat-log","object":"chat.completion","created":0,"model":"deepseek-flash",
                        "choices":[{"index":0,"message":{"role":"assistant","content":null,
                        "tool_calls":[{"id":"call_log","type":"function","function":{
                        "name":"queryRunLogs","arguments":%s}}]},"finish_reason":"tool_calls"}]}
                        """.formatted(encodedArguments);
            } else {
                secondBody.set(body);
                response = """
                        {"id":"chat-answer","object":"chat.completion","created":0,"model":"deepseek-flash",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"已收到日志查询结果"},"finish_reason":"stop"}]}
                        """;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            runner.withPropertyValues("spring.profiles.active=ai",
                            "LLM_API_KEY=test-placeholder-not-a-real-key", "LLM_MODEL=deepseek-flash",
                            "LLM_BASE_URL=http://127.0.0.1:" + server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ExperimentLogService logService = context.getBean(ExperimentLogService.class);
                        String level = "all".equals(scenario) ? null : "ERROR";
                        if ("missing".equals(scenario)) {
                            when(logService.getByRunId(99999L, null))
                                    .thenThrow(new BusinessException(404, "实验运行不存在"));
                        } else if (!"invalid".equals(scenario)) {
                            ExperimentLogVO log = new ExperimentLogVO();
                            log.setId(4L);
                            log.setRunId(1L);
                            log.setLevel("ERROR");
                            log.setContent("CUDA out of memory");
                            log.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
                            when(logService.getByRunId(1L, level))
                                    .thenReturn("empty".equals(scenario) ? List.of() : List.of(log));
                        }
                        assertThat(context.getBean(AiChatService.class).chat("Run 1 有什么错误日志？").getContent())
                                .isEqualTo("已收到日志查询结果");
                        assertThat(calls.get()).isEqualTo(2);
                        assertThat(secondBody.get()).contains("call_log");
                        switch (scenario) {
                            case "missing" -> {
                                verify(logService).getByRunId(99999L, null);
                                assertThat(secondBody.get()).contains("实验运行不存在", "success\\\":false");
                            }
                            case "invalid" -> {
                                verifyNoInteractions(logService);
                                assertThat(secondBody.get()).contains("日志级别不合法", "success\\\":false");
                            }
                            case "empty" -> {
                                verify(logService).getByRunId(1L, level);
                                assertThat(secondBody.get()).contains("当前实验运行暂无 ERROR 日志", "success\\\":true");
                            }
                            default -> {
                                verify(logService).getByRunId(1L, level);
                                assertThat(secondBody.get()).contains("CUDA out of memory", "2026-01-02", "success\\\":true");
                            }
                        }
                        verifyNoInteractions(context.getBean(ExperimentMetricService.class));
                    });
        } finally {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    static class AutoConfigurationOnly {
    }
}
