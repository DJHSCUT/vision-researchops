package com.djh.researchops;

import com.djh.researchops.controller.AiChatController;
import com.djh.researchops.service.AiChatService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

// 读取真实 YAML 并验证自动配置；不连接数据库或外部模型服务。
class AiProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(AutoConfigurationOnly.class, AiChatService.class, AiChatController.class);

    @Test
    void ordinaryModeStartsWithoutApiKeyOrAiBeans() {
        runner.withPropertyValues("spring.profiles.active=", "spring.ai.openai.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiChatService.class);
                    assertThat(context).doesNotHaveBean(AiChatController.class);
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    static class AutoConfigurationOnly {
    }
}
