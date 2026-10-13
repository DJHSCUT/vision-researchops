package com.djh.researchops.rag;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class RagProfileTests {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(AutoConfigurationOnly.class, RagConfiguration.class, RagDevController.class);

    @Test void defaultProfileHasNoRagBeans() {
        runner.withPropertyValues("spring.profiles.active=").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(RagService.class).doesNotHaveBean(EmbeddingModel.class)
                    .doesNotHaveBean(VectorStore.class).doesNotHaveBean(RagDevController.class);
        });
    }
    @Test void ragProfileBuildsOnlyEmbeddingAndVectorStoreWithoutApiKeyOrServices() {
        runner.withPropertyValues("spring.profiles.active=rag").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(RagService.class).hasSingleBean(EmbeddingModel.class)
                    .hasSingleBean(VectorStore.class).hasSingleBean(RagDevController.class).doesNotHaveBean(ChatModel.class);
            assertThat(context.getBean(EmbeddingModel.class).dimensions()).isEqualTo(1024);
        });
    }
    @Test void ragCanBeExplicitlyDisabled() {
        runner.withPropertyValues("spring.profiles.active=rag", "researchops.rag.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(RagService.class).doesNotHaveBean(EmbeddingModel.class)
                    .doesNotHaveBean(VectorStore.class).doesNotHaveBean(RagDevController.class);
        });
    }
    @Test void startupMakesNoQdrantConnectionOrImport() throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(150);
            runner.withPropertyValues("spring.profiles.active=rag", "researchops.rag.qdrant-host=127.0.0.1",
                    "researchops.rag.qdrant-port=" + listener.getLocalPort()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(listener::accept).isInstanceOf(java.net.SocketTimeoutException.class);
            });
        }
    }
    @Test void actualOllamaSdkCallsApiEmbedWithPinnedModelAnd1024ResultsOnlyWhenRequested() throws Exception {
        var requests = new AtomicInteger();
        var body = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(exchange.getRequestURI().getPath());
            String vector = "1," + "0,".repeat(1022) + "0";
            byte[] response = ("{\"model\":\"qwen3-embedding:0.6b\",\"embeddings\":[[" + vector
                    + "]],\"total_duration\":1,\"load_duration\":1,\"prompt_eval_count\":1}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            runner.withPropertyValues("spring.profiles.active=rag", "researchops.rag.ollama-base-url=http://127.0.0.1:"
                    + server.getAddress().getPort()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(requests.get()).isZero();
                assertThat(context.getBean(EmbeddingModel.class).embed("视角覆盖不足")).hasSize(1024);
                assertThat(requests.get()).isEqualTo(1);
                assertThat(path.get()).isEqualTo("/api/embed");
                assertThat(body.get()).contains("qwen3-embedding:0.6b", "视角覆盖不足", "\"truncate\":false");
            });
        } finally { server.stop(0); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    static class AutoConfigurationOnly { }
}
